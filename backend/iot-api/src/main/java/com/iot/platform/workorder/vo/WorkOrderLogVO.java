package com.iot.platform.workorder.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class WorkOrderLogVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workOrderId;
    private String operator;
    private String action;
    private String remark;
    private LocalDateTime ts;
}
