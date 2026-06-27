package com.iot.platform.device.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.common.BusinessException;
import com.iot.platform.device.dto.IotDeviceGroupDTO;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.entity.IotDeviceGroup;
import com.iot.platform.device.mapper.IotDeviceGroupMapper;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.service.IotDeviceGroupService;
import com.iot.platform.device.vo.IotDeviceGroupVO;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IotDeviceGroupServiceImpl implements IotDeviceGroupService {

    private final IotDeviceGroupMapper groupMapper;
    private final IotDeviceMapper deviceMapper;

    @Override
    public List<IotDeviceGroupVO> all() {
        Long tenantId = TenantContext.getTenantId();
        List<IotDeviceGroup> list = groupMapper.selectList(new LambdaQueryWrapper<IotDeviceGroup>()
                .eq(IotDeviceGroup::getTenantId, tenantId)
                .orderByAsc(IotDeviceGroup::getSort)
                .orderByAsc(IotDeviceGroup::getId));
        if (list.isEmpty()) return List.of();

        // 统计每个分组下的设备数
        List<Long> ids = list.stream().map(IotDeviceGroup::getId).collect(Collectors.toList());
        Map<Long, Integer> countMap = new HashMap<>();
        for (IotDevice d : deviceMapper.selectList(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getTenantId, tenantId)
                .in(IotDevice::getGroupId, ids))) {
            countMap.merge(d.getGroupId(), 1, Integer::sum);
        }
        return list.stream().map(g -> {
            IotDeviceGroupVO vo = new IotDeviceGroupVO();
            BeanUtil.copyProperties(g, vo);
            vo.setDeviceCount(countMap.getOrDefault(g.getId(), 0));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(IotDeviceGroupDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        Long dup = groupMapper.selectCount(new LambdaQueryWrapper<IotDeviceGroup>()
                .eq(IotDeviceGroup::getTenantId, tenantId)
                .eq(IotDeviceGroup::getGroupName, dto.getGroupName()));
        if (dup != null && dup > 0) throw new BusinessException("分组名已存在");

        IotDeviceGroup g = new IotDeviceGroup();
        BeanUtil.copyProperties(dto, g);
        g.setTenantId(tenantId);
        if (g.getParentId() == null) g.setParentId(0L);
        if (g.getSort() == null) g.setSort(0);
        groupMapper.insert(g);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(IotDeviceGroupDTO dto) {
        if (dto.getId() == null) throw new BusinessException("id 不能为空");
        Long tenantId = TenantContext.getTenantId();
        IotDeviceGroup exist = groupMapper.selectOne(new LambdaQueryWrapper<IotDeviceGroup>()
                .eq(IotDeviceGroup::getId, dto.getId())
                .eq(IotDeviceGroup::getTenantId, tenantId));
        if (exist == null) throw new BusinessException("分组不存在");

        if (!exist.getGroupName().equals(dto.getGroupName())) {
            Long dup = groupMapper.selectCount(new LambdaQueryWrapper<IotDeviceGroup>()
                    .eq(IotDeviceGroup::getTenantId, tenantId)
                    .eq(IotDeviceGroup::getGroupName, dto.getGroupName())
                    .ne(IotDeviceGroup::getId, dto.getId()));
            if (dup != null && dup > 0) throw new BusinessException("分组名已存在");
        }

        exist.setGroupName(dto.getGroupName());
        exist.setDescription(dto.getDescription());
        if (dto.getSort() != null) exist.setSort(dto.getSort());
        groupMapper.updateById(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        IotDeviceGroup exist = groupMapper.selectOne(new LambdaQueryWrapper<IotDeviceGroup>()
                .eq(IotDeviceGroup::getId, id)
                .eq(IotDeviceGroup::getTenantId, tenantId));
        if (exist == null) throw new BusinessException("分组不存在");
        // 检查是否仍有设备
        Long count = deviceMapper.selectCount(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getTenantId, tenantId)
                .eq(IotDevice::getGroupId, id));
        if (count != null && count > 0) {
            throw new BusinessException("该分组仍有 " + count + " 台设备,请先移出");
        }
        groupMapper.deleteById(id);
    }
}