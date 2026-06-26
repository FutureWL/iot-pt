package com.iot.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.common.BusinessException;
import com.iot.platform.security.JwtTokenProvider;
import com.iot.platform.system.dto.LoginDTO;
import com.iot.platform.system.entity.SysTenant;
import com.iot.platform.system.entity.SysUser;
import com.iot.platform.system.mapper.SysTenantMapper;
import com.iot.platform.system.mapper.SysUserMapper;
import com.iot.platform.system.service.AuthService;
import com.iot.platform.system.vo.UserInfoVO;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务 - M0 最小实现
 *
 * 注:角色/权限查询尚未实现(数据库表已建好,M1 阶段补)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
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

        // 3. 角色 / 权限 (M1 阶段从 sys_user_role + sys_role_menu 关联查询)
        List<String> roles = List.of("SUPER_ADMIN");
        List<String> permissions = List.of(
                "dashboard:view",
                "device:list", "device:group", "device:shadow",
                "product:list",
                "data:realtime", "data:history",
                "rule:list", "rule:alert",
                "screen:view",
                "system:user", "system:role", "system:menu", "system:tenant", "system:notify"
        );

        // 4. 生成 token
        String token = jwtTokenProvider.generate(
                user.getId(), user.getUsername(), tenant.getId(), roles, permissions);

        // 5. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户登录成功: tenant={}, user={}", tenant.getTenantCode(), user.getUsername());

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
        if (tenantId == null) {
            throw new BusinessException(401, "未登录");
        }
        // M0 阶段简化:M1 阶段从 SecurityContext 取 userId 查 DB
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getTenantId, tenantId)
                        .eq(SysUser::getUsername, "admin"));
        if (user == null) throw new BusinessException("用户不存在");

        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) throw new BusinessException("租户不存在");

        return UserInfoVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .tenantName(tenant.getTenantName())
                .roles(List.of("SUPER_ADMIN"))
                .permissions(List.of(
                        "dashboard:view", "device:list", "device:group", "device:shadow",
                        "product:list", "data:realtime", "data:history",
                        "rule:list", "rule:alert", "screen:view",
                        "system:user", "system:role", "system:menu", "system:tenant", "system:notify"))
                .build();
    }

    @Override
    public void logout() {
        // 无状态 JWT,客户端清除 token 即可
        log.info("用户登出: tenantId={}", TenantContext.getTenantId());
    }
}
