package com.iot.platform.monitor.pd.controller;
import com.iot.platform.common.R;
import com.iot.platform.monitor.pd.service.PdService;
import com.iot.platform.monitor.pd.vo.PdRealtimeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/monitor/pd") @RequiredArgsConstructor
public class PdController {
    private final PdService service;
    @GetMapping("/realtime")
    public R<List<PdRealtimeVO>> realtime(@RequestParam(required = false) Long deviceId) {
        return R.ok(service.realtime(deviceId));
    }
}
