package com.iot.platform.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.platform.common.BusinessException;
import com.iot.platform.system.dto.AssignMenuDTO;
import com.iot.platform.system.dto.RoleDTO;
import com.iot.platform.system.dto.RoleQueryDTO;
import com.iot.platform.system.entity.SysRole;
import com.iot.platform.system.entity.SysRoleMenu;
import com.iot.platform.system.entity.SysUserRole;
import com.iot.platform.system.mapper.SysRoleMapper;
import com.iot.platform.system.mapper.SysRoleMenuMapper;
import com.iot.platform.system.mapper.SysUserRoleMapper;
import com.iot.platform.system.service.RoleService;
import com.iot.platform.system.vo.SysRoleVO;
import com.iot.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public IPage<SysRoleVO> page(RoleQueryDTO q) {
        Long tenantId = TenantContext.getTenantId();
        Page<SysRole> page = new Page<>(
                q.getPageNum() == null ? 1 : q.getPageNum(),
                q.getPageSize() == null ? 10 : q.getPageSize()
        );

        LambdaQueryWrapper<SysRole> w = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .and(StrUtil.isNotBlank(q.getKeyword()), ww -> ww
                        .like(SysRole::getRoleCode, q.getKeyword())
                        .or().like(SysRole::getRoleName, q.getKeyword()))
                .orderByAsc(SysRole::getBuiltIn)
                .orderByAsc(SysRole::getId);

        IPage<SysRole> res = roleMapper.selectPage(page, w);
        return res.convert(this::toVO);
    }

    @Override
    public SysRoleVO detail(Long id) {
        SysRole r = mustGet(id);
        SysRoleVO vo = toVO(r);
        vo.setMenuIds(getMenuIds(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(RoleDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .eq(SysRole::getRoleCode, dto.getRoleCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException("角色编码已存在");
        }
        SysRole r = new SysRole();
        BeanUtil.copyProperties(dto, r);
        r.setTenantId(tenantId);
        r.setBuiltIn(0);
        roleMapper.insert(r);
        log.info("创建角色: code={}", dto.getRoleCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RoleDTO dto) {
        if (dto.getId() == null) throw new BusinessException("id 不能为空");
        SysRole exist = mustGet(dto.getId());

        if (exist.getBuiltIn() != null && exist.getBuiltIn() == 1
                && !exist.getRoleCode().equals(dto.getRoleCode())) {
            throw new BusinessException("内置角色编码不可修改");
        }

        // 若改名,校验唯一
        if (!exist.getRoleCode().equals(dto.getRoleCode())) {
            Long tenantId = TenantContext.getTenantId();
            Long dup = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getTenantId, tenantId)
                    .eq(SysRole::getRoleCode, dto.getRoleCode())
                    .ne(SysRole::getId, dto.getId()));
            if (dup != null && dup > 0) throw new BusinessException("角色编码已存在");
        }

        exist.setRoleCode(dto.getRoleCode());
        exist.setRoleName(dto.getRoleName());
        exist.setDescription(dto.getDescription());
        roleMapper.updateById(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole exist = mustGet(id);
        if (exist.getBuiltIn() != null && exist.getBuiltIn() == 1) {
            throw new BusinessException("内置角色不可删除");
        }
        // 检查是否仍有用户绑定,有则禁止删除
        Long userCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new BusinessException("该角色仍有 " + userCount + " 个用户绑定,不可删除");
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, id));
        log.info("删除角色: id={}, code={}", id, exist.getRoleCode());
    }

    @Override
    public List<Long> getMenuIds(Long roleId) {
        List<SysRoleMenu> list = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        return list.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, AssignMenuDTO dto) {
        SysRole role = mustGet(roleId);
        if (role.getBuiltIn() != null && role.getBuiltIn() == 1) {
            // 内置角色也允许改权限(但要小心 SUPER_ADMIN 改空会导致所有人都进不去)
            // 这里不卡,允许修改
        }
        // 全量替换:先删后插
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : dto.getMenuIds()) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
        log.info("角色分配菜单: roleId={}, count={}", roleId, dto.getMenuIds().size());
    }

    // ============ 辅助 ============

    private SysRoleVO toVO(SysRole r) {
        SysRoleVO vo = new SysRoleVO();
        BeanUtil.copyProperties(r, vo);
        return vo;
    }

    private SysRole mustGet(Long id) {
        Long tenantId = TenantContext.getTenantId();
        SysRole r = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getId, id)
                .eq(SysRole::getTenantId, tenantId));
        if (r == null) throw new BusinessException("角色不存在");
        return r;
    }
}