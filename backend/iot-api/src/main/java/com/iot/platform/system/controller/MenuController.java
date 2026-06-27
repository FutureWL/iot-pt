package com.iot.platform.system.controller;

import com.iot.platform.common.R;
import com.iot.platform.system.service.MenuService;
import com.iot.platform.system.vo.SysMenuTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "菜单管理")
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "菜单树(完整)")
    @GetMapping("/tree")
    public R<List<SysMenuTreeVO>> tree() {
        return R.ok(menuService.tree());
    }
}