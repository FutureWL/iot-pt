package com.iot.platform.system.log.controller;

import com.iot.platform.system.log.service.SysLogService;
import com.iot.platform.system.log.vo.SysOperationLogVO;
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
class SysLogControllerTest extends ControllerTestSupport {
    @Mock private SysLogService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new SysLogController(service)); }

    @Test
    void pageShouldReturnPagedList() throws Exception {
        SysOperationLogVO vo = new SysOperationLogVO();
        vo.setId(1L); vo.setUserId(1L); vo.setUsername("admin");
        vo.setModule("设备管理"); vo.setAction("新增"); vo.setMethod("POST");
        vo.setUrl("/iot/device"); vo.setIp("127.0.0.1"); vo.setStatus(1);
        vo.setCostMs(45L); vo.setTs("2026-06-28 10:00:00");

        Map<String, Object> page = new HashMap<>();
        page.put("records", List.of(vo)); page.put("total", 1);
        page.put("size", 10); page.put("current", 1);
        when(service.page(any())).thenReturn(page);

        mockMvc.perform(get("/system/log/page").param("current", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("admin"))
                .andExpect(jsonPath("$.data.records[0].module").value("设备管理"))
                .andExpect(jsonPath("$.data.records[0].action").value("新增"));
    }
}
