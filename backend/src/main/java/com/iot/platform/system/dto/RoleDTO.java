package com.iot.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleDTO {
    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64)
    private String roleCode;

    @NotBlank(message = "角色名不能为空")
    @Size(max = 128)
    private String roleName;

    private String description;
}