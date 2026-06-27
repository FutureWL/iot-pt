package com.iot.platform.system.controller;

import com.iot.platform.common.R;
import com.iot.platform.system.dto.AssignRoleDTO;
import com.iot.platform.system.service.UserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户-角色")
@RestController
@RequestMapping("/system/user-role")
@RequiredArgsConstructor
public class UserRoleController {

    private final UserRoleService userRoleService;

    @Operation(summary = "获取用户已分配的角色 id")
    @GetMapping("/{userId}")
    public R<List<Long>> getRoleIds(@PathVariable Long userId) {
        return R.ok(userRoleService.getRoleIds(userId));
    }

    @Operation(summary = "给用户分配角色")
    @PutMapping("/{userId}")
    public R<Void> assignRoles(@PathVariable Long userId,
                               @Valid @RequestBody AssignRoleDTO dto) {
        userRoleService.assignRoles(userId, dto);
        return R.ok();
    }
}