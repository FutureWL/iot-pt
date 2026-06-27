package com.iot.platform.protocol.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备会话(连接上下文)
 *
 * 由具体协议适配器创建,平台用于:
 *   - 下行消息时定位连接
 *   - 上下线状态跟踪
 *   - 设备级属性存储(如 clientId、remoteAddress)
 *
 * @author IoT Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSession {

    /** 协议层会话 ID(由各协议内部生成,MQTT 通常是 clientId,TCP 是 channelId) */
    private String sessionId;

    /** 设备唯一标识 */
    private String deviceKey;

    /** 产品 Key */
    private String productKey;

    /** 协议名称 */
    private String protocol;

    /** 客户端地址 */
    private String remoteAddress;

    /** 认证是否通过 */
    private boolean authenticated;

    /** 上下线时间 */
    private Instant connectTime;

    private Instant lastActiveTime;

    /**
     * 协议相关扩展属性(如 MQTT 的 clientId、TCP 的 channel,供下行时使用)
     * 键值含义由各协议自行约定。
     */
    @Builder.Default
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    public void touch() {
        this.lastActiveTime = Instant.now();
    }
}
