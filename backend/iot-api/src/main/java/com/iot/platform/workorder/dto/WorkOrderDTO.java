package com.iot.platform.workorder.dto;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class WorkOrderDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long alertId;
    private Long deviceId;
    private String deviceKey;
    private String deviceName;
    private String title;
    private String description;
    private String priority;
    private String status;
    private String assignee;
    private String creator;
    private LocalDateTime slaDeadline;
    private LocalDateTime completedAt;
}
