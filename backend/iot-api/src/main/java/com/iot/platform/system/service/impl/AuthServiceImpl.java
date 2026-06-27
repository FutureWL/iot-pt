package com.iot.platform.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.common.BusinessException;
import com.iot.platform.security.JwtTokenProvider;
import com.iot.platform.system.dto.LoginDTO;
import com.iot.platform.system.entity.SysMenu;
import com.iot.platform.system.entity.SysRole;
import com.iot.platform.system.entity.SysRoleMenu;
import com.iot.platform.system.entity.SysTenant;
import com.iot.platform.system.entity.SysUser;
import com.iot.platform.system.entity.SysUserRole;
import com.iot.platform.system.mapper.SysMenuMapper;
import com.iot.platform.system.mapper.SysRoleMapper;
import com.iot.platform.system.mapper.SysRoleMenuMapper;
import com.iot.platform.system.mapper.SysTenantMapper;
import com.iot.platform.system.mapper.SysUserMapper;
import com.iot.platform.system.mapper.SysUserRoleMapper;
import com.iot.platform.system.service.AuthService;
import com.iot.platform.system.vo.UserInfoVO;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务
 *
 * <p>角色和权限从 sys_user_role + sys_role + sys_role_menu + sys_menu 关联查询</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public UserInfoVO login(LoginDTO dto) {
        // 1. 校验租户
        SysTenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getTenantCode, dto.getTenantCode()));
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        if (tenant.getStatus() != null && tenant.getStatus() == 0) {
            throw new BusinessException("租户已禁用");
        }

        // 2. 查用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenant.getId())
                        .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已禁用");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 3. 查角色 + 权限
        List<String> roles = loadRoleCodes(user.getId());
        List<String> permissions = loadPermissions(user.getId(), roles);

        // 4. 生成 token
        String token = jwtTokenProvider.generate(
                user.getId(), user.getUsername(), tenant.getId(), roles, permissions);

        // 5. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户登录成功: tenant={}, user={}, roles={}", tenant.getTenantCode(),
                user.getUsername(), roles);

        return UserInfoVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .tenantName(tenant.getTenantName())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    @Override
    public UserInfoVO currentUser() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            throw new BusinessException(401, "未登录");
        }

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getId, userId)
                        .eq(SysUser::getTenantId, tenantId));
        if (user == null) throw new BusinessException("用户不存在");

        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) throw new BusinessException("租户不存在");

        List<String> roles = loadRoleCodes(userId);
        List<String> permissions = loadPermissions(userId, roles);

        return UserInfoVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .tenantName(tenant.getTenantName())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    @Override
    public void logout() {
        log.info("用户登出: tenantId={}", TenantContext.getTenantId());
    }

    // ============ 辅助方法 ============

    /** 加载用户的所有角色编码 */
    private List<String> loadRoleCodes(Long userId) {
        List<SysUserRole> urs = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (CollUtil.isEmpty(urs)) return Collections.emptyList();
        List<Long> roleIds = urs.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
    }

    /** 加载用户的所有权限标识 */
    private List<String> loadPermissions(Long userId, List<String> roles) {
        // SUPER_ADMIN 直通所有菜单
        if (roles.contains("SUPER_ADMIN")) {
            List<SysMenu> all = menuMapper.selectList(null);
            return all.stream()
                    .map(SysMenu::getPermission)
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList());
        }
        // 普通用户: 通过角色-菜单-菜单权限 聚合
        List<SysUserRole> urs = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (CollUtil.isEmpty(urs)) return Collections.emptyList();
        List<Long> roleIds = urs.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        List<SysRoleMenu> rms = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds));
        if (CollUtil.isEmpty(rms)) return Collections.emptyList();
        List<Long> menuIds = rms.stream().map(SysRoleMenu::getMenuId)
                .distinct().collect(Collectors.toList());

        List<SysMenu> menus = menuMapper.selectBatchIds(menuIds);
        return menus.stream()
                .map(SysMenu::getPermission)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }
}