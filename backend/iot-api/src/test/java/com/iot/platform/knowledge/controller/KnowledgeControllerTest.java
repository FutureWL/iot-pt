package com.iot.platform.knowledge.controller;

import com.iot.platform.knowledge.service.KnowledgeService;
import com.iot.platform.knowledge.vo.KnowledgeVO;
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
class KnowledgeControllerTest extends ControllerTestSupport {
    @Mock private KnowledgeService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new KnowledgeController(service)); }

    @Test
    void pageShouldReturnPagedList() throws Exception {
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(1L); vo.setCategory("运维"); vo.setTitle("变压器检修手册");
        vo.setStatus("PUBLISHED"); vo.setVersion(3);
        vo.setAuthor("admin"); vo.setUpdatedAt("2026-06-28 10:00:00");

        Map<String, Object> page = new HashMap<>();
        page.put("records", List.of(vo)); page.put("total", 1);
        page.put("size", 10); page.put("current", 1);
        when(service.page(any())).thenReturn(page);

        mockMvc.perform(get("/knowledge/page").param("current", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("变压器检修手册"))
                .andExpect(jsonPath("$.data.records[0].category").value("运维"))
                .andExpect(jsonPath("$.data.records[0].status").value("PUBLISHED"));
    }
}
