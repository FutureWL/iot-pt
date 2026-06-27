package com.iot.platform.rule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HandleAlertDTO {
    @NotNull(message = "请选择处理结果")
    private Integer status;   // 1=已处理 2=已忽略
    private String remark;
}