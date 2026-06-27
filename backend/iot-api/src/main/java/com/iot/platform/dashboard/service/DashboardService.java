package com.iot.platform.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.entity.IotDeviceProperty;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.mapper.IotDevicePropertyMapper;
import com.iot.platform.product.entity.IotProduct;
import com.iot.platform.product.mapper.IotProductMapper;
import com.iot.platform.rule.entity.IotAlert;
import com.iot.platform.rule.mapper.IotAlertMapper;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard 聚合统计
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IotDeviceMapper deviceMapper;
    private final IotDevicePropertyMapper propertyMapper;
    private final IotProductMapper productMapper;
    private final IotAlertMapper alertMapper;

    public Map<String, Object> summary() {
        Long tenantId = TenantContext.getTenantId();

        Map<String, Object> result = new LinkedHashMap<>();

        // 设备总数 / 状态分布
        List<IotDevice> all = deviceMapper.selectList(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getTenantId, tenantId));
        Map<String, Integer> deviceByStatus = new LinkedHashMap<>();
        deviceByStatus.put("online", 0);
        deviceByStatus.put("offline", 0);
        deviceByStatus.put("disabled", 0);
        for (IotDevice d : all) {
            if (d.getStatus() == null) deviceByStatus.merge("offline", 1, Integer::sum);
            else if (d.getStatus() == 1) deviceByStatus.merge("online", 1, Integer::sum);
            else if (d.getStatus() == 2) deviceByStatus.merge("disabled", 1, Integer::sum);
            else deviceByStatus.merge("offline", 1, Integer::sum);
        }
        result.put("deviceTotal", all.size());
        result.put("deviceByStatus", deviceByStatus);

        // 产品总数
        Long productTotal = productMapper.selectCount(new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getTenantId, tenantId));
        result.put("productTotal", productTotal);

        // 今日告警(未处理)
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Long todayAlerts = alertMapper.selectCount(new LambdaQueryWrapper<IotAlert>()
                .eq(IotAlert::getTenantId, tenantId)
                .ge(IotAlert::getCreatedAt, startOfDay));
        Long pendingAlerts = alertMapper.selectCount(new LambdaQueryWrapper<IotAlert>()
                .eq(IotAlert::getTenantId, tenantId)
                .eq(IotAlert::getStatus, 0));
        result.put("todayAlerts", todayAlerts);
        result.put("pendingAlerts", pendingAlerts);

        // 影子总数(=今日消息数的近似,因为 shadow 是 upsert 计数)
        Long shadowTotal = propertyMapper.selectCount(new LambdaQueryWrapper<IotDeviceProperty>()
                .eq(IotDeviceProperty::getTenantId, tenantId));
        result.put("shadowTotal", shadowTotal);

        // 设备按产品分组
        Map<Long, List<IotDevice>> byProduct = all.stream().collect(Collectors.groupingBy(IotDevice::getProductId));
        List<Map<String, Object>> productDistribution = new ArrayList<>();
        for (Map.Entry<Long, List<IotDevice>> e : byProduct.entrySet()) {
            IotProduct p = productMapper.selectById(e.getKey());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", e.getKey());
            item.put("productKey", p == null ? null : p.getProductKey());
            item.put("productName", p == null ? "未命名" : p.getProductName());
            item.put("count", e.getValue().size());
            productDistribution.add(item);
        }
        result.put("productDistribution", productDistribution);

        // 最近告警(7 条)
        List<IotAlert> recentAlerts = alertMapper.selectList(new LambdaQueryWrapper<IotAlert>()
                .eq(IotAlert::getTenantId, tenantId)
                .orderByDesc(IotAlert::getCreatedAt)
                .last("LIMIT 7"));
        result.put("recentAlerts", recentAlerts.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("level", a.getLevel());
            m.put("title", a.getTitle());
            m.put("deviceKey", a.getDeviceKey());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).collect(Collectors.toList()));

        // 最近上线的设备(10 条)
        List<IotDevice> recentOnline = deviceMapper.selectList(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getTenantId, tenantId)
                .eq(IotDevice::getStatus, 1)
                .orderByDesc(IotDevice::getLastOnlineTime)
                .last("LIMIT 10"));
        result.put("recentOnlineDevices", recentOnline.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("deviceKey", d.getDeviceKey());
            m.put("deviceName", d.getDeviceName());
            m.put("lastOnlineTime", d.getLastOnlineTime());
            // 拿产品名
            IotProduct p = productMapper.selectById(d.getProductId());
            m.put("productName", p == null ? null : p.getProductName());
            return m;
        }).collect(Collectors.toList()));

        return result;
    }
}