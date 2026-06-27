package com.iot.platform.protocol.core;

/**
 * 设备消息发布器抽象
 *
 * <p>协议适配器把"收到的设备消息"通过此接口发布出去。
 * 具体实现:
 *   - {@code LocalIotMessagePublisher}:同进程内同步调用 DeviceMessageProcessor
 *   - {@code RedisIotMessagePublisher}: 跨进程发到 Redis Stream</p>
 *
 * <p>通过 profile 决定激活哪个实现。</p>
 */
public interface IotMessagePublisher {

    /**
     * 发布设备消息
     * @param envelope 消息信封(包含 deviceKey / type / payload 等)
     */
    void publish(IotMessageEnvelope envelope);
}
