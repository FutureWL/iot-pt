package com.iot.platform.monitor.temperature.controller;
import com.iot.platform.common.R;
import com.iot.platform.monitor.temperature.service.TemperatureService;
import com.iot.platform.monitor.temperature.vo.TemperaturePointVO;
import com.iot.platform.monitor.temperature.vo.TemperatureStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/monitor/temperature") @RequiredArgsConstructor
public class TemperatureController {
    private final TemperatureService service;
    @GetMapping("/stats") public R<TemperatureStatsVO> stats(@RequestParam(required=false) Long deviceId) {
        return R.ok(service.stats(deviceId));
    }
    @GetMapping("/points") public R<List<TemperaturePointVO>> points(
            @RequestParam(required=false) Long deviceId, @RequestParam(required=false) String location) {
        return R.ok(service.points(deviceId, location));
    }
}
