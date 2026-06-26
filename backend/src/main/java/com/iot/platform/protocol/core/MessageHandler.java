package com.iot.platform.protocol.core;

/**
 * 设备消息处理回调(由业务层实现,协议适配器收到上行消息后回调)
 *
 * <p>设计目的: 解耦协议适配器与业务层。协议适配器只负责"接收并解析",
 * 业务层只看到归一化的 DeviceMessage。</p>
 *
 * <p>这是一个函数式接口 — 只能处理消息本体;
 * 设备上下线状态由 {@link ProtocolDispatcher} 维护,业务层可通过
 * {@code dispatcher.onlineSessions()} 查询在线设备,或订阅
 * {@code ProtocolAutoConfiguration} 暴露的事件。</p>
 *
 * @author IoT Platform Team
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * 设备消息上行回调
     *
     * @param message 已解析的标准设备消息
     */
    void onMessage(DeviceMessage message);
}
