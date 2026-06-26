package com.iot.platform.protocol.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备消息 - 所有协议(MQTT/TCP/...)解析后归一化的消息结构。
 *
 * 设计原则:
 *   1. 协议无关 — 业务层只看到 DeviceMessage,不关心是哪种协议来的。
 *   2. 自描述 — 携带 productKey/deviceKey,业务层可单点定位。
 *   3. payload 是物模型数据 — 属性名 → 值,事件名 → 输出参数。
 *
 * @author IoT Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMessage {

    /** 消息 ID(全链路追踪) */
    private String messageId;

    /** 协议名称 mqtt/tcp */
    private String protocol;

    /** 设备唯一标识(对应 iot_device.device_key) */
    private String deviceKey;

    /** 设备产品 Key */
    private String productKey;

    /** 消息类型 */
    private MessageType type;

    /** 设备时间戳(毫秒) */
    private Long timestamp;

    /** 平台接收时间(毫秒) */
    private Long receivedAt;

    /** 主题(可选,用于调试) */
    private String topic;

    /**
     * 物模型负载:
     * - PROPERTY_REPORT: 属性 identifier → value
     * - EVENT_REPORT:    { eventIdentifier, outputParams: { ... } }
     * - SERVICE_REPLY:   { serviceIdentifier, outputParams: { ... }, code, message }
     * - 其他类型可空
     */
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    /** 原始报文(JSON 字符串,用于排错) */
    private String rawPayload;
}
