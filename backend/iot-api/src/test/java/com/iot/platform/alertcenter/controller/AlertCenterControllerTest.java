package com.iot.platform.alertcenter.controller;

import com.iot.platform.alertcenter.service.AlertCenterService;
import com.iot.platform.alertcenter.vo.AlertCenterVO;
import com.iot.platform.alertcenter.vo.AlertLevelStatVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AlertCenterControllerTest extends ControllerTestSupport {
    @Mock private AlertCenterService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new AlertCenterController(service)); }

    @Test
    void statsShouldReturnLevelCounts() throws Exception {
        AlertLevelStatVO vo = new AlertLevelStatVO();
        vo.setLevel("URGENT"); vo.setCount(3);
        when(service.stats()).thenReturn(List.of(vo));

        mockMvc.perform(get("/alert/center/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].level").value("URGENT"))
                .andExpect(jsonPath("$.data[0].count").value(3));
    }

    @Test
    void pageShouldReturnPagedRecords() throws Exception {
        AlertCenterVO vo = new AlertCenterVO();
        vo.setId(1L); vo.setLevel("URGENT"); vo.setTitle("测试告警");
        vo.setStatus(0); vo.setAlertTime("2026-06-28 10:00:00");

        Map<String, Object> page = new HashMap<>();
        page.put("records", List.of(vo));
        page.put("total", 1); page.put("size", 10); page.put("current", 1);
        when(service.page(any())).thenReturn(page);

        mockMvc.perform(get("/alert/center/page").param("current", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].level").value("URGENT"))
                .andExpect(jsonPath("$.data.records[0].title").value("测试告警"));
    }
}
