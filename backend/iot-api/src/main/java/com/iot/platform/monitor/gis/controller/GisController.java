package com.iot.platform.monitor.gis.controller;
import com.iot.platform.common.R;
import com.iot.platform.monitor.gis.service.GisService;
import com.iot.platform.monitor.gis.vo.GisDeviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/monitor/gis") @RequiredArgsConstructor
public class GisController {
    private final GisService service;
    @GetMapping("/devices") public R<List<GisDeviceVO>> devices() { return R.ok(service.devices()); }
}
