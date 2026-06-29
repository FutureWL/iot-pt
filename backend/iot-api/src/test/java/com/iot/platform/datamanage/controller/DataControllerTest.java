package com.iot.platform.datamanage.controller;

import com.iot.platform.common.R;
import com.iot.platform.datamanage.service.RealtimeDataService;
import com.iot.platform.datamanage.service.RealtimeTdengineService;
import com.iot.platform.datamanage.service.TdengineQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DataController 单元测试(用 standalone MockMvc,避免加载完整 Spring 上下文)
 *
 * 重点验证 bug 修复:
 *   1. deviceId 是 String(JacksonConfig 把 Long 序列化为 String 防 JS 精度丢失)
 *      → 不能用 ((Number) m.get("deviceId")).longValue(),必须 toString() 转
 *   2. TDengine 子表不存在 → 应返空,不应 500
 */
@ExtendWith(MockitoExtension.class)
class DataControllerTest {
    @Mock private RealtimeDataService realtimeService;
    @Mock private TdengineQueryService tdengineService;
    @Mock private RealtimeTdengineService realtimeTdService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
            new DataController(realtimeService, tdengineService, realtimeTdService)
        ).build();
    }

    @Test
    void historyShouldHandleStringDeviceId() throws Exception {
        // 模拟 JacksonConfig 的行为:Long 序列化为 String
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceId", "10000001");  // String, not Long!
        device.put("deviceKey", "TH-001");
        when(realtimeService.listLive()).thenReturn(List.of(device));
        when(tdengineService.query(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong()))
            .thenReturn(List.of());

        mvc.perform(get("/data/history")
                .param("deviceId", "10000001")
                .param("identifier", "temperature")
                .param("type", "double")
                .param("startMs", "1000")
                .param("endMs", "2000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void historyShouldIgnoreUnknownDevice() throws Exception {
        // realtime 返回的列表中 deviceId 都不匹配 → 应仍返 200(默认 tenantId=1)
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceId", "99999999");
        device.put("deviceKey", "X");
        when(realtimeService.listLive()).thenReturn(List.of(device));
        when(tdengineService.query(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong()))
            .thenReturn(List.of());

        mvc.perform(get("/data/history")
                .param("deviceId", "10000001")
                .param("identifier", "temperature")
                .param("type", "double")
                .param("startMs", "1000")
                .param("endMs", "2000"))
            .andExpect(status().isOk());
    }

    @Test
    void historyStatsShouldHandleStringDeviceId() throws Exception {
        // historyStats 端点直接用 tenantId=1,不调 realtimeService
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", 0L); stats.put("min", null); stats.put("max", null); stats.put("avg", null);
        when(tdengineService.stats(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong()))
            .thenReturn(stats);

        mvc.perform(get("/data/history/stats")
                .param("deviceId", "10000001")
                .param("identifier", "temperature")
                .param("startMs", "1000")
                .param("endMs", "2000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void realtimeShouldNotCastDeviceId() throws Exception {
        // 验证:realtime 接口不应抛 ClassCastException
        // 真实场景里,Jackson 序列化的 deviceId 是 String
        mvc.perform(get("/data/realtime"))
            .andExpect(status().isOk());
    }
}