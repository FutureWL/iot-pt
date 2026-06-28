package com.iot.platform.monitor.topology.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class TopologyNodeVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String type;
    private String voltageLevel;
    private String status;
    private Long deviceId;
    private String region;
    private String substationCode;
    private String properties;
    private List<TopologyNodeVO> connectedDevices;
    private List<?> recentAlerts;
}
