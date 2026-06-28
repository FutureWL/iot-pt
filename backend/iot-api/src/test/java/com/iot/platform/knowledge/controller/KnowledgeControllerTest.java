package com.iot.platform.knowledge.controller;

import com.iot.platform.knowledge.dto.KnowledgeDTO;
import com.iot.platform.knowledge.service.KnowledgeService;
import com.iot.platform.knowledge.vo.KnowledgeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeControllerTest extends ControllerTestSupport {
    @Mock private KnowledgeService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new KnowledgeController(service)); }

    @Test
    void pageShouldReturnPagedList() throws Exception {
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(1L); vo.setCategory("故障处理"); vo.setTitle("变压器局部放电 SOP");
        vo.setStatus("PUBLISHED"); vo.setVersion(3);
        vo.setAuthor("admin");

        Map<String, Object> page = new HashMap<>();
        page.put("records", List.of(vo)); page.put("total", 1);
        page.put("size", 10); page.put("current", 1); page.put("pages", 1);
        when(service.page(any())).thenReturn(page);

        // 前端 CrudList 用 pageNum/pageSize,后端要归一化为 current/size
        mockMvc.perform(get("/knowledge/page").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("变压器局部放电 SOP"));
    }

    @Test
    void detailShouldReturnDoc() throws Exception {
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(7L); vo.setTitle("GIS 维护"); vo.setContent("# GIS...");
        when(service.detail(eq(7L))).thenReturn(vo);

        mockMvc.perform(get("/knowledge/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.title").value("GIS 维护"))
                .andExpect(jsonPath("$.data.content").value("# GIS..."));
    }

    @Test
    void createShouldReturnId() throws Exception {
        KnowledgeDTO dto = new KnowledgeDTO();
        dto.setCategory("基础知识"); dto.setTitle("test"); dto.setContent("c");
        when(service.create(any(KnowledgeDTO.class))).thenReturn(99L);

        mockMvc.perform(post("/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(99));
    }

    @Test
    void updateShouldInvokeService() throws Exception {
        KnowledgeDTO dto = new KnowledgeDTO();
        dto.setId(1L); dto.setTitle("updated");
        doNothing().when(service).update(any(KnowledgeDTO.class));

        mockMvc.perform(put("/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk());

        verify(service).update(any(KnowledgeDTO.class));
    }

    @Test
    void deleteShouldInvokeService() throws Exception {
        doNothing().when(service).delete(eq(11L));

        mockMvc.perform(delete("/knowledge/11"))
                .andExpect(status().isOk());

        verify(service).delete(11L);
    }
}