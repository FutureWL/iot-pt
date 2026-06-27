package com.iot.platform.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iot.platform.common.R;
import com.iot.platform.system.dto.AssignMenuDTO;
import com.iot.platform.system.dto.RoleDTO;
import com.iot.platform.system.dto.RoleQueryDTO;
import com.iot.platform.system.service.RoleService;
import com.iot.platform.system.vo.SysRoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "分页列表")
    @GetMapping("/page")
    public R<IPage<SysRoleVO>> page(RoleQueryDTO query) {
        return R.ok(roleService.page(query));
    }

    @Operation(summary = "全部角色(下拉用)")
    @GetMapping("/all")
    public R<List<SysRoleVO>> all() {
        // 全量,不分页
        RoleQueryDTO q = new RoleQueryDTO();
        q.setPageNum(1);
        q.setPageSize(1000);
        return R.ok(roleService.page(q).getRecords());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<SysRoleVO> detail(@PathVariable Long id) {
        return R.ok(roleService.detail(id));
    }

    @Operation(summary = "新建")
    @PostMapping
    public R<Void> create(@Valid @RequestBody RoleDTO dto) {
        roleService.create(dto);
        return R.ok();
    }

    @Operation(summary = "更新")
    @PutMapping
    public R<Void> update(@Valid @RequestBody RoleDTO dto) {
        roleService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @Operation(summary = "获取角色已分配的菜单 id")
    @GetMapping("/{id}/menus")
    public R<List<Long>> getMenuIds(@PathVariable Long id) {
        return R.ok(roleService.getMenuIds(id));
    }

    @Operation(summary = "给角色分配菜单")
    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable Long id,
                               @Valid @RequestBody AssignMenuDTO dto) {
        roleService.assignMenus(id, dto);
        return R.ok();
    }
}