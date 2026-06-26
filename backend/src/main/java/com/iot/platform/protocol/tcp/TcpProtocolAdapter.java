package com.iot.platform.protocol.tcp;

import com.iot.platform.config.IotProperties;
import com.iot.platform.protocol.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TCP 协议适配器(M3 阶段实现)
 *
 * 当前为占位实现。
 * TODO(M3):
 *   - Netty Server 监听 iot.protocol.tcp.port
 *   - 自定义帧协议(长度字段 + payload)
 *   - 握手时校验 deviceKey+deviceSecret
 *   - 解析 JSON 消息 → DeviceMessage
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.protocol.tcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TcpProtocolAdapter implements ProtocolAdapter {

    private final IotProperties properties;
    private MessageHandler handler;
    private volatile boolean running = false;

    @Override public String getName() { return "tcp"; }

    @Override
    public void start() {
        running = true;
        log.info("[TCP] 适配器已启动 port={} (骨架)", properties.getProtocol().getTcp().getPort());
    }

    @Override
    public void stop() {
        running = false;
        log.info("[TCP] 适配器已停止");
    }

    @Override public boolean isRunning() { return running; }

    @Override
    public void setMessageHandler(MessageHandler handler) {
        this.handler = handler;
    }

    @Override
    public boolean sendDownMessage(DeviceSession session, Map<String, Object> downMessage) {
        if (session == null || !running) return false;
        log.info("[TCP] 下行(骨架) deviceKey={} payload={}", session.getDeviceKey(), downMessage);
        return true;
    }
}
