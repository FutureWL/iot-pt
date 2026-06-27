package com.iot.platform.dashboard.controller;

import com.iot.platform.common.R;
import com.iot.platform.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "工作台")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "工作台汇总数据")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(dashboardService.summary());
    }
}