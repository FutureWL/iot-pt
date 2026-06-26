package com.iot.platform.rule.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 规则过滤条件
 *
 * <pre>
 * 示例:
 * 1) 简单条件: 温度 > 30
 *    {"property":"temperature","op":">","value":30}
 *
 * 2) 复合条件(AND): 温度 > 30 且 湿度 < 20
 *    {"allOf":[
 *      {"property":"temperature","op":">","value":30},
 *      {"property":"humidity","op":"<","value":20}
 *    ]}
 *
 * 3) 任一(OR):
 *    {"anyOf":[
 *      {"property":"temperature","op":">","value":30},
 *      {"property":"battery","op":"<","value":10}
 *    ]}
 * </pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleFilter {
    /** 监听属性,空=触发器类型下任意属性 */
    private String property;
    /** 比较操作: >, <, >=, <=, ==, !=, contains */
    private String op;
    /** 比较值 */
    private Object value;

    private java.util.List<RuleFilter> allOf;
    private java.util.List<RuleFilter> anyOf;
}