package com.iot.platform.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.common.BusinessException;
import com.iot.platform.system.dto.AssignRoleDTO;
import com.iot.platform.system.entity.SysUserRole;
import com.iot.platform.system.mapper.SysUserRoleMapper;
import com.iot.platform.system.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final SysUserRoleMapper userRoleMapper;

    @Override
    public List<Long> getRoleIds(Long userId) {
        List<SysUserRole> list = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        return list.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, AssignRoleDTO dto) {
        if (userId == null) throw new BusinessException("userId 不能为空");
        if (dto.getRoleIds() == null || dto.getRoleIds().isEmpty()) {
            throw new BusinessException("至少选择一个角色");
        }
        // 全量替换
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        for (Long roleId : dto.getRoleIds()) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
        log.info("用户分配角色: userId={}, count={}", userId, dto.getRoleIds().size());
    }
}