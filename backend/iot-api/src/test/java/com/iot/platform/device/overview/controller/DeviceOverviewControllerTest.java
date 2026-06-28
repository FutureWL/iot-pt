package com.iot.platform.device.overview.controller;

import com.iot.platform.device.overview.service.DeviceOverviewService;
import com.iot.platform.device.overview.vo.DeviceOverviewItemVO;
import com.iot.platform.device.overview.vo.DeviceOverviewStatsVO;
import com.iot.platform.device.overview.vo.ProductDistributionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备总览 Controller 测试 — TDD RED 阶段
 * 目标:3 个端点(stats / product-distribution / devices)按前端 VO 契约验证
 */
@ExtendWith(MockitoExtension.class)
class DeviceOverviewControllerTest extends ControllerTestSupport {

    @Mock
    private DeviceOverviewService deviceOverviewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(new DeviceOverviewController(deviceOverviewService));
    }

    @Test
    void statsShouldReturnAggregateCounts() throws Exception {
        DeviceOverviewStatsVO stats = new DeviceOverviewStatsVO();
        stats.setTotal(120L);
        stats.setOnline(95L);
        stats.setOffline(20L);
        stats.setFault(3L);
        stats.setWarning(2L);
        stats.setHealthScore(85);
        when(deviceOverviewService.stats()).thenReturn(stats);

        mockMvc.perform(get("/device/overview/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(120))
                .andExpect(jsonPath("$.data.online").value(95))
                .andExpect(jsonPath("$.data.offline").value(20))
                .andExpect(jsonPath("$.data.fault").value(3))
                .andExpect(jsonPath("$.data.warning").value(2))
                .andExpect(jsonPath("$.data.healthScore").value(85));
    }

    @Test
    void productDistributionShouldReturnList() throws Exception {
        ProductDistributionVO item = new ProductDistributionVO();
        item.setProductKey("PK-SWITCH-01");
        item.setProductName("智能开关");
        item.setCount(45);
        when(deviceOverviewService.productDistribution()).thenReturn(List.of(item));

        mockMvc.perform(get("/device/overview/product-distribution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].productKey").value("PK-SWITCH-01"))
                .andExpect(jsonPath("$.data[0].productName").value("智能开关"))
                .andExpect(jsonPath("$.data[0].count").value(45));
    }

    @Test
    void devicesShouldReturnListWithKeywordFilter() throws Exception {
        DeviceOverviewItemVO item = new DeviceOverviewItemVO();
        item.setId(7L);
        item.setDeviceKey("DEV-007");
        item.setDeviceName("一楼配电柜");
        item.setProductName("智能电表");
        item.setStatus(1);
        item.setHealthScore(95);
        item.setLastOnlineTime(LocalDateTime.of(2026, 6, 28, 9, 30, 0));
        when(deviceOverviewService.listDevices("配电", null)).thenReturn(List.of(item));

        mockMvc.perform(get("/device/overview/devices")
                        .param("keyword", "配电")
                        .param("status", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].deviceKey").value("DEV-007"))
                .andExpect(jsonPath("$.data[0].productName").value("智能电表"))
                .andExpect(jsonPath("$.data[0].status").value(1))
                .andExpect(jsonPath("$.data[0].healthScore").value(95));
    }
}
