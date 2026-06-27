package com.iot.platform.protocol.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * 设备消息跨进程传输信封
 *
 * <p>IoT 进程把 DeviceMessage 包装成 Envelope 发到 Redis Stream,
 * API 进程订阅 Stream 收到 Envelope 后反序列化处理。</p>
 *
 * <p>同一进程内,envelope 实际上只在 EventBus 上传递(零开销),
 * 但格式与跨进程一致,便于以后切换到 Kafka/Pub-Sub。</p>
 *
 * @author IoT Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class IotMessageEnvelope {

    /** 消息 ID(UUID,用于去重) */
    private String id;

    /** 消息类型:PROPERTY_REPORT / EVENT_REPORT / ONLINE / OFFLINE */
    private String type;

    /** 协议 mqtt / tcp */
    private String protocol;

    /** 设备内部 ID(API 进程里用) */
    private Long deviceId;

    /** 设备 Key */
    private String deviceKey;

    /** 产品 Key */
    private String productKey;

    /** 设备 IP(ONLINE 时用) */
    private String remoteAddress;

    /** 负载:属性 JSON 字符串 / 事件 identifier / null */
    private String payload;

    /** 事件输出 identifier(EVENT 时用) */
    private String eventIdentifier;

    /** 设备时间戳(毫秒) */
    private Long timestamp;

    /** 平台时间戳(毫秒) */
    private Long receivedAt;

    @SneakyThrows
    public String toJson(ObjectMapper objectMapper) {
        return objectMapper.writeValueAsString(this);
    }

    @SneakyThrows
    public static IotMessageEnvelope fromJson(String json, ObjectMapper objectMapper) {
        return objectMapper.readValue(json, IotMessageEnvelope.class);
    }

    public static IotMessageEnvelope fromDeviceMessage(DeviceMessage msg, Long deviceId) {
        return IotMessageEnvelope.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(msg.getType() == null ? "" : msg.getType().name())
                .protocol(msg.getProtocol())
                .deviceId(deviceId)
                .deviceKey(msg.getDeviceKey())
                .productKey(msg.getProductKey())
                .remoteAddress(null)
                .payload(extractPayloadString(msg))
                .timestamp(msg.getTimestamp())
                .receivedAt(Instant.now().toEpochMilli())
                .build();
    }

    private static String extractPayloadString(DeviceMessage msg) {
        if (msg.getPayload() == null || msg.getPayload().isEmpty()) return null;
        try {
            // 这里仅做示意:真实序列化在调用处用 ObjectMapper 做
            return msg.getPayload().toString();
        } catch (Exception e) {
            return null;
        }
    }
}
