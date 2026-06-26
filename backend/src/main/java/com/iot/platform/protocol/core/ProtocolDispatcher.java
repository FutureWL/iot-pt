package com.iot.platform.protocol.core;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 协议分发器 - 全局单例
 *
 * 1. 启动时收集所有 ProtocolAdapter bean;
 * 2. 提供按名称获取适配器;
 * 3. 维护设备 session 索引(在线设备表);
 * 4. 平台启动时设置统一 MessageHandler。
 *
 * @author IoT Platform Team
 */
@Slf4j
public class ProtocolDispatcher {

    private final Map<String, ProtocolAdapter> adapters = new ConcurrentHashMap<>();
    private final Map<String, DeviceSession> onlineSessions = new ConcurrentHashMap<>();

    private MessageHandler messageHandler;

    public void register(ProtocolAdapter adapter) {
        adapters.put(adapter.getName(), adapter);
        adapter.setMessageHandler(message -> dispatch(message));
        // 上线/下线由适配器主动调 onSessionConnect / onSessionDisconnect
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

    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    private void dispatch(DeviceMessage message) {
        if (messageHandler != null) {
            try {
                messageHandler.onMessage(message);
            } catch (Exception e) {
                log.error("处理设备消息异常: deviceKey={}, type={}", message.getDeviceKey(), message.getType(), e);
            }
        }
    }

    // ---- session 管理(由具体适配器回调) ----

    public void onSessionConnect(DeviceSession session) {
        onlineSessions.put(session.getDeviceKey(), session);
        log.info("设备上线: protocol={}, deviceKey={}", session.getProtocol(), session.getDeviceKey());
        // 业务层需要感知上下线时,可在此发事件(M3 阶段接入 Spring ApplicationEventPublisher)
    }

    public void onSessionDisconnect(DeviceSession session) {
        DeviceSession removed = onlineSessions.remove(session.getDeviceKey());
        if (removed != null) {
            log.info("设备离线: protocol={}, deviceKey={}", session.getProtocol(), session.getDeviceKey());
        }
    }

    /** 全局启动/停止入口 */
    public void startAll() {
        adapters.values().forEach(ProtocolAdapter::start);
    }

    public void stopAll() {
        adapters.values().forEach(ProtocolAdapter::stop);
    }
}
