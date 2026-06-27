package com.iot.platform.system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 给角色分配菜单(权限)
 */
@Data
public class AssignMenuDTO {

    @NotEmpty(message = "至少选择一个菜单")
    private List<Long> menuIds;
}