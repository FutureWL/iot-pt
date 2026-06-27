package com.iot.platform.protocol.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 同进程内的消息发布器 - 直接调 DeviceMessageProcessor
 *
 * <p>激活条件: iot.processing.mode=local(默认) 或 不配置</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "iot.role", havingValue = "all", matchIfMissing = true)
public class LocalIotMessagePublisher implements IotMessagePublisher {

    private final DeviceMessageProcessor processor;
    private final DeviceMessageHandler handler;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(IotMessageEnvelope envelope) {
        log.info("[local-pub] 收到 envelope type={} device={} type-java={}",
                 envelope.getType(), envelope.getDeviceKey(),
                 envelope.getClass().getSimpleName());
        try {
            // 由 dispatcher 已经解析过 type,这里只转发
            handler.handle(envelope);
        } catch (Exception e) {
            log.error("[local-pub] 处理消息失败: {}", envelope.getId(), e);
        }
    }
}
