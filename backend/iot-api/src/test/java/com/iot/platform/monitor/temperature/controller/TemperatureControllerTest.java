package com.iot.platform.monitor.temperature.controller;

import com.iot.platform.monitor.temperature.service.TemperatureService;
import com.iot.platform.monitor.temperature.vo.TemperaturePointVO;
import com.iot.platform.monitor.temperature.vo.TemperatureStatsVO;
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
class TemperatureControllerTest extends ControllerTestSupport {
    @Mock private TemperatureService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new TemperatureController(service)); }

    @Test
    void statsShouldReturnAggregate() throws Exception {
        TemperatureStatsVO vo = new TemperatureStatsVO();
        vo.setMax(85.5); vo.setAvg(45.2); vo.setMin(22.0);
        vo.setAlertCount(3); vo.setSensorCount(15);
        when(service.stats(null)).thenReturn(vo);

        mockMvc.perform(get("/monitor/temperature/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.max").value(85.5))
                .andExpect(jsonPath("$.data.avg").value(45.2))
                .andExpect(jsonPath("$.data.alertCount").value(3))
                .andExpect(jsonPath("$.data.sensorCount").value(15));
    }

    @Test
    void pointsShouldReturnList() throws Exception {
        TemperaturePointVO p = new TemperaturePointVO();
        p.setSensorId("S-001"); p.setDeviceKey("DEV-001");
        p.setLocation("母排"); p.setTemperature(62.5); p.setTs("2026-06-28 10:00:00");
        when(service.points(null, null)).thenReturn(List.of(p));

        mockMvc.perform(get("/monitor/temperature/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].sensorId").value("S-001"))
                .andExpect(jsonPath("$.data[0].location").value("母排"))
                .andExpect(jsonPath("$.data[0].temperature").value(62.5));
    }
}
