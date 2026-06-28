package com.iot.platform.monitor.topology.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.monitor.topology.entity.TopologyNode;
import com.iot.platform.monitor.topology.mapper.TopologyNodeMapper;
import com.iot.platform.monitor.topology.service.TopologyService;
import com.iot.platform.monitor.topology.vo.TopologyGraphVO;
import com.iot.platform.monitor.topology.vo.TopologyNodeVO;
import com.iot.platform.monitor.topology.vo.TopologyRegionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopologyServiceImpl implements TopologyService {

    private final TopologyNodeMapper topologyNodeMapper;

    @Override
    public TopologyGraphVO graph(String regionId, int depth) {
        LambdaQueryWrapper<TopologyNode> wrapper = new LambdaQueryWrapper<>();
        if (regionId != null && !regionId.isBlank()) {
            wrapper.eq(TopologyNode::getRegion, regionId);
        }
        wrapper.last("LIMIT 200");
        List<TopologyNode> nodes = topologyNodeMapper.selectList(wrapper);

        TopologyGraphVO graph = new TopologyGraphVO();
        graph.setRegion(regionId == null ? "default" : regionId);
        graph.setRootNodeId(nodes.isEmpty() ? null : nodes.get(0).getId());
        graph.setNodes(nodes.stream().map(this::toVO).collect(Collectors.toList()));
        graph.setEdges(Collections.emptyList());
        TopologyGraphVO.Stats stats = new TopologyGraphVO.Stats();
        stats.setNodeCount(nodes.size());
        stats.setEdgeCount(0);
        stats.setFaultCount((int) nodes.stream().filter(n -> "fault".equals(n.getStatus())).count());
        stats.setWarningCount((int) nodes.stream().filter(n -> "warning".equals(n.getStatus())).count());
        graph.setStats(stats);
        return graph;
    }

    @Override
    public List<TopologyRegionVO> regions() {
        LambdaQueryWrapper<TopologyNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(TopologyNode::getRegion);
        wrapper.isNotNull(TopologyNode::getRegion);
        wrapper.groupBy(TopologyNode::getRegion);
        List<TopologyNode> nodes = topologyNodeMapper.selectList(wrapper);
        return nodes.stream().map(n -> {
            TopologyRegionVO r = new TopologyRegionVO();
            r.setId(n.getRegion());
            r.setName(n.getRegion());
            Long nodeCount = topologyNodeMapper.selectCount(new LambdaQueryWrapper<TopologyNode>().eq(TopologyNode::getRegion, n.getRegion()));
            r.setNodeCount(nodeCount.intValue());
            Long faultCount = topologyNodeMapper.selectCount(new LambdaQueryWrapper<TopologyNode>().eq(TopologyNode::getRegion, n.getRegion()).eq(TopologyNode::getStatus, "fault"));
            r.setFaultCount(faultCount.intValue());
            return r;
        }).collect(Collectors.toList());
    }

    @Override
    public TopologyNodeVO nodeDetail(String nodeId) {
        TopologyNode entity = topologyNodeMapper.selectById(nodeId);
        if (entity == null) return null;
        TopologyNodeVO vo = toVO(entity);
        vo.setConnectedDevices(Collections.emptyList());
        vo.setRecentAlerts(Collections.emptyList());
        return vo;
    }

    private TopologyNodeVO toVO(TopologyNode e) {
        TopologyNodeVO v = new TopologyNodeVO();
        BeanUtils.copyProperties(e, v);
        return v;
    }
}
