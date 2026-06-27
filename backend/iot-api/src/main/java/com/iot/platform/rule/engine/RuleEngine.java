package com.iot.platform.rule.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.BusinessException;
import com.iot.platform.rule.entity.IotAlert;
import com.iot.platform.rule.entity.IotRule;
import com.iot.platform.rule.event.PropertyReportEvent;
import com.iot.platform.rule.mapper.IotAlertMapper;
import com.iot.platform.rule.mapper.IotRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则引擎
 *
 * <p>监听 PropertyReportEvent,遍历该租户下所有触发器=data 且 status=1 的规则,
 * 评估过滤条件,执行动作(目前仅支持 alert)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngine {

    private final IotRuleMapper ruleMapper;
    private final IotAlertMapper alertMapper;
    private final ObjectMapper objectMapper;

    @EventListener
    @Async("ruleEngineExecutor")
    public void onPropertyReport(PropertyReportEvent ev) {
        try {
            // 取出该租户下所有 data 触发器规则
            List<IotRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<IotRule>()
                    .eq(IotRule::getTenantId, ev.getTenantId())
                    .eq(IotRule::getTriggerType, "data")
                    .eq(IotRule::getStatus, 1));
            if (rules.isEmpty()) return;

            for (IotRule rule : rules) {
                try {
                    // 1) 解析过滤条件
                    RuleFilter filter = objectMapper.readValue(rule.getFilterExpr(), RuleFilter.class);
                    if (!evaluate(filter, ev)) continue;

                    // 2) 解析动作
                    List<RuleAction> actions = objectMapper.readValue(
                            rule.getActions(), new TypeReference<List<RuleAction>>() {});
                    for (RuleAction action : actions) {
                        if ("alert".equals(action.getType())) {
                            executeAlert(rule, action, ev);
                        } else {
                            log.warn("[Rule] 未知动作类型: {}", action.getType());
                        }
                    }
                } catch (Exception e) {
                    log.error("[Rule] 规则处理失败: ruleId={} name={}", rule.getId(), rule.getRuleName(), e);
                }
            }
        } catch (Exception e) {
            log.error("[Rule] 引擎异常", e);
        }
    }

    // ============ 过滤条件求值 ============

    private boolean evaluate(RuleFilter f, PropertyReportEvent ev) {
        if (f == null) return true;
        if (f.getAllOf() != null) {
            for (RuleFilter child : f.getAllOf()) {
                if (!evaluate(child, ev)) return false;
            }
            return true;
        }
        if (f.getAnyOf() != null) {
            for (RuleFilter child : f.getAnyOf()) {
                if (evaluate(child, ev)) return true;
            }
            return false;
        }
        // 叶子条件: property + op + value
        String prop = f.getProperty();
        if (prop == null || prop.isEmpty()) return true;
        // 取该属性的当前值(优先用本次上报的,否则从 shadows 取)
        Object actual = prop.equals(ev.getIdentifier()) ? ev.getValue() : ev.getCurrentShadows().get(prop);
        if (actual == null) return false;
        return compareOp(actual, f.getOp(), f.getValue());
    }

    private boolean compareOp(Object actual, String op, Object target) {
        if (op == null) return true;
        try {
            double a = toDouble(actual);
            double t = toDouble(target);
            switch (op) {
                case ">":  return a > t;
                case "<":  return a < t;
                case ">=": return a >= t;
                case "<=": return a <= t;
                case "==": return Math.abs(a - t) < 1e-9;
                case "!=": return Math.abs(a - t) >= 1e-9;
                case "contains": return actual.toString().contains(target == null ? "" : target.toString());
                default:
                    log.warn("[Rule] 未知 op: {}", op);
                    return false;
            }
        } catch (Exception e) {
            // 字符串相等
            switch (op) {
                case "==": return String.valueOf(actual).equals(String.valueOf(target));
                case "!=": return !String.valueOf(actual).equals(String.valueOf(target));
                case "contains": return String.valueOf(actual).contains(String.valueOf(target));
                default: return false;
            }
        }
    }

    private double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString());
    }

    // ============ 动作执行 ============

    private void executeAlert(IotRule rule, RuleAction action, PropertyReportEvent ev) {
        IotAlert alert = new IotAlert();
        alert.setTenantId(ev.getTenantId());
        alert.setRuleId(rule.getId());
        alert.setDeviceId(ev.getDeviceId());
        alert.setDeviceKey(ev.getDeviceKey());
        alert.setProductKey(ev.getProductKey());
        alert.setLevel(action.getLevel() == null ? "INFO" : action.getLevel().toUpperCase());
        alert.setTitle(render(action.getTitle(), ev));
        alert.setContent(render(action.getContent(), ev));
        alert.setStatus(0);
        alertMapper.insert(alert);
        log.info("[Rule] 告警: rule={} device={} level={} title={}",
                rule.getRuleName(), ev.getDeviceKey(), alert.getLevel(), alert.getTitle());
    }

    /**
     * 模板渲染: ${identifier} / ${value} / ${deviceKey} ...
     */
    private String render(String template, PropertyReportEvent ev) {
        if (template == null) return null;
        Map<String, Object> vars = new HashMap<>();
        vars.put("value", ev.getValue());
        vars.put("identifier", ev.getIdentifier());
        vars.put("deviceKey", ev.getDeviceKey());
        vars.put("productKey", ev.getProductKey());
        if (ev.getCurrentShadows() != null) vars.putAll(ev.getCurrentShadows());
        String result = template;
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}",
                    e.getValue() == null ? "" : e.getValue().toString());
        }
        return result;
    }
}