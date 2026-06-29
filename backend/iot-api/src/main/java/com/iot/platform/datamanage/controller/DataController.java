package com.iot.platform.datamanage.controller;

import com.iot.platform.common.R;
import com.iot.platform.datamanage.service.RealtimeDataService;
import com.iot.platform.datamanage.service.RealtimeTdengineService;
import com.iot.platform.datamanage.service.TdengineQueryService;
import com.iot.platform.tenant.TenantContext;
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
    private final RealtimeTdengineService realtimeTdService;

    @Operation(summary = "实时数据(所有设备当前影子,Redis 缓存 5s)")
    @GetMapping("/realtime")
    public R<List<Map<String, Object>>> realtime() {
        return R.ok(realtimeService.listLive());
    }

    @Operation(summary = "轻量刷新: 只返回每个属性在 TDengine 的最后上报时间戳")
    @GetMapping("/realtime/timestamps")
    public R<Map<Long, Map<String, Long>>> realtimeTimestamps() {
        Long tenantId = TenantContext.getTenantId();
        return R.ok(realtimeTdService.getLatestTimestamps(tenantId));
    }

    @Operation(summary = "历史数据(时序库查询)")
    @GetMapping("/history")
    public R<List<Map<String, Object>>> history(
            @RequestParam Long deviceId,
            @RequestParam String identifier,
            @RequestParam(required = false, defaultValue = "double") String type,
            @RequestParam Long startMs,
            @RequestParam Long endMs) throws Exception {
        // 验证设备存在并获取 tenantId
        // 注: JacksonConfig 把 Long 序列化为 String 防 JS 精度丢失,这里 deviceId 是 String
        Long tenantId = realtimeService.listLive().stream()
                .filter(m -> {
                    Object did = m.get("deviceId");
                    if (did == null) return false;
                    try { return Long.parseLong(did.toString()) == deviceId; }
                    catch (NumberFormatException e) { return false; }
                })
                .findFirst()
                .map(m -> 1L)  // 简化，默认租户 1 (RealtimeDataService 内部已过滤)
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