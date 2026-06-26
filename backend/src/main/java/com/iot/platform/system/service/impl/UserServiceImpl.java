package com.iot.platform.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.common.BusinessException;
import com.iot.platform.system.dto.ResetPasswordDTO;
import com.iot.platform.system.dto.UserDTO;
import com.iot.platform.system.dto.UserQueryDTO;
import com.iot.platform.system.entity.SysUser;
import com.iot.platform.system.entity.SysUserRole;
import com.iot.platform.system.mapper.SysUserMapper;
import com.iot.platform.system.mapper.SysUserRoleMapper;
import com.iot.platform.system.service.UserService;
import com.iot.platform.system.vo.SysUserVO;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IPage<SysUserVO> page(UserQueryDTO q) {
        Long tenantId = TenantContext.getTenantId();
        Page<SysUser> page = new Page<>(
                q.getPageNum() == null ? 1 : q.getPageNum(),
                q.getPageSize() == null ? 10 : q.getPageSize()
        );

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(q.getStatus() != null, SysUser::getStatus, q.getStatus())
                .and(StrUtil.isNotBlank(q.getKeyword()), w -> w
                        .like(SysUser::getUsername, q.getKeyword())
                        .or().like(SysUser::getNickname, q.getKeyword())
                        .or().like(SysUser::getPhone, q.getKeyword())
                        .or().like(SysUser::getEmail, q.getKeyword()))
                .orderByDesc(SysUser::getCreatedAt);

        IPage<SysUser> res = userMapper.selectPage(page, wrapper);

        // 实体 -> VO(脱敏)
        IPage<SysUserVO> voPage = res.convert(u -> {
            SysUserVO vo = new SysUserVO();
            BeanUtil.copyProperties(u, vo);
            return vo;
        });
        return voPage;
    }

    @Override
    public SysUserVO detail(Long id) {
        SysUser u = mustGet(id);
        SysUserVO vo = new SysUserVO();
        BeanUtil.copyProperties(u, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(UserDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (StrUtil.isBlank(dto.getPassword())) {
            throw new BusinessException("新建用户必须输入密码");
        }

        // 唯一性校验 (同租户下 username 唯一)
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException("用户名已存在");
        }

        SysUser u = new SysUser();
        u.setTenantId(tenantId);
        u.setUsername(dto.getUsername());
        u.setPassword(passwordEncoder.encode(dto.getPassword()));
        u.setNickname(dto.getNickname());
        u.setEmail(dto.getEmail());
        u.setPhone(dto.getPhone());
        u.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());

        userMapper.insert(u);
        log.info("创建用户: tenantId={}, username={}", tenantId, dto.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("id 不能为空");
        }
        SysUser exist = mustGet(dto.getId());

        // 若改名,校验新名字不冲突
        if (!exist.getUsername().equals(dto.getUsername())) {
            Long tenantId = TenantContext.getTenantId();
            Long dup = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTenantId, tenantId)
                    .eq(SysUser::getUsername, dto.getUsername())
                    .ne(SysUser::getId, dto.getId()));
            if (dup != null && dup > 0) {
                throw new BusinessException("用户名已存在");
            }
        }

        exist.setUsername(dto.getUsername());
        exist.setNickname(dto.getNickname());
        exist.setEmail(dto.getEmail());
        exist.setPhone(dto.getPhone());
        if (dto.getStatus() != null) exist.setStatus(dto.getStatus());

        // 仅当传入密码时才更新
        if (StrUtil.isNotBlank(dto.getPassword())) {
            exist.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        userMapper.updateById(exist);
        log.info("更新用户: id={}, username={}", exist.getId(), exist.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 保护:不允许删除内置 admin(id=1)
        if (id != null && id == 1L) {
            throw new BusinessException("内置管理员不可删除");
        }
        SysUser exist = mustGet(id);
        userMapper.deleteById(exist.getId());
        // 级联清理:该用户的角色绑定
        userRoleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, exist.getId()));
        log.info("删除用户: id={}, username={}", exist.getId(), exist.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, ResetPasswordDTO dto) {
        SysUser exist = mustGet(id);
        exist.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(exist);
        log.info("重置用户密码: id={}, username={}", id, exist.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Integer status) {
        if (id != null && id == 1L) {
            throw new BusinessException("内置管理员不可禁用");
        }
        SysUser exist = mustGet(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("status 必须为 0 或 1");
        }
        exist.setStatus(status);
        userMapper.updateById(exist);
    }

    /** 校验 + 取用户(限定当前租户) */
    private SysUser mustGet(Long id) {
        Long tenantId = TenantContext.getTenantId();
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .eq(SysUser::getTenantId, tenantId));
        if (u == null) {
            throw new BusinessException("用户不存在");
        }
        return u;
    }
}