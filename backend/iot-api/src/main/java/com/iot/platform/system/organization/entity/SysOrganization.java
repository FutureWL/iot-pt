package com.iot.platform.system.organization.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_organization")
public class SysOrganization implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long parentId;
    private String name;
    private Integer sort;
    private String leader;
    private String phone;
    private String description;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}