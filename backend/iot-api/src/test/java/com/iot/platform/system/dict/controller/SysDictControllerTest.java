package com.iot.platform.system.dict.controller;

import com.iot.platform.system.dict.service.SysDictService;
import com.iot.platform.system.dict.vo.SysDictTypeVO;
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
class SysDictControllerTest extends ControllerTestSupport {
    @Mock private SysDictService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new SysDictController(service)); }

    @Test
    void typePageShouldReturnPagedList() throws Exception {
        SysDictTypeVO vo = new SysDictTypeVO();
        vo.setId(1L); vo.setType("device_status"); vo.setTypeName("设备状态字典");
        vo.setStatus(1);

        Map<String, Object> page = new HashMap<>();
        page.put("records", List.of(vo)); page.put("total", 1);
        when(service.pageTypes(any())).thenReturn(page);

        mockMvc.perform(get("/system/dict/type/page").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].type").value("device_status"))
                .andExpect(jsonPath("$.data.records[0].typeName").value("设备状态字典"));
    }
}
