package com.iot.platform.workorder.controller;

import com.iot.platform.workorder.dto.WorkOrderDTO;
import com.iot.platform.workorder.dto.WorkOrderQuery;
import com.iot.platform.workorder.service.WorkOrderService;
import com.iot.platform.workorder.vo.WorkOrderLogVO;
import com.iot.platform.workorder.vo.WorkOrderStatsVO;
import com.iot.platform.workorder.vo.WorkOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工单模块 Controller 测试 — TDD RED 阶段
 * 目标:8 个端点全部按前端 VO 契约验证
 */
@ExtendWith(MockitoExtension.class)
class WorkorderControllerTest extends ControllerTestSupport {

    @Mock
    private WorkOrderService workOrderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(new WorkorderController(workOrderService));
    }

    @Test
    void statsShouldReturnFourCounts() throws Exception {
        WorkOrderStatsVO stats = new WorkOrderStatsVO();
        stats.setPending(7L);
        stats.setProcessing(3L);
        stats.setCompleted(120L);
        stats.setOverdue(2L);
        when(workOrderService.stats()).thenReturn(stats);

        mockMvc.perform(get("/workorder/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pending").value(7))
                .andExpect(jsonPath("$.data.processing").value(3))
                .andExpect(jsonPath("$.data.completed").value(120))
                .andExpect(jsonPath("$.data.overdue").value(2));
    }

    @Test
    void pageShouldReturnPagedRecords() throws Exception {
        WorkOrderVO vo = new WorkOrderVO();
        vo.setId(1L);
        vo.setWorkOrderNo("WO202606280001");
        vo.setTitle("设备离线");
        vo.setStatus("PENDING");
        vo.setPriority("HIGH");

        Map<String, Object> pageResult = Map.of(
                "records", List.of(vo),
                "total", 1,
                "size", 10,
                "current", 1
        );
        when(workOrderService.page(any(WorkOrderQuery.class))).thenReturn(pageResult);

        mockMvc.perform(get("/workorder/page")
                        .param("current", "1")
                        .param("size", "10")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].workOrderNo").value("WO202606280001"))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING"));
    }

    @Test
    void detailShouldReturnWorkOrder() throws Exception {
        WorkOrderVO vo = new WorkOrderVO();
        vo.setId(42L);
        vo.setWorkOrderNo("WO202606280042");
        vo.setTitle("网关断连");
        vo.setStatus("PROCESSING");
        when(workOrderService.detail(42L)).thenReturn(vo);

        mockMvc.perform(get("/workorder/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.workOrderNo").value("WO202606280042"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void logsShouldReturnLogList() throws Exception {
        WorkOrderLogVO log = new WorkOrderLogVO();
        log.setId(1L);
        log.setWorkOrderId(42L);
        log.setOperator("admin");
        log.setAction("CREATE");
        log.setTs(java.time.LocalDateTime.of(2026, 6, 28, 10, 0, 0));
        when(workOrderService.logs(42L)).thenReturn(List.of(log));

        mockMvc.perform(get("/workorder/42/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].workOrderId").value(42))
                .andExpect(jsonPath("$.data[0].operator").value("admin"))
                .andExpect(jsonPath("$.data[0].action").value("CREATE"));
    }

    @Test
    void createShouldReturnNewId() throws Exception {
        when(workOrderService.create(any(WorkOrderDTO.class))).thenReturn(99L);

        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setTitle("手动创建工单");
        dto.setDeviceId(7L);
        dto.setPriority("NORMAL");

        mockMvc.perform(post("/workorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(99));
    }

    @Test
    void updateShouldInvokeService() throws Exception {
        doNothing().when(workOrderService).update(any(WorkOrderDTO.class));

        WorkOrderDTO dto = new WorkOrderDTO();
        dto.setId(42L);
        dto.setTitle("更新标题");
        dto.setPriority("URGENT");

        mockMvc.perform(put("/workorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(workOrderService).update(any(WorkOrderDTO.class));
    }

    @Test
    void assignShouldPassAssignee() throws Exception {
        doNothing().when(workOrderService).assign(eq(42L), eq("zhangsan"));

        mockMvc.perform(put("/workorder/42/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"zhangsan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(workOrderService).assign(42L, "zhangsan");
    }

    @Test
    void completeShouldPassRemark() throws Exception {
        doNothing().when(workOrderService).complete(eq(42L), eq("已修复"));

        mockMvc.perform(put("/workorder/42/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"已修复\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(workOrderService).complete(42L, "已修复");
    }
}