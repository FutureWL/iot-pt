package com.iot.platform.system.organization.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.common.BusinessException;
import com.iot.platform.system.organization.entity.SysOrganization;
import com.iot.platform.system.organization.mapper.SysOrganizationMapper;
import com.iot.platform.system.organization.service.SysOrganizationService;
import com.iot.platform.system.organization.vo.SysOrganizationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class SysOrganizationServiceImpl implements SysOrganizationService {
    private final SysOrganizationMapper mapper;

    @Override public List<SysOrganizationVO> tree() {
        List<SysOrganization> all = mapper.selectList(
            new LambdaQueryWrapper<SysOrganization>().orderByAsc(SysOrganization::getSort).orderByAsc(SysOrganization::getId));
        return buildTree(all, 0L);
    }

    @Override public Long create(SysOrganization dto) {
        if (dto == null || StrUtil.isBlank(dto.getName())) throw new BusinessException("组织名称不能为空");
        // 校验 parent 存在
        if (dto.getParentId() != null && dto.getParentId() != 0) {
            if (mapper.selectById(dto.getParentId()) == null) throw new BusinessException("父组织不存在: " + dto.getParentId());
        } else {
            dto.setParentId(0L);
        }
        if (dto.getSort() == null) dto.setSort(0);
        mapper.insert(dto);
        return dto.getId();
    }

    @Override public void update(SysOrganization dto) {
        if (dto == null || dto.getId() == null) throw new BusinessException("id 不能为空");
        SysOrganization exist = mapper.selectById(dto.getId());
        if (exist == null) throw new BusinessException("组织不存在: " + dto.getId());
        // 不能把自己设为自己的父
        if (dto.getParentId() != null && dto.getParentId().equals(dto.getId())) {
            throw new BusinessException("父组织不能是自己");
        }
        dto.setCreatedAt(null);
        mapper.updateById(dto);
    }

    @Override @Transactional
    public void delete(Long id) {
        SysOrganization exist = mapper.selectById(id);
        if (exist == null) throw new BusinessException("组织不存在: " + id);
        // cascade 删子组织
        deleteCascade(id);
    }

    private void deleteCascade(Long parentId) {
        mapper.deleteById(parentId);
        List<SysOrganization> children = mapper.selectList(
            new LambdaQueryWrapper<SysOrganization>().eq(SysOrganization::getParentId, parentId));
        for (SysOrganization c : children) deleteCascade(c.getId());
    }

    // ========== helpers ==========
    private List<SysOrganizationVO> buildTree(List<SysOrganization> all, Long parentId) {
        List<SysOrganizationVO> result = new ArrayList<>();
        for (SysOrganization e : all) {
            if (parentId.equals(e.getParentId())) {
                SysOrganizationVO vo = toVO(e);
                vo.setChildren(buildTree(all, e.getId()));
                result.add(vo);
            }
        }
        return result;
    }
    private SysOrganizationVO toVO(SysOrganization e) {
        SysOrganizationVO vo = new SysOrganizationVO();
        BeanUtil.copyProperties(e, vo, "children");
        return vo;
    }
}