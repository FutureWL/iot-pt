package com.iot.platform.device.overview.controller;

import com.iot.platform.common.R;
import com.iot.platform.device.overview.service.DeviceOverviewService;
import com.iot.platform.device.overview.vo.DeviceOverviewItemVO;
import com.iot.platform.device.overview.vo.DeviceOverviewStatsVO;
import com.iot.platform.device.overview.vo.ProductDistributionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/device/overview")
@RequiredArgsConstructor
public class DeviceOverviewController {

    private final DeviceOverviewService deviceOverviewService;

    @GetMapping("/stats")
    public R<DeviceOverviewStatsVO> stats() {
        return R.ok(deviceOverviewService.stats());
    }

    @GetMapping("/product-distribution")
    public R<List<ProductDistributionVO>> productDistribution() {
        return R.ok(deviceOverviewService.productDistribution());
    }

    @GetMapping("/devices")
    public R<List<DeviceOverviewItemVO>> listDevices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return R.ok(deviceOverviewService.listDevices(keyword, status));
    }
}
