package com.iot.platform.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.system.dto.AssignMenuDTO;
import com.iot.platform.system.dto.RoleDTO;
import com.iot.platform.system.dto.RoleQueryDTO;
import com.iot.platform.system.vo.SysRoleVO;

public interface RoleService {
    IPage<SysRoleVO> page(RoleQueryDTO query);
    SysRoleVO detail(Long id);
    void create(RoleDTO dto);
    void update(RoleDTO dto);
    void delete(Long id);

    /** 获取角色已分配的菜单 id */
    java.util.List<Long> getMenuIds(Long roleId);

    /** 分配菜单(全量替换) */
    void assignMenus(Long roleId, AssignMenuDTO dto);
}