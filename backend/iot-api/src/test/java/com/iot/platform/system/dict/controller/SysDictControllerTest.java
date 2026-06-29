package com.iot.platform.system.dict.controller;

import com.iot.platform.system.dict.entity.SysDictItem;
import com.iot.platform.system.dict.entity.SysDictType;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SysDictControllerTest extends ControllerTestSupport {
    @Mock private SysDictService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new SysDictController(service)); }

    @Test void typePageShouldReturnPagedList() throws Exception {
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
                .andExpect(jsonPath("$.data.records[0].type").value("device_status"));
    }
    @Test void itemPageShouldReturnPagedList() throws Exception {
        Map<String, Object> page = new HashMap<>();
        page.put("records", List.of()); page.put("total", 0);
        when(service.pageItems(any())).thenReturn(page);
        mockMvc.perform(get("/system/dict/item/page").param("type", "alert_level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
    @Test void createTypeShouldReturnId() throws Exception {
        when(service.createType(any())).thenReturn(99L);
        mockMvc.perform(post("/system/dict/type")
                .contentType("application/json")
                .content("{\"type\":\"e2e_test\",\"typeName\":\"E2E测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(99));
    }
    @Test void createItemShouldReturnId() throws Exception {
        when(service.createItem(any())).thenReturn(101L);
        mockMvc.perform(post("/system/dict/item")
                .contentType("application/json")
                .content("{\"type\":\"e2e_test\",\"code\":\"c1\",\"label\":\"L\",\"value\":\"V\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(101));
    }
    @Test void updateTypeShouldReturnOk() throws Exception {
        doNothing().when(service).updateType(any());
        mockMvc.perform(put("/system/dict/type")
                .contentType("application/json")
                .content("{\"id\":1,\"typeName\":\"新名称\"}"))
                .andExpect(status().isOk());
        verify(service, times(1)).updateType(any());
    }
    @Test void updateItemShouldReturnOk() throws Exception {
        doNothing().when(service).updateItem(any());
        mockMvc.perform(put("/system/dict/item")
                .contentType("application/json")
                .content("{\"id\":1,\"label\":\"新\"}"))
                .andExpect(status().isOk());
    }
    @Test void deleteTypeShouldReturnOk() throws Exception {
        doNothing().when(service).deleteType(any());
        mockMvc.perform(delete("/system/dict/type/5"))
                .andExpect(status().isOk());
        verify(service).deleteType(5L);
    }
    @Test void deleteItemShouldReturnOk() throws Exception {
        doNothing().when(service).deleteItem(any());
        mockMvc.perform(delete("/system/dict/item/8"))
                .andExpect(status().isOk());
        verify(service).deleteItem(8L);
    }
}