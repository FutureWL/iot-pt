package com.iot.platform.device.overview.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

@Data
public class DeviceOverviewStatsVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long total;
    private Long online;
    private Long offline;
    private Long fault;
    private Long warning;
    private Integer healthScore;
}
