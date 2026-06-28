package com.iot.platform.monitor.topology.service;

import com.iot.platform.monitor.topology.vo.TopologyGraphVO;
import com.iot.platform.monitor.topology.vo.TopologyNodeVO;
import com.iot.platform.monitor.topology.vo.TopologyRegionVO;

import java.util.List;

public interface TopologyService {
    TopologyGraphVO graph(String regionId, int depth);

    List<TopologyRegionVO> regions();

    TopologyNodeVO nodeDetail(String nodeId);
}
