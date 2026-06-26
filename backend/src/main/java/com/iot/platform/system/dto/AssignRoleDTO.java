package com.iot.platform.system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 给用户分配角色
 */
@Data
public class AssignRoleDTO {

    @NotEmpty(message = "至少选择一个角色")
    private List<Long> roleIds;
}