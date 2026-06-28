package com.iot.platform.monitor.topology.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

@Data
public class TopologyRegionVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String parentId;
    private Integer nodeCount;
    private Integer faultCount;
}
