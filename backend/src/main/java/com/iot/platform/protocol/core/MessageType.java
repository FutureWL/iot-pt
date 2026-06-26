package com.iot.platform.protocol.core;

/**
 * 设备消息类型(所有协议解析后统一使用)
 *
 * @author IoT Platform Team
 */
public enum MessageType {

    /** 设备上线 */
    ONLINE,

    /** 设备离线 */
    OFFLINE,

    /** 心跳 */
    HEARTBEAT,

    /** 属性上报 */
    PROPERTY_REPORT,

    /** 事件上报 */
    EVENT_REPORT,

    /** 服务调用回复 */
    SERVICE_REPLY,

    /** 属性/服务指令下行(平台到设备) */
    DOWNLINK
}
