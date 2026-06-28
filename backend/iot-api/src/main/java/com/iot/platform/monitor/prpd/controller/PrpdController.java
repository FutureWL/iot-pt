package com.iot.platform.monitor.prpd.controller;
import com.iot.platform.common.R;
import com.iot.platform.monitor.prpd.service.PrpdService;
import com.iot.platform.monitor.prpd.vo.PrpdResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/monitor/prpd") @RequiredArgsConstructor
public class PrpdController {
    private final PrpdService service;
    @GetMapping("/latest/{deviceId}")
    public R<PrpdResultVO> latest(@PathVariable Long deviceId) {
        return R.ok(service.latest(deviceId));
    }
}
