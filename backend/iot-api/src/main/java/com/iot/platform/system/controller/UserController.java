package com.iot.platform.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.common.R;
import com.iot.platform.system.dto.ResetPasswordDTO;
import com.iot.platform.system.dto.UserDTO;
import com.iot.platform.system.dto.UserQueryDTO;
import com.iot.platform.system.service.UserService;
import com.iot.platform.system.vo.SysUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理 - 增删改查
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页列表")
    @GetMapping("/page")
    public R<IPage<SysUserVO>> page(UserQueryDTO query) {
        return R.ok(userService.page(query));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<SysUserVO> detail(@PathVariable Long id) {
        return R.ok(userService.detail(id));
    }

    @Operation(summary = "新建用户")
    @PostMapping
    public R<Void> create(@Valid @RequestBody UserDTO dto) {
        userService.create(dto);
        return R.ok();
    }

    @Operation(summary = "更新用户")
    @PutMapping
    public R<Void> update(@Valid @RequestBody UserDTO dto) {
        userService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id,
                                 @Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(id, dto);
        return R.ok();
    }

    @Operation(summary = "切换状态(启/停)")
    @PutMapping("/{id}/status/{status}")
    public R<Void> toggleStatus(@PathVariable Long id,
                                @PathVariable Integer status) {
        userService.toggleStatus(id, status);
        return R.ok();
    }
}