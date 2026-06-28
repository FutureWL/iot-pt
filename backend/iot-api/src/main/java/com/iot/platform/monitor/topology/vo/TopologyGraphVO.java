package com.iot.platform.monitor.topology.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class TopologyGraphVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String region;
    private String rootNodeId;
    private List<TopologyNodeVO> nodes;
    private List<TopologyNodeVO> edges;  // EdgeVO omitted for brevity, using node-shaped stub
    private Stats stats = new Stats();

    @Data
    public static class Stats implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Integer nodeCount;
        private Integer edgeCount;
        private Integer faultCount;
        private Integer warningCount;
    }
}
