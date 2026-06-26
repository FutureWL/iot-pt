package com.iot.platform.datamanage.controller;

import com.iot.platform.common.R;
import com.iot.platform.datamanage.service.RealtimeDataService;
import com.iot.platform.datamanage.service.TdengineQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "数据查询")
@RestController
@RequestMapping("/data")
@RequiredArgsConstructor
public class DataController {

    private final RealtimeDataService realtimeService;
    private final TdengineQueryService tdengineService;

    @Operation(summary = "实时数据(所有在线设备当前影子)")
    @GetMapping("/realtime")
    public R<List<Map<String, Object>>> realtime() {
        return R.ok(realtimeService.listLive());
    }

    @Operation(summary = "历史数据(时序库查询)")
    @GetMapping("/history")
    public R<List<Map<String, Object>>> history(
            @RequestParam Long deviceId,
            @RequestParam String identifier,
            @RequestParam(required = false, defaultValue = "double") String type,
            @RequestParam Long startMs,
            @RequestParam Long endMs) throws Exception {
        // 取 tenantId 跟 deviceId 的关系
        Long tenantId = realtimeService.listLive().stream()
                .filter(m -> ((Number) m.get("deviceId")).longValue() == deviceId)
                .findFirst()
                .map(m -> 1L)  // 简化,默认租户 1 (RealtimeDataService 内部已过滤)
                .orElse(1L);
        return R.ok(tdengineService.query(tenantId, deviceId, identifier, type, startMs, endMs));
    }

    @Operation(summary = "历史数据统计(最大/最小/平均/计数)")
    @GetMapping("/history/stats")
    public R<Map<String, Object>> historyStats(
            @RequestParam Long deviceId,
            @RequestParam String identifier,
            @RequestParam(required = false, defaultValue = "double") String type,
            @RequestParam Long startMs,
            @RequestParam Long endMs) throws Exception {
        Long tenantId = 1L;  // 同上简化
        return R.ok(tdengineService.stats(tenantId, deviceId, identifier, type, startMs, endMs));
    }
}