package com.iot.platform.rule.dto;

import lombok.Data;

@Data
public class IotRuleQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;
    private String triggerType;
    private Integer status;
}