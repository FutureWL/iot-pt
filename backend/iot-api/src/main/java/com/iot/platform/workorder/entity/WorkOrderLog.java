package com.iot.platform.workorder.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("work_order_log")
public class WorkOrderLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long workOrderId;
    private String operator;
    private String action;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime ts;
}
