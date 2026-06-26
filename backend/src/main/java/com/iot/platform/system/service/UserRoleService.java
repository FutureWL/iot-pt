package com.iot.platform.system.service;

import com.iot.platform.system.dto.AssignRoleDTO;

import java.util.List;

public interface UserRoleService {
    /** 获取用户的角色 id 列表 */
    List<Long> getRoleIds(Long userId);

    /** 给用户分配角色(全量替换) */
    void assignRoles(Long userId, AssignRoleDTO dto);
}