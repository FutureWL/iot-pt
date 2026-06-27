package com.iot.platform.protocol.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 跨进程消息消费者 - API 进程用
 *
 * <p>订阅 Redis Stream "iot:device:events",每条消息反序列化为 Envelope,
 * 调 {@link DeviceMessageHandler} 处理。</p>
 *
 * <p>激活条件: iot.role=api</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "iot.role", havingValue = "api")
public class RedisIotMessageConsumer {

    public static final String GROUP = "iot-api-consumer";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DeviceMessageHandler handler;

    private volatile boolean running = false;
    private Thread consumerThread;

    @PostConstruct
    public void start() {
        // 创建 consumer group
        try {
            redis.opsForStream().createGroup(
                    RedisIotMessagePublisher.STREAM_KEY,
                    ReadOffset.from("0"),
                    GROUP);
            log.info("[redis-consumer] 创建 consumer group: {}", GROUP);
        } catch (Exception e) {
            // BUSYGROUP - group 已存在
            log.debug("[redis-consumer] consumer group 已存在: {}", e.getMessage());
        }

        running = true;
        consumerThread = new Thread(this::consumeLoop, "iot-stream-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("[redis-consumer] 启动订阅 stream={} group={}",
                RedisIotMessagePublisher.STREAM_KEY, GROUP);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (consumerThread != null) consumerThread.interrupt();
        log.info("[redis-consumer] 已停止");
    }

    private void consumeLoop() {
        String consumerName = "api-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        while (running) {
            try {
                // 每次最多阻塞 2s,自动返回(支持优雅停机)
                StreamReadOptions options = StreamReadOptions.empty()
                        .count(100)
                        .block(Duration.ofSeconds(2));
                @SuppressWarnings("unchecked")
                List<MapRecord<String, Object, Object>> records = (List<MapRecord<String, Object, Object>>) (List<?>)
                        redis.opsForStream().read(
                                options,
                                StreamOffset.create(RedisIotMessagePublisher.STREAM_KEY, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    try {
                        handleRecord(record);
                    } catch (Exception e) {
                        log.error("[redis-consumer] 单条处理失败 (id={}): {}",
                                record.getId(), e.getMessage());
                    }
                }
            } catch (org.springframework.dao.DataAccessException e) {
                log.error("[redis-consumer] 拉取失败,5s 后重试: {}", e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
            } catch (Exception e) {
                log.error("[redis-consumer] 未知异常", e);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { break; }
            }
        }
    }

    private void handleRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> body = record.getValue();
        if (body == null || body.isEmpty()) {
            log.warn("[redis-consumer] 记录无 payload: id={}", record.getId());
            return;
        }
        // ObjectRecord 的 value 是个包含 "__object" key 的 Map,value 是 JSON 字符串
        Object raw = null;
        for (Object v : body.values()) {
            raw = v;
            break;
        }
        if (raw == null) {
            log.warn("[redis-consumer] 记录 value 为空: id={}", record.getId());
            return;
        }
        String json = raw.toString();
        try {
            IotMessageEnvelope env = IotMessageEnvelope.fromJson(json, objectMapper);
            handler.handle(env);
            redis.opsForStream().acknowledge(RedisIotMessagePublisher.STREAM_KEY, GROUP, record.getId());
        } catch (Exception e) {
            log.error("[redis-consumer] 处理消息失败 (recordId={}): {}",
                    record.getId(), e.getMessage(), e);
            // 不 ACK,下次重投
        }
    }
}
