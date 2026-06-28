package com.iot.platform.monitor.topology.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

@Data
@TableName("topology_edge")
public class TopologyEdge implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private String id;
    private String sourceId;
    private String targetId;
    private String type;
    private String status;
    private String label;
}
