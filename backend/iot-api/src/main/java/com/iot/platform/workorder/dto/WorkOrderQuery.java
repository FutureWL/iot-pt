package com.iot.platform.workorder.dto;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

@Data
public class WorkOrderQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Integer current = 1;
    private Integer size = 10;
    private String status;
    private String priority;
    private String assignee;
    private Long deviceId;
}
