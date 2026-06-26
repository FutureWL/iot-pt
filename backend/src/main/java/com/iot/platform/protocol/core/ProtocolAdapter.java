package com.iot.platform.protocol.core;

import java.util.Map;

/**
 * 协议适配器 SPI(关键扩展点)
 *
 * 所有协议(MQTT / TCP / HTTP / CoAP ...)实现此接口,
 * 平台通过 Spring 自动注入并管理。
 *
 * 下行发送时,参数 {@code downMessage} 的 Map 结构:
 * <pre>
 * - 服务调用: { type: "service", serviceIdentifier: "openLock", inputParams: {...} }
 * - 属性设置: { type: "property", properties: { switch: true } }
 * - 自定义:   { type: "raw", topic: "...", payload: {...} }
 * </pre>
 *
 * @author IoT Platform Team
 */
public interface ProtocolAdapter {

    /** 协议名称: mqtt / tcp / http ... */
    String getName();

    /** 启动适配器 */
    void start();

    /** 停止适配器 */
    void stop();

    /** 是否运行中 */
    boolean isRunning();

    /**
     * 绑定消息处理回调(由平台在启动时调用,只能绑定一次)
     */
    void setMessageHandler(MessageHandler handler);

    /**
     * 下行发送消息到设备
     *
     * @param session     目标设备会话
     * @param downMessage 下行消息体(见接口注释)
     * @return 是否成功放入发送队列
     */
    boolean sendDownMessage(DeviceSession session, Map<String, Object> downMessage);
}
