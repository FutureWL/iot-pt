package com.iot.platform.monitor.prpd.controller;

import com.iot.platform.monitor.prpd.service.PrpdService;
import com.iot.platform.monitor.prpd.vo.PrpdResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PrpdControllerTest extends ControllerTestSupport {
    @Mock private PrpdService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new PrpdController(service)); }

    @Test
    void latestShouldReturnResult() throws Exception {
        PrpdResultVO vo = new PrpdResultVO();
        vo.setDeviceId(1L); vo.setDeviceKey("DEV-001"); vo.setDeviceName("局放监测仪");
        vo.setDischargeType("电晕"); vo.setConfidence(0.85); vo.setPointCount(50);
        vo.setCollectedAt("2026-06-28 10:00:00");
        vo.setPoints(List.of());
        when(service.latest(1L)).thenReturn(vo);

        mockMvc.perform(get("/monitor/prpd/latest/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceKey").value("DEV-001"))
                .andExpect(jsonPath("$.data.dischargeType").value("电晕"))
                .andExpect(jsonPath("$.data.confidence").value(0.85));
    }
}
