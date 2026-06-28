package com.iot.platform.ops.statistics.controller;

import com.iot.platform.ops.statistics.service.OpsStatisticsService;
import com.iot.platform.ops.statistics.vo.OpsKpiSummaryVO;
import com.iot.platform.ops.statistics.vo.OpsKpiVO;
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
class OpsStatisticsControllerTest extends ControllerTestSupport {
    @Mock private OpsStatisticsService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new OpsStatisticsController(service)); }

    @Test
    void summaryShouldReturnKpis() throws Exception {
        OpsKpiSummaryVO vo = new OpsKpiSummaryVO();
        vo.setSlaRate(99.5); vo.setAvgResponseMin(15.0);
        vo.setFaultRate(0.5); vo.setTotalWorkOrders(120L); vo.setTotalAlerts(45L);
        when(service.summary("30d")).thenReturn(vo);

        mockMvc.perform(get("/ops/statistics/summary").param("range", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.slaRate").value(99.5))
                .andExpect(jsonPath("$.data.avgResponseMin").value(15.0))
                .andExpect(jsonPath("$.data.totalWorkOrders").value(120));
    }

    @Test
    void trendShouldReturnKpiList() throws Exception {
        OpsKpiVO vo = new OpsKpiVO();
        vo.setPeriod("2026-06"); vo.setKpiType("ALERT_COUNT"); vo.setValue(45.0);
        when(service.trend("ALERT_COUNT", "30d")).thenReturn(List.of(vo));

        mockMvc.perform(get("/ops/statistics/trend")
                        .param("kpiType", "ALERT_COUNT").param("range", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].period").value("2026-06"))
                .andExpect(jsonPath("$.data[0].value").value(45.0));
    }

    @Test
    void faultTypeShouldReturnDistribution() throws Exception {
        when(service.faultType("30d")).thenReturn(List.of(java.util.Map.of("type", "离线", "count", 5)));

        mockMvc.perform(get("/ops/statistics/fault-type").param("range", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].type").value("离线"))
                .andExpect(jsonPath("$.data[0].count").value(5));
    }

    @Test
    void groupRankShouldReturnRankedList() throws Exception {
        OpsKpiVO vo = new OpsKpiVO();
        vo.setPeriod("2026-06"); vo.setKpiType("FAULT"); vo.setValue(10.0);
        vo.setGroup("东区"); vo.setRank(1);
        when(service.groupRank("FAULT")).thenReturn(List.of(vo));

        mockMvc.perform(get("/ops/statistics/group-rank").param("kpiType", "FAULT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].group").value("东区"))
                .andExpect(jsonPath("$.data[0].rank").value(1));
    }
}
