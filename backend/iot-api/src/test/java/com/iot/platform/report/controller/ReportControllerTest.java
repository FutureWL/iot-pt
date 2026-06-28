package com.iot.platform.report.controller;

import com.iot.platform.report.service.ReportService;
import com.iot.platform.report.vo.ReportTemplateVO;
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
class ReportControllerTest extends ControllerTestSupport {
    @Mock private ReportService service;
    private MockMvc mockMvc;
    @BeforeEach void setUp() { mockMvc = buildMockMvc(new ReportController(service)); }

    @Test
    void templatesShouldReturnList() throws Exception {
        ReportTemplateVO vo = new ReportTemplateVO();
        vo.setId(1L); vo.setTemplateName("巡检日报模板");
        vo.setReportType("巡检日报"); vo.setParamsSchema("{}");
        when(service.listTemplates()).thenReturn(List.of(vo));

        mockMvc.perform(get("/report/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].templateName").value("巡检日报模板"))
                .andExpect(jsonPath("$.data[0].reportType").value("巡检日报"));
    }

    @Test
    void generatedPageShouldReturnList() throws Exception {
        Map<String, Object> page = new HashMap<>();
        page.put("records", List.of()); page.put("total", 0);
        when(service.pageGenerated(any())).thenReturn(page);

        mockMvc.perform(get("/report/generated/page").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
