package com.iot.platform.protocol.core;

/**
 * 设备消息观察者 - iot-common 定义的 SPI
 *
 * <p>由 iot-console 实现(抓包/SSE 推送),
 * 通过 ObjectProvider< MessageObserver > 注入到 ProtocolDispatcher,
 * iot-common 不需要直接依赖 iot-console。</p>
 */
@FunctionalInterface
public interface MessageObserver {
    void onEnvelope(IotMessageEnvelope envelope);
}
