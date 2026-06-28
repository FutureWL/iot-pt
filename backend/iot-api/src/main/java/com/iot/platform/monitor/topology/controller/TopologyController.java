package com.iot.platform.monitor.topology.controller;

import com.iot.platform.common.R;
import com.iot.platform.monitor.topology.service.TopologyService;
import com.iot.platform.monitor.topology.vo.TopologyGraphVO;
import com.iot.platform.monitor.topology.vo.TopologyNodeVO;
import com.iot.platform.monitor.topology.vo.TopologyRegionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/monitor/topology")
@RequiredArgsConstructor
public class TopologyController {

    private final TopologyService topologyService;

    @GetMapping("/graph")
    public R<TopologyGraphVO> graph(@RequestParam(required = false) String regionId,
                                    @RequestParam(defaultValue = "2") int depth) {
        String normalizedRegion = (regionId == null || regionId.isBlank()) ? null : regionId;
        return R.ok(topologyService.graph(normalizedRegion, depth));
    }

    @GetMapping("/regions")
    public R<List<TopologyRegionVO>> regions() {
        return R.ok(topologyService.regions());
    }

    @GetMapping("/node/{nodeId}")
    public R<TopologyNodeVO> nodeDetail(@PathVariable String nodeId) {
        return R.ok(topologyService.nodeDetail(nodeId));
    }
}
