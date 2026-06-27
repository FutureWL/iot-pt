package com.iot.platform.protocol.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.device.entity.IotDevice;
import com.iot.platform.device.mapper.IotDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 设备消息处理统一入口
 *
 * <p>所有"设备消息"最终都走这里,根据 type 分发:
 *   PROPERTY_REPORT → 写属性 + TDengine + 规则引擎 + WS
 *   EVENT_REPORT    → 写事件表
 *   ONLINE / OFFLINE → 更新设备状态 + WS
 * </p>
 *
 * <p>同进程时由 LocalIotMessagePublisher 调用;
 * 跨进程时由 RedisIotMessageConsumer 反序列化后调用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceMessageHandler {

    private final DeviceMessageProcessor processor;
    private final IotDeviceMapper deviceMapper;
    private final ObjectMapper objectMapper;

    public void handle(IotMessageEnvelope env) {
        log.info("[handler] 收到 envelope type={} device={}", env.getType(), env.getDeviceKey());
        // 优先用 deviceId;没有则按 deviceKey 查(MQTT/TCP 适配器不知道内部 ID)
        IotDevice device = null;
        if (env.getDeviceId() != null) {
            device = deviceMapper.selectById(env.getDeviceId());
        }
        if (device == null && env.getDeviceKey() != null && !env.getDeviceKey().isEmpty()) {
            device = deviceMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IotDevice>()
                            .eq(IotDevice::getDeviceKey, env.getDeviceKey()));
        }
        if (device == null) {
            log.warn("[handler] 设备未找到: deviceId={} deviceKey={}",
                    env.getDeviceId(), env.getDeviceKey());
            return;
        }

        switch (env.getType()) {
            case "PROPERTY_REPORT" -> processor.handlePropertyReport(
                    device, env.getProductKey(), env.getPayload());
            case "EVENT_REPORT" -> handleEventReport(device, env);
            case "ONLINE" -> processor.markOnline(device, env.getRemoteAddress());
            case "OFFLINE" -> processor.markOffline(device);
            default -> log.warn("[handler] 未知消息类型: {}", env.getType());
        }
    }

    private void handleEventReport(IotDevice device, IotMessageEnvelope env) {
        try {
            JsonNode node = env.getPayload() == null ? null : objectMapper.readTree(env.getPayload());
            String identifier = env.getEventIdentifier();
            processor.handleEventReport(device, identifier, node);
        } catch (Exception e) {
            log.error("[handler] 事件上报解析失败: {}", e);
        }
    }
}
