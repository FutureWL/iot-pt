package com.iot.platform.ops.statistics.controller;
import com.iot.platform.common.R;
import com.iot.platform.ops.statistics.service.OpsStatisticsService;
import com.iot.platform.ops.statistics.vo.OpsKpiSummaryVO;
import com.iot.platform.ops.statistics.vo.OpsKpiVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/ops/statistics") @RequiredArgsConstructor
public class OpsStatisticsController {
    private final OpsStatisticsService service;
    @GetMapping("/summary") public R<OpsKpiSummaryVO> summary(@RequestParam(defaultValue = "30d") String range) { return R.ok(service.summary(range)); }
    @GetMapping("/trend") public R<List<OpsKpiVO>> trend(@RequestParam String kpiType, @RequestParam(defaultValue = "30d") String range) { return R.ok(service.trend(kpiType, range)); }
    @GetMapping("/fault-type") public R<List<Map<String, Object>>> faultType(@RequestParam(defaultValue = "30d") String range) { return R.ok(service.faultType(range)); }
    @GetMapping("/group-rank") public R<List<OpsKpiVO>> groupRank(@RequestParam String kpiType) { return R.ok(service.groupRank(kpiType)); }
}
