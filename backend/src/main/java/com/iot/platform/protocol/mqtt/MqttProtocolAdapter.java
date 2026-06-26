package com.iot.platform.protocol.mqtt;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iot.platform.config.IotProperties;
import com.iot.platform.datamanage.service.TdengineWriter;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.entity.IotDeviceProperty;
import com.iot.platform.device.mapper.IotDeviceMapper;
import com.iot.platform.device.mapper.IotDevicePropertyMapper;
import com.iot.platform.product.entity.IotProduct;
import com.iot.platform.product.mapper.IotProductMapper;
import com.iot.platform.protocol.core.*;
import com.iot.platform.rule.event.PropertyReportEvent;
import com.iot.platform.websocket.WebSocketEventPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MQTT 协议适配器 - 真实实现
 *
 * <p>主题规范(类 Aliyun 物模型):</p>
 * <ul>
 *   <li>属性上报: {@code iot/{productKey}/{deviceKey}/property/post}</li>
 *   <li>事件上报: {@code iot/{productKey}/{deviceKey}/event/post}</li>
 *   <li>下行属性设置: 平台 publish 到 {@code iot/{productKey}/{deviceKey}/property/set}</li>
 *   <li>下行服务调用: 平台 publish 到 {@code iot/{productKey}/{deviceKey}/service/{identifier}/invoke}</li>
 * </ul>
 *
 * <p>Payload 格式(JSON):</p>
 * <ul>
 *   <li>属性上报: {@code {"temperature": 25.6, "humidity": 58.2}}</li>
 *   <li>事件上报: {@code {"identifier": "high_temp", "value": {...}, "timestamp": "..."}}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.protocol.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttProtocolAdapter implements ProtocolAdapter {

    private final IotProperties properties;
    private final IotDeviceMapper deviceMapper;
    private final IotDevicePropertyMapper propertyMapper;
    private final IotProductMapper productMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TdengineWriter tdengineWriter;
    private final WebSocketEventPublisher wsPublisher;

    private MqttClient client;
    private volatile boolean running = false;
    private MessageHandler handler;

    @Override public String getName() { return "mqtt"; }

    @PostConstruct
    public void init() {
        log.info("[MQTT] 适配器初始化 broker={}", properties.getProtocol().getMqtt().getBrokerUrl());
    }

    @Override
    public void start() {
        IotProperties.Protocol.Mqtt cfg = properties.getProtocol().getMqtt();
        String broker = cfg.getBrokerUrl();
        if (broker == null || broker.isBlank()) {
            log.warn("[MQTT] brokerUrl 未配置,跳过启动");
            return;
        }

        String clientId = "iot-platform-" + IdUtil.fastSimpleUUID();
        try {
            client = new MqttClient(broker, clientId, new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);
            opts.setKeepAliveInterval(30);
            opts.setConnectionTimeout(10);
            opts.setMaxInflight(100);
            if (cfg.getUsername() != null && !cfg.getUsername().isBlank()) {
                opts.setUserName(cfg.getUsername());
            }
            if (cfg.getPassword() != null && !cfg.getPassword().isBlank()) {
                opts.setPassword(cfg.getPassword().toCharArray());
            }

            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("[MQTT] {}连接到 {}", reconnect ? "重连" : "已", serverURI);
                    doSubscribe();
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("[MQTT] 连接断开: {}", cause == null ? "未知" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) { /* noop */ }
            });

            client.connect(opts);
            running = true;
            doSubscribe();
            log.info("[MQTT] 适配器已启动 clientId={}", clientId);
        } catch (Exception e) {
            log.error("[MQTT] 启动失败 broker={} err={}", broker, e.getMessage());
        }
    }

    private void doSubscribe() {
        if (client == null || !client.isConnected()) return;
        try {
            // 设备属性上报: iot/{productKey}/{deviceKey}/property/post
            client.subscribe("iot/+/+/property/post", 1);
            // 设备事件上报: iot/{productKey}/{deviceKey}/event/post
            client.subscribe("iot/+/+/event/post", 1);
            log.info("[MQTT] 订阅完成: iot/+/+/property/post, iot/+/+/event/post");
        } catch (MqttException e) {
            log.error("[MQTT] 订阅失败", e);
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        try {
            // 解析 topic
            String[] parts = topic.split("/");
            if (parts.length < 5) return;
            // iot / {productKey} / {deviceKey} / {type} / post
            String productKey = parts[1];
            String deviceKey = parts[2];
            String msgType = parts[3] + "/" + parts[4];  // "property/post" or "event/post"

            // 找设备
            IotDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<IotDevice>()
                    .eq(IotDevice::getDeviceKey, deviceKey));
            if (device == null) {
                log.warn("[MQTT] 收到未知设备消息: productKey={} deviceKey={}", productKey, deviceKey);
                return;
            }
            // 校验产品 Key
            IotProduct product = productMapper.selectById(device.getProductId());
            if (product == null || !product.getProductKey().equals(productKey)) {
                log.warn("[MQTT] topic productKey 与设备不匹配: device={}, topic_pk={}", deviceKey, productKey);
                return;
            }

            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.debug("[MQTT] 收到 topic={} payload={}", topic, payload);

            if ("property/post".equals(msgType)) {
                handlePropertyReport(device, productKey, payload);
            } else if ("event/post".equals(msgType)) {
                handleEventReport(device, payload);
            }

            // 标记设备在线
            markOnline(device);
        } catch (Throwable e) {
            log.error("[MQTT] 处理消息失败: topic={}", topic, e);
        }
    }

    /**
     * 处理属性上报
     * payload 形如: {"temperature": 25.6, "humidity": 58.2}
     */
    private void handlePropertyReport(IotDevice device, String productKey, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.isObject()) return;
            int count = 0;
            var fields = root.fields();
            while (fields.hasNext()) {
                var e = fields.next();
                String identifier = e.getKey();
                JsonNode valueNode = e.getValue();
                // 验证标识符在物模型中
                if (!identifierInThingModel(device, identifier)) {
                    log.warn("[MQTT] 属性[{}]不在物模型中,跳过", identifier);
                    continue;
                }
                String valueJson;
                try {
                    valueJson = valueNode.isTextual() ? valueNode.asText() : objectMapper.writeValueAsString(valueNode);
                } catch (Exception ex) {
                    valueJson = valueNode.toString();
                }
                // 去掉多余的双引号(写时序库用)
                upsertProperty(device.getTenantId(), device.getId(),
                        String.valueOf(device.getProductId()),
                        device.getDeviceKey(), productKey,
                        identifier, valueJson);
                count++;
            }
            log.info("[MQTT] 设备[{}]属性上报 {} 条", device.getDeviceKey(), count);
        } catch (Throwable e) {
            log.error("[MQTT] 属性上报解析失败: device={} payload={}", device.getDeviceKey(), payload, e);
        }
    }

    /**
     * 处理事件上报
     * payload 形如: {"identifier": "high_temp", "value": {"temp": 36.5}, "timestamp": "..."}
     */
    private void handleEventReport(IotDevice device, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String identifier = root.path("identifier").asText(null);
            if (identifier == null || identifier.isEmpty()) {
                log.warn("[MQTT] 事件消息缺少 identifier: device={}", device.getDeviceKey());
                return;
            }
            log.info("[MQTT] 设备[{}]事件上报: {}", device.getDeviceKey(), identifier);
            // TODO: 写入 iot_event 表(暂未建表),P5 规则引擎阶段会用到
        } catch (Exception e) {
            log.error("[MQTT] 事件上报解析失败: device={} payload={}", device.getDeviceKey(), payload, e);
        }
    }

    private boolean identifierInThingModel(IotDevice device, String identifier) {
        IotProduct p = productMapper.selectById(device.getProductId());
        if (p == null || p.getThingModel() == null) return false;
        try {
            JsonNode root = objectMapper.readTree(p.getThingModel());
            JsonNode props = root.path("properties");
            if (props.isArray()) {
                for (JsonNode n : props) {
                    if (identifier.equals(n.path("identifier").asText())) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void upsertProperty(Long tenantId, Long deviceId, String productId, String deviceKey, String productKey,
                                 String identifier, String valueJson) {
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
        // 同步写 TDengine(异步,失败不影响主流程)
        try {
            tdengineWriter.writeAsync(tenantId, deviceId, productKey, deviceKey, identifier, valueJson);
        } catch (Exception e) {
            log.error("[MQTT] TDengine 写入调度失败", e);
        }
        // 触发规则引擎事件
        Map<String, Object> shadows = loadAllShadows(deviceId);
        Object parsed = parseValue(valueJson);
        try {
            eventPublisher.publishEvent(new PropertyReportEvent(
                    this, tenantId, deviceId,
                    Long.parseLong(productId), deviceKey, productKey,
                    identifier, parsed, shadows));
        } catch (Exception e) {
            log.error("[MQTT] 发布事件失败", e);
        }
        // WebSocket 实时推送
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

    private void markOnline(IotDevice device) {
        if (device.getStatus() != null && device.getStatus() == 1) {
            // 已经在在线,只更新 lastOnlineTime
            device.setLastOnlineTime(LocalDateTime.now());
            deviceMapper.updateById(device);
        } else {
            device.setStatus(1);
            device.setLastOnlineTime(LocalDateTime.now());
            if (device.getActiveTime() == null) {
                device.setActiveTime(LocalDateTime.now());
            }
            deviceMapper.updateById(device);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (client != null) {
            try {
                if (client.isConnected()) client.disconnect();
                client.close();
            } catch (MqttException ignored) {}
        }
        log.info("[MQTT] 适配器已停止");
    }

    @Override public boolean isRunning() { return running; }

    @Override
    public void setMessageHandler(MessageHandler handler) {
        this.handler = handler;
    }

    @Override
    public boolean sendDownMessage(DeviceSession session, Map<String, Object> downMessage) {
        if (session == null || !running || client == null || !client.isConnected()) return false;
        try {
            // 下行: iot/{productKey}/{deviceKey}/property/set
            // 简化: 把整个 downMessage 作为一个 JSON 数组发到 property/set
            ObjectNode payload = objectMapper.createObjectNode();
            downMessage.forEach((k, v) -> {
                if (v instanceof Number) payload.put(k, ((Number) v).doubleValue());
                else if (v instanceof Boolean) payload.put(k, (Boolean) v);
                else payload.put(k, String.valueOf(v));
            });
            byte[] body = objectMapper.writeValueAsBytes(payload);
            String topic = "iot/" + session.getProductKey() + "/" + session.getDeviceKey() + "/property/set";
            MqttMessage msg = new MqttMessage(body);
            msg.setQos(1);
            client.publish(topic, msg);
            log.info("[MQTT] 下行发送 topic={} payload={}", topic, payload);
            return true;
        } catch (Exception e) {
            log.error("[MQTT] 下行失败: {}", e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void destroy() { stop(); }
}