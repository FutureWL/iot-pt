package com.iot.platform.device.overview.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DeviceOverviewItemVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String deviceKey;
    private String deviceName;
    private String productName;
    private Integer status;
    private Integer healthScore;
    private LocalDateTime lastOnlineTime;
}
