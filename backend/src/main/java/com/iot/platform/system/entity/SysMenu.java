package com.iot.platform.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 菜单/权限实体
 *
 * <p>menu_type: 1=目录 2=菜单 3=按钮</p>
 */
@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long parentId;

    private String menuName;

    /** 1=目录 2=菜单 3=按钮 */
    private Integer menuType;

    private String path;
    private String component;
    private String icon;
    private Integer sort;

    /** 权限标识,如 device:add */
    private String permission;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}