package com.iot.platform.monitor.topology.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

@Data
@TableName("topology_node")
public class TopologyNode implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private String id;
    private String name;
    private String type;
    private String voltageLevel;
    private String status;
    private Long deviceId;
    private String region;
    private String substationCode;
    private String properties;
}
