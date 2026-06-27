package com.iot.platform.rule.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("iot_alert")
public class IotAlert implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long ruleId;
    private Long deviceId;
    private String deviceKey;
    private String productKey;

    /** INFO / WARN / ERROR / CRITICAL */
    private String level;

    private String title;
    private String content;

    /** 0=未处理 1=已处理 2=已忽略 */
    private Integer status;

    private String handler;
    private LocalDateTime handleTime;
    private String handleRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}