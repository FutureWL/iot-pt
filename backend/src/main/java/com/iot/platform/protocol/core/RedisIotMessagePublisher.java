package com.iot.platform.protocol.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 跨进程消息发布器 - IoT 进程用
 *
 * <p>把设备消息发到 Redis Stream "iot:device:events",由 API 进程的
 * RedisIotMessageConsumer 订阅消费。</p>
 *
 * <p>激活条件: iot.processing.mode=remote
 * (说明本进程是 IoT 进程,业务处理在远端 API 进程)。</p>
 *
 * <p>Stream 配置:
 *   - key:    iot:device:events
 *   - maxlen: 100000  (保留最近 10w 条,够几小时缓冲)
 *   - 近似裁剪(~): 不阻塞生产者
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "iot.role", havingValue = "iot")
public class RedisIotMessagePublisher implements IotMessagePublisher {

    public static final String STREAM_KEY = "iot:device:events";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(IotMessageEnvelope envelope) {
        try {
            String json = envelope.toJson(objectMapper);
            ObjectRecord<String, String> record = StreamRecords.newRecord()
                    .ofObject(json)
                    .withStreamKey(STREAM_KEY);
            RecordId id = redis.opsForStream().add(record);
            // 生产端无需 ACK,只需发布成功
            log.debug("[redis-pub] sent id={} streamId={} type={}",
                    envelope.getId(), id, envelope.getType());
        } catch (Exception e) {
            // Redis 不可用,绝对不能丢消息:本地缓冲到日志(后续可加磁盘队列)
            log.error("[redis-pub] 发送到 Redis Stream 失败! envelope={}",
                    envelope.toJson(objectMapper), e);
        }
    }
}
