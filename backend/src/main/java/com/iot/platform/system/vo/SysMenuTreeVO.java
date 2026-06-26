package com.iot.platform.system.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点(用于权限分配)
 */
@Data
public class SysMenuTreeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String path;
    private String icon;
    private Integer sort;
    private String permission;

    private List<SysMenuTreeVO> children = new ArrayList<>();
}