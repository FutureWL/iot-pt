package com.iot.platform.protocol.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 协议分发器
 *
 * <p>职责:
 *   1. 收集所有 ProtocolAdapter
 *   2. 维护设备 session 索引(在线设备表)
 *   3. 适配器收到消息 → 包装成 IotMessageEnvelope → 通过 IotMessagePublisher 发出
 * </p>
 *
 * <p>具体发布方式(local / Redis)由 IotMessagePublisher 的实现决定,
 * 与本类解耦。</p>
 */
@Slf4j
@Component
public class ProtocolDispatcher {

    /**
     * 消息发布器(可选):
     *   - iot.role=all 时由 LocalIotMessagePublisher(同进程)实现
     *   - iot.role=iot 时由 RedisIotMessagePublisher(发到 Redis Stream)实现
     *   - iot.role=api 时无 publisher(只消费,不发布)
     */
    private final ObjectProvider<IotMessagePublisher> publisherProvider;

    /**
     * 消息观察者(由 iot-console 提供,iot-common 不直接依赖)
     * 用于 IoT 控制台抓包、SSE 推送等横切关注点。
     * iot-broker 没引 iot-console 时为空,不影响主流程。
     */
    private final ObjectProvider<MessageObserver> observerProvider;

    public ProtocolDispatcher(ObjectProvider<IotMessagePublisher> publisherProvider,
                              ObjectProvider<MessageObserver> observerProvider) {
        this.publisherProvider = publisherProvider;
        this.observerProvider = observerProvider;
    }

    private final Map<String, ProtocolAdapter> adapters = new ConcurrentHashMap<>();
    private final Map<String, DeviceSession> onlineSessions = new ConcurrentHashMap<>();

    public void register(ProtocolAdapter adapter) {
        adapters.put(adapter.getName(), adapter);
        // 适配器收到原始字节流,解析后回调 dispatch()
        adapter.setMessageHandler(this::dispatch);
    }

    public ProtocolAdapter get(String name) {
        return adapters.get(name);
    }

    public List<ProtocolAdapter> all() {
        return adapters.values().stream().collect(Collectors.toList());
    }

    public Map<String, DeviceSession> onlineSessions() {
        return onlineSessions;
    }

    /** 适配器调这里 → 发到下游 */
    public void dispatch(DeviceMessage message) {
        log.info("[dispatcher] 收到 deviceKey={} type={}", message.getDeviceKey(), message.getType());
        IotMessagePublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            log.debug("[dispatcher] 无 publisher(iot.role=api 模式,不发布)");
            return;
        }
        try {
            IotMessageEnvelope env = IotMessageEnvelope.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .type(message.getType() == null ? "" : message.getType().name())
                    .protocol(message.getProtocol())
                    .deviceKey(message.getDeviceKey())
                    .productKey(message.getProductKey())
                    .payload(serializePayload(message))
                    .timestamp(message.getTimestamp())
                    .receivedAt(java.time.Instant.now().toEpochMilli())
                    .build();
            publisher.publish(env);
            // 通知观察者(抓包/SSE),失败不影响主流程
            notifyObservers(env);
        } catch (Exception e) {
            log.error("dispatch 失败: deviceKey={}", message.getDeviceKey(), e);
        }
    }

    private void notifyObservers(IotMessageEnvelope env) {
        if (observerProvider == null) return;
        observerProvider.ifAvailable(obs -> {
            try { obs.onEnvelope(env); } catch (Exception e) {
                log.warn("observer 处理失败: {}", e.getMessage());
            }
        });
    }

    private String serializePayload(DeviceMessage msg) {
        if (msg.getPayload() == null || msg.getPayload().isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(msg.getPayload());
        } catch (Exception e) {
            return msg.getPayload().toString();
        }
    }

    // ---- session 管理(由具体适配器回调) ----

    public void onSessionConnect(DeviceSession session) {
        onlineSessions.put(session.getDeviceKey(), session);
        log.info("设备上线: protocol={}, deviceKey={}", session.getProtocol(), session.getDeviceKey());
        IotMessagePublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) return;
        // 通知业务层
        publisher.publish(IotMessageEnvelope.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type("ONLINE")
                .protocol(session.getProtocol())
                .deviceKey(session.getDeviceKey())
                .productKey(session.getProductKey())
                .remoteAddress(session.getRemoteAddress())
                .receivedAt(java.time.Instant.now().toEpochMilli())
                .build());
    }

    public void onSessionDisconnect(DeviceSession session) {
        DeviceSession removed = onlineSessions.remove(session.getDeviceKey());
        if (removed != null) {
            log.info("设备离线: protocol={}, deviceKey={}", session.getProtocol(), session.getDeviceKey());
            IotMessagePublisher publisher = publisherProvider.getIfAvailable();
            if (publisher == null) return;
            publisher.publish(IotMessageEnvelope.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .type("OFFLINE")
                    .protocol(session.getProtocol())
                    .deviceKey(session.getDeviceKey())
                    .productKey(session.getProductKey())
                    .receivedAt(java.time.Instant.now().toEpochMilli())
                    .build());
        }
    }

    /** 全局启动/停止入口(给 ProtocolAutoConfiguration 用) */
    public void startAll() {
        adapters.values().forEach(ProtocolAdapter::start);
    }

    public void stopAll() {
        adapters.values().forEach(ProtocolAdapter::stop);
    }
}
