package com.iot.platform.system.service;

import com.iot.platform.system.vo.SysMenuTreeVO;

import java.util.List;

public interface MenuService {
    /** 完整菜单树(给角色分配权限用) */
    List<SysMenuTreeVO> tree();
}