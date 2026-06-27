package com.iot.platform.protocol.mqtt;

import cn.hutool.core.util.IdUtil;
import com.iot.platform.config.IotProperties;
import com.iot.platform.protocol.core.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * MQTT 协议适配器 - 精简版
 *
 * <p>只负责:连接 EMQX、订阅、解析 → 调 dispatcher.dispatch(DeviceMessage)
 * 业务处理(写库/规则/WS)由 dispatcher → IotMessagePublisher → DeviceMessageProcessor 负责</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${iot.role:all}' != 'api' && '${iot.protocol.mqtt.enabled:true}' == 'true'")
public class MqttProtocolAdapter implements ProtocolAdapter {

    private final IotProperties properties;
    private final ProtocolDispatcher dispatcher;

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
            if (cfg.getUsername() != null && !cfg.getUsername().isBlank()) opts.setUserName(cfg.getUsername());
            if (cfg.getPassword() != null && !cfg.getPassword().isBlank()) opts.setPassword(cfg.getPassword().toCharArray());

            client.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("[MQTT] {}连接到 {}", reconnect ? "重连" : "已", serverURI);
                    doSubscribe();
                }
                @Override public void connectionLost(Throwable cause) {
                    log.warn("[MQTT] 连接断开: {}", cause == null ? "未知" : cause.getMessage());
                }
                @Override public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(topic, message);
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) { /* noop */ }
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
            client.subscribe("iot/+/+/property/post", 1);
            client.subscribe("iot/+/+/event/post", 1);
            // 设备下行(服务调用 + 属性设置)
            client.subscribe("iot/+/+/property/set", 1);
            client.subscribe("iot/+/+/service/+/invoke", 1);
            log.info("[MQTT] 订阅完成: property/post, event/post, property/set, service/+/invoke");
        } catch (MqttException e) {
            log.error("[MQTT] 订阅失败", e);
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        try {
            String[] parts = topic.split("/");
            if (parts.length < 5) return;
            String productKey = parts[1];
            String deviceKey = parts[2];
            String msgType = parts[3] + "/" + parts[4];  // "property/post" / "event/post"
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.debug("[MQTT] 收到 topic={} payload={}", topic, payload);

            DeviceMessage.DeviceMessageBuilder builder = DeviceMessage.builder()
                    .messageId(IdUtil.fastSimpleUUID())
                    .protocol("mqtt")
                    .deviceKey(deviceKey)
                    .productKey(productKey)
                    .receivedAt(System.currentTimeMillis())
                    .rawPayload(payload);

            if ("property/post".equals(msgType)) {
                builder.type(MessageType.PROPERTY_REPORT)
                       .timestamp(System.currentTimeMillis())
                       .payload(parsePropertyPayload(payload));
            } else if ("event/post".equals(msgType)) {
                builder.type(MessageType.EVENT_REPORT)
                       .payload(parseEventPayload(payload));
            } else {
                log.debug("[MQTT] 收到下行/未知 topic,忽略: {}", topic);
                return;
            }

            DeviceMessage devMsg = builder.build();
            // 调 dispatcher 入口
            if (handler != null) handler.onMessage(devMsg);

        } catch (Throwable e) {
            log.error("[MQTT] 处理失败: topic={}", topic, e);
        }
    }

    private java.util.Map<String, Object> parsePropertyPayload(String payload) {
        // 形如 {"temperature": 25.6, "humidity": 58.2}
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(payload);
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            if (root.isObject()) {
                root.fields().forEachRemaining(e -> map.put(e.getKey(), unwrap(e.getValue())));
            }
            return map;
        } catch (Exception e) {
            return java.util.Collections.singletonMap("raw", payload);
        }
    }

    private java.util.Map<String, Object> parseEventPayload(String payload) {
        // 形如 {"identifier":"high_temp","value":{...},"timestamp":...}
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(payload);
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            if (root.isObject()) {
                map.put("identifier", root.path("identifier").asText());
                map.put("value", unwrap(root.path("value")));
                map.put("timestamp", root.path("timestamp").asLong());
            }
            return map;
        } catch (Exception e) {
            return java.util.Collections.singletonMap("raw", payload);
        }
    }

    private Object unwrap(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isTextual()) return node.asText();
        return node.toString();
    }

    @Override
    public void stop() {
        running = false;
        if (client != null) {
            try { if (client.isConnected()) client.disconnect(); client.close(); } catch (MqttException ignored) {}
        }
        log.info("[MQTT] 适配器已停止");
    }

    @Override public boolean isRunning() { return running; }
    @Override public void setMessageHandler(MessageHandler handler) { this.handler = handler; }
    @Override
    public boolean sendDownMessage(DeviceSession session, java.util.Map<String, Object> downMessage) {
        if (session == null || !running || client == null || !client.isConnected()) return false;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            String topic = "iot/" + session.getProductKey() + "/" + session.getDeviceKey() + "/property/set";
            byte[] body = om.writeValueAsBytes(downMessage);
            MqttMessage msg = new MqttMessage(body);
            msg.setQos(1);
            client.publish(topic, msg);
            return true;
        } catch (Exception e) {
            log.error("[MQTT] 下行失败: {}", e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void destroy() { stop(); }
}
