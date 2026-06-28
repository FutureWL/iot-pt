package com.iot.platform.workorder.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

@Data
public class WorkOrderStatsVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long pending;
    private Long processing;
    private Long completed;
    private Long overdue;
}
