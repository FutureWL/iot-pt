package com.iot.platform.protocol.core;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.entity.IotDeviceProperty;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.mapper.IotDevicePropertyMapper;
import com.iot.platform.datamanage.service.TdengineWriter;
import com.iot.platform.rule.event.PropertyReportEvent;
import com.iot.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备消息业务处理器
 *
 * <p>从 MQTT/TCP 适配器中抽出,只处理"写库 + 触发规则引擎 + WebSocket 推送"这些业务逻辑。
 * 适配器只负责协议解析(把字节流变成 DeviceMessage),不直接写库。</p>
 *
 * <p>本类只在 API 进程实例化。IoT 进程通过 Redis Stream 触发远端的实例。</p>
 *
 * @author IoT Platform Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceMessageProcessor {

    private final IotDeviceMapper deviceMapper;
    private final IotDevicePropertyMapper propertyMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TdengineWriter tdengineWriter;
    private final WebSocketEventPublisher wsPublisher;

    /**
     * 处理属性上报
     * @param device   已查好的设备实体
     * @param productKey 物模型所属产品 Key
     * @param payload  JSON 字符串,形如 {"temperature": 25.6, "humidity": 58.2}
     */
    public void handlePropertyReport(IotDevice device, String productKey, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.isObject()) return;
            int count = 0;
            var fields = root.fields();
            while (fields.hasNext()) {
                var e = fields.next();
                String identifier = e.getKey();
                JsonNode valueNode = e.getValue();
                String valueJson;
                try {
                    valueJson = valueNode.isTextual() ? valueNode.asText() : objectMapper.writeValueAsString(valueNode);
                } catch (Exception ex) {
                    valueJson = valueNode.toString();
                }
                upsertProperty(device.getTenantId(), device.getId(),
                        String.valueOf(device.getProductId()),
                        device.getDeviceKey(), productKey,
                        identifier, valueJson);
                count++;
            }
            log.info("[processor] 设备[{}]属性上报 {} 条", device.getDeviceKey(), count);
        } catch (Throwable e) {
            log.error("[processor] 属性上报解析失败: device={} payload={}", device.getDeviceKey(), payload, e);
        }
    }

    /**
     * 处理事件上报
     */
    public void handleEventReport(IotDevice device, String identifier, JsonNode valueNode) {
        log.info("[processor] 设备[{}]事件上报: {}", device.getDeviceKey(), identifier);
        // TODO: 写 iot_event 表(P5 规则引擎阶段用到)
    }

    /**
     * 设备上线
     */
    public void markOnline(IotDevice device, String ipAddress) {
        boolean wasOnline = device.getStatus() != null && device.getStatus() == 1;
        device.setStatus(1);
        device.setLastOnlineTime(LocalDateTime.now());
        if (ipAddress != null) device.setIpAddress(ipAddress);
        if (device.getActiveTime() == null) device.setActiveTime(LocalDateTime.now());
        deviceMapper.updateById(device);
        if (!wasOnline) {
            try {
                wsPublisher.publishDeviceStatus(device.getTenantId(), device.getId(),
                        device.getDeviceKey(), 1);
            } catch (Exception ignored) {}
        }
        log.info("[processor] 设备[{}]上线", device.getDeviceKey());
    }

    /**
     * 设备离线
     */
    public void markOffline(IotDevice device) {
        if (device == null) return;
        device.setStatus(0);
        device.setLastOfflineTime(LocalDateTime.now());
        deviceMapper.updateById(device);
        try {
            wsPublisher.publishDeviceStatus(device.getTenantId(), device.getId(),
                    device.getDeviceKey(), 0);
        } catch (Exception ignored) {}
        log.info("[processor] 设备[{}]离线", device.getDeviceKey());
    }

    // ---------- 私有 ----------

    private void upsertProperty(Long tenantId, Long deviceId, String productId, String deviceKey,
                                 String productKey, String identifier, String valueJson) {
        IotDeviceProperty exist = propertyMapper.selectOne(new LambdaQueryWrapper<IotDeviceProperty>()
                .eq(IotDeviceProperty::getDeviceId, deviceId)
                .eq(IotDeviceProperty::getIdentifier, identifier));
        if (exist == null) {
            IotDeviceProperty p = new IotDeviceProperty();
            p.setTenantId(tenantId);
            p.setDeviceId(deviceId);
            p.setIdentifier(identifier);
            p.setValueJson(valueJson);
            propertyMapper.insert(p);
        } else {
            exist.setValueJson(valueJson);
            exist.setUpdatedAt(LocalDateTime.now());
            propertyMapper.updateById(exist);
        }
        // 写 TDengine(异步,失败不影响主流程)
        try {
            tdengineWriter.writeAsync(tenantId, deviceId, productKey, deviceKey, identifier, valueJson);
        } catch (Exception e) {
            log.error("[processor] TDengine 写入调度失败", e);
        }
        // 触发规则引擎 + WebSocket 实时推送
        Map<String, Object> shadows = loadAllShadows(deviceId);
        Object parsed = parseValue(valueJson);
        try {
            eventPublisher.publishEvent(new PropertyReportEvent(
                    this, tenantId, deviceId,
                    Long.parseLong(productId), deviceKey, productKey,
                    identifier, parsed, shadows));
        } catch (Exception e) {
            log.error("[processor] 发布事件失败", e);
        }
        try {
            wsPublisher.publishShadowUpdate(tenantId, deviceId, deviceKey,
                    identifier, parsed,
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } catch (Exception ignored) {}
    }

    private Map<String, Object> loadAllShadows(Long deviceId) {
        Map<String, Object> map = new HashMap<>();
        for (IotDeviceProperty p : propertyMapper.selectList(new LambdaQueryWrapper<IotDeviceProperty>()
                .eq(IotDeviceProperty::getDeviceId, deviceId))) {
            map.put(p.getIdentifier(), parseValue(p.getValueJson()));
        }
        return map;
    }

    private Object parseValue(String v) {
        if (v == null || v.isEmpty() || v.equals("null")) return null;
        try { return objectMapper.readTree(v); } catch (Exception e) { return v; }
    }
}
