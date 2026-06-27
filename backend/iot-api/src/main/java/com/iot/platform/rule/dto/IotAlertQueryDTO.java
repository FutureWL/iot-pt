package com.iot.platform.rule.dto;

import lombok.Data;

@Data
public class IotAlertQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;
    private String level;
    private Integer status;
    private Long ruleId;
    private Long deviceId;
}