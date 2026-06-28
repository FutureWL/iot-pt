package com.iot.platform.monitor.environment.controller;

import com.iot.platform.monitor.environment.service.EnvironmentService;
import com.iot.platform.monitor.environment.vo.EnvironmentRealtimeVO;
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
class EnvironmentControllerTest extends ControllerTestSupport {
    @Mock private EnvironmentService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new EnvironmentController(service)); }

    @Test
    void realtimeShouldReturnList() throws Exception {
        EnvironmentRealtimeVO vo = new EnvironmentRealtimeVO();
        vo.setDeviceId(1L); vo.setDeviceKey("DEV-001");
        vo.setTemperature(28.5); vo.setHumidity(65.0);
        vo.setWaterStatus(0); vo.setTiltAngle(0.5); vo.setVibrationRMS(0.02);
        vo.setCondensationRisk(false); vo.setTs("2026-06-28 10:00:00");
        when(service.realtime(null)).thenReturn(List.of(vo));

        mockMvc.perform(get("/monitor/environment/realtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].deviceKey").value("DEV-001"))
                .andExpect(jsonPath("$.data[0].temperature").value(28.5))
                .andExpect(jsonPath("$.data[0].humidity").value(65.0));
    }
}
