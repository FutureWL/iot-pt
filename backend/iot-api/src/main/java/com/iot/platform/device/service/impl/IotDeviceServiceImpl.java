package com.iot.platform.device.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.common.BusinessException;
import com.iot.platform.device.dto.IotDeviceDTO;
import com.iot.platform.device.dto.IotDeviceQueryDTO;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.entity.IotDeviceGroup;
import com.iot.platform.device.entity.IotDeviceProperty;
import com.iot.platform.device.mapper.IotDeviceGroupMapper;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.mapper.IotDevicePropertyMapper;
import com.iot.platform.device.service.IotDeviceService;
import com.iot.platform.device.vo.IotDeviceVO;
import com.iot.platform.product.entity.IotProduct;
import com.iot.platform.product.mapper.IotProductMapper;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IotDeviceServiceImpl implements IotDeviceService {

    private final IotDeviceMapper deviceMapper;
    private final IotDeviceGroupMapper groupMapper;
    private final IotDevicePropertyMapper propertyMapper;
    private final IotProductMapper productMapper;

    @Override
    public IPage<IotDeviceVO> page(IotDeviceQueryDTO q) {
        Long tenantId = TenantContext.getTenantId();
        Page<IotDevice> page = new Page<>(
                q.getPageNum() == null ? 1 : q.getPageNum(),
                q.getPageSize() == null ? 10 : q.getPageSize()
        );
        LambdaQueryWrapper<IotDevice> w = new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getTenantId, tenantId)
                .eq(q.getProductId() != null, IotDevice::getProductId, q.getProductId())
                .eq(q.getGroupId() != null, IotDevice::getGroupId, q.getGroupId())
                .eq(q.getStatus() != null, IotDevice::getStatus, q.getStatus())
                .and(StrUtil.isNotBlank(q.getKeyword()), ww -> ww
                        .like(IotDevice::getDeviceKey, q.getKeyword())
                        .or().like(IotDevice::getDeviceName, q.getKeyword())
                        .or().like(IotDevice::getDescription, q.getKeyword()))
                .orderByDesc(IotDevice::getCreatedAt);

        IPage<IotDevice> res = deviceMapper.selectPage(page, w);
        IPage<IotDeviceVO> voPage = res.convert(this::toVO);
        // 列表脱敏
        voPage.getRecords().forEach(v -> v.setDeviceSecret(mask(v.getDeviceSecret())));
        return voPage;
    }

    @Override
    public IotDeviceVO detail(Long id, boolean fullSecret) {
        IotDevice d = mustGet(id);
        IotDeviceVO vo = toVO(d);
        if (!fullSecret) {
            vo.setDeviceSecret(mask(vo.getDeviceSecret()));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotDeviceVO create(IotDeviceDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        // 产品存在
        IotProduct product = productMapper.selectById(dto.getProductId());
        if (product == null) throw new BusinessException("产品不存在");

        // 同租户下 deviceKey 唯一
        Long dup = deviceMapper.selectCount(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getTenantId, tenantId)
                .eq(IotDevice::getDeviceKey, dto.getDeviceKey()));
        if (dup != null && dup > 0) {
            throw new BusinessException("设备 Key 已存在");
        }

        IotDevice d = new IotDevice();
        BeanUtil.copyProperties(dto, d);
        d.setTenantId(tenantId);
        // 自动生成密钥
        d.setDeviceSecret(StrUtil.isBlank(dto.getDeviceSecret())
                ? IdUtil.fastSimpleUUID()
                : dto.getDeviceSecret());
        // 状态默认离线
        d.setStatus(0);
        // 协议从产品继承
        d.setProtocol(product.getNetType());
        if (d.getGroupId() == null) d.setGroupId(0L);
        deviceMapper.insert(d);
        log.info("创建设备: key={}, productId={}", dto.getDeviceKey(), dto.getProductId());
        return toVO(d);  // 返回完整 secret,只此一次
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(IotDeviceDTO dto) {
        if (dto.getId() == null) throw new BusinessException("id 不能为空");
        IotDevice exist = mustGet(dto.getId());

        if (!exist.getDeviceKey().equals(dto.getDeviceKey())) {
            Long tenantId = TenantContext.getTenantId();
            Long dup = deviceMapper.selectCount(new LambdaQueryWrapper<IotDevice>()
                    .eq(IotDevice::getTenantId, tenantId)
                    .eq(IotDevice::getDeviceKey, dto.getDeviceKey())
                    .ne(IotDevice::getId, dto.getId()));
            if (dup != null && dup > 0) throw new BusinessException("设备 Key 已存在");
        }

        exist.setDeviceKey(dto.getDeviceKey());
        exist.setDeviceName(dto.getDeviceName());
        exist.setGroupId(dto.getGroupId() == null ? 0L : dto.getGroupId());
        exist.setLocation(dto.getLocation());
        exist.setTags(dto.getTags());
        exist.setDescription(dto.getDescription());
        // 不允许通过 update 改 secret
        deviceMapper.updateById(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        IotDevice exist = mustGet(id);
        deviceMapper.deleteById(id);
        // 级联清理影子
        propertyMapper.delete(new LambdaQueryWrapper<IotDeviceProperty>()
                .eq(IotDeviceProperty::getDeviceId, id));
        log.info("删除设备: id={}, key={}", id, exist.getDeviceKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetSecret(Long id) {
        IotDevice exist = mustGet(id);
        String newSecret = IdUtil.fastSimpleUUID();
        exist.setDeviceSecret(newSecret);
        deviceMapper.updateById(exist);
        log.info("重置设备密钥: id={}", id);
        return newSecret;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Integer status) {
        IotDevice exist = mustGet(id);
        if (status == null) throw new BusinessException("status 不能为空");
        if (status < 0 || status > 2) throw new BusinessException("status 必须为 0/1/2");
        exist.setStatus(status);
        deviceMapper.updateById(exist);
    }

    // ============ 辅助 ============

    private IotDeviceVO toVO(IotDevice d) {
        IotDeviceVO vo = new IotDeviceVO();
        BeanUtil.copyProperties(d, vo);
        // 关联产品
        IotProduct p = productMapper.selectById(d.getProductId());
        if (p != null) {
            vo.setProductKey(p.getProductKey());
            vo.setProductName(p.getProductName());
        }
        // 关联分组
        if (d.getGroupId() != null && d.getGroupId() > 0) {
            IotDeviceGroup g = groupMapper.selectById(d.getGroupId());
            if (g != null) vo.setGroupName(g.getGroupName());
        }
        return vo;
    }

    private IotDevice mustGet(Long id) {
        Long tenantId = TenantContext.getTenantId();
        IotDevice d = deviceMapper.selectOne(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getId, id)
                .eq(IotDevice::getTenantId, tenantId));
        if (d == null) throw new BusinessException("设备不存在");
        return d;
    }

    private String mask(String s) {
        if (s == null || s.length() < 8) return "****";
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }
}