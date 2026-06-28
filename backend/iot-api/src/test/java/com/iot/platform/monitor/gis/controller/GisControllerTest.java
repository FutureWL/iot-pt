package com.iot.platform.monitor.gis.controller;

import com.iot.platform.monitor.gis.service.GisService;
import com.iot.platform.monitor.gis.vo.GisDeviceVO;
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
class GisControllerTest extends ControllerTestSupport {
    @Mock private GisService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new GisController(service)); }

    @Test
    void devicesShouldReturnList() throws Exception {
        GisDeviceVO vo = new GisDeviceVO();
        vo.setDeviceId(1L); vo.setDeviceKey("DEV-001"); vo.setDeviceName("东区电表");
        vo.setLng(121.5); vo.setLat(31.2); vo.setStatus(1); vo.setAlertCount(0);
        when(service.devices()).thenReturn(List.of(vo));

        mockMvc.perform(get("/monitor/gis/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].deviceKey").value("DEV-001"))
                .andExpect(jsonPath("$.data[0].lng").value(121.5))
                .andExpect(jsonPath("$.data[0].lat").value(31.2))
                .andExpect(jsonPath("$.data[0].status").value(1));
    }
}
