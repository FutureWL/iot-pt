package com.iot.platform.system.log.controller;
import com.iot.platform.common.R;
import com.iot.platform.system.log.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/system/log") @RequiredArgsConstructor
public class SysLogController {
    private final SysLogService service;
    @GetMapping("/page") public R<Map<String, Object>> page(@RequestParam Map<String, Object> params) { return R.ok(service.page(params)); }
}
