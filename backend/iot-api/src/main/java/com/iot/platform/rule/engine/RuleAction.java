package com.iot.platform.rule.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 规则动作
 *
 * <pre>
 * 1) 告警动作:
 *    {"type":"alert","level":"WARN","title":"高温","content":"温度 ${temperature} 超过 ${threshold}"}
 *
 * 2) 后续可扩展: device_invoke / notify / forward 等
 * </pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleAction {
    private String type;
    private String level;
    private String title;
    private String content;
    /** 其他自由扩展字段 */
    private Map<String, Object> params;
}