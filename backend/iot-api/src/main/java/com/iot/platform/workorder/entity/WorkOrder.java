package com.iot.platform.workorder.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("work_order")
public class WorkOrder implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String workOrderNo;
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
