package com.iot.platform.protocol.mqtt;

import com.iot.platform.config.IotProperties;
import com.iot.platform.protocol.core.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MQTT 协议适配器(M3 阶段实现)
 *
 * 当前为占位实现:
 * 1. 暂时不连接 broker(后续阶段实现 paho / hivemq client 集成)
 * 2. 保留接口,保证 Spring 能正常注册
 *
 * TODO(M3):
 *   - 使用 HiveMQ MQTT Client 连接 EMQX
 *   - 订阅 /sys/+/+/thing/+/+/+ 通配主题
 *   - 解析属性/事件/服务回复 → 归一化为 DeviceMessage
 *   - 下行通过 publish 到 /sys/{pk}/{dk}/thing/service/{svc}/invoke
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.protocol.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttProtocolAdapter implements ProtocolAdapter {

    private final IotProperties properties;
    private MessageHandler handler;
    private volatile boolean running = false;

    @Override public String getName() { return "mqtt"; }

    @PostConstruct
    public void init() {
        log.info("[MQTT] 适配器初始化 broker={} (M3 阶段实现实际连接)", properties.getProtocol().getMqtt().getBrokerUrl());
    }

    @Override
    public void start() {
        running = true;
        log.info("[MQTT] 适配器已启动 (骨架)");
        // TODO M3: 实现连接、订阅、解析
    }

    @Override
    public void stop() {
        running = false;
        log.info("[MQTT] 适配器已停止");
    }

    @Override public boolean isRunning() { return running; }

    @Override
    public void setMessageHandler(MessageHandler handler) {
        this.handler = handler;
    }

    @Override
    public boolean sendDownMessage(DeviceSession session, Map<String, Object> downMessage) {
        if (session == null || !running) return false;
        // TODO M3: 真实 publish
        log.info("[MQTT] 下行(骨架) deviceKey={} payload={}", session.getDeviceKey(), downMessage);
        return true;
    }
}
