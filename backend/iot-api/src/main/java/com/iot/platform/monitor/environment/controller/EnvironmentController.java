package com.iot.platform.monitor.environment.controller;
import com.iot.platform.common.R;
import com.iot.platform.monitor.environment.service.EnvironmentService;
import com.iot.platform.monitor.environment.vo.EnvironmentRealtimeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/monitor/environment") @RequiredArgsConstructor
public class EnvironmentController {
    private final EnvironmentService service;
    @GetMapping("/realtime") public R<List<EnvironmentRealtimeVO>> realtime(@RequestParam(required=false) Long deviceId) {
        return R.ok(service.realtime(deviceId));
    }
}
