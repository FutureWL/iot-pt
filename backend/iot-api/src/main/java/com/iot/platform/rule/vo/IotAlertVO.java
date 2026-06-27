package com.iot.platform.rule.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class IotAlertVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long ruleId;
    private String ruleName;
    private Long deviceId;
    private String deviceKey;
    private String productKey;
    private String level;
    private String title;
    private String content;
    private Integer status;
    private String handler;
    private String handleRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}