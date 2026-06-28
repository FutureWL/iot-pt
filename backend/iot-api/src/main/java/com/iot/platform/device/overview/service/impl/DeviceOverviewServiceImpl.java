package com.iot.platform.device.overview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.overview.service.DeviceOverviewService;
import com.iot.platform.device.overview.vo.DeviceOverviewItemVO;
import com.iot.platform.device.overview.vo.DeviceOverviewStatsVO;
import com.iot.platform.device.overview.vo.ProductDistributionVO;
import com.iot.platform.product.entity.IotProduct;
import com.iot.platform.product.mapper.IotProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceOverviewServiceImpl implements DeviceOverviewService {

    private final IotDeviceMapper deviceMapper;
    private final IotProductMapper productMapper;

    @Override
    public DeviceOverviewStatsVO stats() {
        LambdaQueryWrapper<IotDevice> all = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<IotDevice> online = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<IotDevice> offline = new LambdaQueryWrapper<>();

        // 仅查未删除的设备
        all.eq(IotDevice::getDeleted, 0);
        online.eq(IotDevice::getDeleted, 0).eq(IotDevice::getStatus, 1);
        offline.eq(IotDevice::getDeleted, 0).eq(IotDevice::getStatus, 0);

        Long total = deviceMapper.selectCount(all);
        Long onCount = deviceMapper.selectCount(online);
        Long offCount = deviceMapper.selectCount(offline);

        // fault / warning 暂时用占位:后续接 iot_alert 表统计
        Long fault = 0L;
        Long warning = 0L;

        DeviceOverviewStatsVO vo = new DeviceOverviewStatsVO();
        vo.setTotal(total);
        vo.setOnline(onCount);
        vo.setOffline(offCount);
        vo.setFault(fault);
        vo.setWarning(warning);
        // 健康分 = online / total * 100,无设备时为 100
        vo.setHealthScore(total == 0 ? 100 : (int) Math.round(onCount * 100.0 / total));
        return vo;
    }

    @Override
    public List<ProductDistributionVO> productDistribution() {
        List<IotDevice> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<IotDevice>().eq(IotDevice::getDeleted, 0));
        if (devices.isEmpty()) return Collections.emptyList();

        // 按 productId 分组
        Map<Long, Long> countByProduct = devices.stream()
                .collect(Collectors.groupingBy(IotDevice::getProductId, Collectors.counting()));
        // 批量查产品名
        Map<Long, IotProduct> productMap = new HashMap<>();
        if (!countByProduct.isEmpty()) {
            productMapper.selectBatchIds(countByProduct.keySet())
                    .forEach(p -> productMap.put(p.getId(), p));
        }
        // 输出按 count 倒序
        return countByProduct.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    IotProduct p = productMap.get(e.getKey());
                    ProductDistributionVO vo = new ProductDistributionVO();
                    vo.setProductKey(p != null ? p.getProductKey() : "UNKNOWN-" + e.getKey());
                    vo.setProductName(p != null ? p.getProductName() : "(已删除)");
                    vo.setCount(e.getValue().intValue());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<DeviceOverviewItemVO> listDevices(String keyword, Integer status) {
        LambdaQueryWrapper<IotDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IotDevice::getDeleted, 0);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(IotDevice::getDeviceName, keyword)
                    .or().like(IotDevice::getDeviceKey, keyword));
        }
        if (status != null) {
            wrapper.eq(IotDevice::getStatus, status);
        }
        wrapper.orderByDesc(IotDevice::getLastOnlineTime);
        wrapper.last("LIMIT 50");
        List<IotDevice> devices = deviceMapper.selectList(wrapper);
        if (devices.isEmpty()) return Collections.emptyList();

        // 批量查 product name
        Map<Long, IotProduct> productMap = new HashMap<>();
        List<Long> productIds = devices.stream().map(IotDevice::getProductId).distinct().collect(Collectors.toList());
        if (!productIds.isEmpty()) {
            productMapper.selectBatchIds(productIds).forEach(p -> productMap.put(p.getId(), p));
        }

        List<DeviceOverviewItemVO> result = new ArrayList<>(devices.size());
        for (IotDevice d : devices) {
            DeviceOverviewItemVO vo = new DeviceOverviewItemVO();
            vo.setId(d.getId());
            vo.setDeviceKey(d.getDeviceKey());
            vo.setDeviceName(d.getDeviceName());
            IotProduct p = productMap.get(d.getProductId());
            vo.setProductName(p != null ? p.getProductName() : "(已删除)");
            vo.setStatus(d.getStatus());
            vo.setHealthScore(calculateHealthScore(d));
            vo.setLastOnlineTime(d.getLastOnlineTime());
            result.add(vo);
        }
        return result;
    }

    private Integer calculateHealthScore(IotDevice d) {
        // 简化:在线=100,离线=30,禁用=0
        if (d.getStatus() == null) return 50;
        return switch (d.getStatus()) {
            case 1 -> 100;
            case 0 -> 30;
            case 2 -> 0;
            default -> 50;
        };
    }
}
