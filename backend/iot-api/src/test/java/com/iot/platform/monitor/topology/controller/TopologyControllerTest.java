package com.iot.platform.monitor.topology.controller;

import com.iot.platform.monitor.topology.service.TopologyService;
import com.iot.platform.monitor.topology.vo.TopologyGraphVO;
import com.iot.platform.monitor.topology.vo.TopologyNodeVO;
import com.iot.platform.monitor.topology.vo.TopologyRegionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 拓扑模块 Controller 测试 — TDD RED 阶段
 * 目标:3 个端点全部按前端 VO 契约验证
 */
@ExtendWith(MockitoExtension.class)
class TopologyControllerTest extends ControllerTestSupport {

    @Mock
    private TopologyService topologyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(new TopologyController(topologyService));
    }

    @Test
    void graphShouldReturnNodesAndEdges() throws Exception {
        TopologyNodeVO node = new TopologyNodeVO();
        node.setId("substation-001");
        node.setName("中心变电站");
        node.setType("substation");
        node.setVoltageLevel("110kV");
        node.setStatus("normal");

        TopologyGraphVO graph = new TopologyGraphVO();
        graph.setRegion("default");
        graph.setRootNodeId("substation-001");
        graph.setNodes(List.of(node));
        graph.setEdges(List.of());

        when(topologyService.graph(isNull(), eq(2))).thenReturn(graph);

        mockMvc.perform(get("/monitor/topology/graph")
                        .param("regionId", "")
                        .param("depth", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.region").value("default"))
                .andExpect(jsonPath("$.data.rootNodeId").value("substation-001"))
                .andExpect(jsonPath("$.data.nodes[0].id").value("substation-001"))
                .andExpect(jsonPath("$.data.nodes[0].type").value("substation"))
                .andExpect(jsonPath("$.data.nodes[0].voltageLevel").value("110kV"));
    }

    @Test
    void regionsShouldReturnRegionTree() throws Exception {
        TopologyRegionVO region = new TopologyRegionVO();
        region.setId("region-east");
        region.setName("东区");
        region.setNodeCount(15);
        region.setFaultCount(2);
        when(topologyService.regions()).thenReturn(List.of(region));

        mockMvc.perform(get("/monitor/topology/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("region-east"))
                .andExpect(jsonPath("$.data[0].name").value("东区"))
                .andExpect(jsonPath("$.data[0].nodeCount").value(15))
                .andExpect(jsonPath("$.data[0].faultCount").value(2));
    }

    @Test
    void nodeDetailShouldReturnNodeWithConnections() throws Exception {
        TopologyNodeVO detail = new TopologyNodeVO();
        detail.setId("substation-001");
        detail.setName("中心变电站");
        detail.setType("substation");
        detail.setStatus("normal");
        detail.setConnectedDevices(List.of());
        detail.setRecentAlerts(List.of());
        when(topologyService.nodeDetail("substation-001")).thenReturn(detail);

        mockMvc.perform(get("/monitor/topology/node/substation-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("substation-001"))
                .andExpect(jsonPath("$.data.name").value("中心变电站"));

        verify(topologyService).nodeDetail("substation-001");
    }
}