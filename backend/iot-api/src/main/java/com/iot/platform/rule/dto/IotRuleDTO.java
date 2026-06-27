package com.iot.platform.rule.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IotRuleDTO {
    private Long id;

    @NotBlank(message = "规则名不能为空")
    private String ruleName;

    private String description;

    /** data / property / event / online / offline */
    @NotBlank(message = "请选择触发器")
    private String triggerType;

    /** JSON */
    @NotBlank(message = "请配置过滤条件")
    private String filterExpr;

    /** JSON 数组 */
    @NotBlank(message = "请至少配置一个动作")
    private String actions;

    private Integer status;
}