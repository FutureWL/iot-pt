package com.iot.platform.monitor.pd.controller;

import com.iot.platform.monitor.pd.service.PdService;
import com.iot.platform.monitor.pd.vo.PdRealtimeVO;
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
class PdControllerTest extends ControllerTestSupport {
    @Mock private PdService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new PdController(service)); }

    @Test
    void realtimeShouldReturnList() throws Exception {
        PdRealtimeVO vo = new PdRealtimeVO();
        vo.setDeviceId(1L); vo.setDeviceKey("DEV-001"); vo.setDeviceName("局放监测仪");
        vo.setAmplitude(35.5); vo.setPulseCount(120); vo.setChannelType("UHF");
        vo.setStatus("normal"); vo.setTs("2026-06-28 10:00:00");
        when(service.realtime(null)).thenReturn(List.of(vo));

        mockMvc.perform(get("/monitor/pd/realtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].deviceKey").value("DEV-001"))
                .andExpect(jsonPath("$.data[0].channelType").value("UHF"))
                .andExpect(jsonPath("$.data[0].status").value("normal"));
    }
}
