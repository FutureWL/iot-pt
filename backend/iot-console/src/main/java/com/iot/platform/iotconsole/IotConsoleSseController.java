package com.iot.platform.iotconsole;

import com.iot.platform.protocol.core.IotMessageEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * IoT 控制台 SSE 实时推送
 *
 * <p>前端 EventSource 订阅 /api/iot-console/stream
 * 收到的事件类型:
 *   - event: "msg"    data: {envelope JSON}  新的设备消息
 *   - event: "ping"   data: {}                 30s 心跳(防超时)
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/iot-console")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.console", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IotConsoleSseController {

    private final SpyBuffer spyBuffer;
    private final IotMetricsService metrics;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong emitterSeq = new AtomicLong();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        // 30 分钟超时(防止僵尸连接)
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        long id = emitterSeq.incrementAndGet();
        emitter.onCompletion(() -> removeEmitter(emitter));
        emitter.onTimeout(() -> removeEmitter(emitter));
        emitter.onError(t -> removeEmitter(emitter));
        emitters.add(emitter);
        log.info("[iot-console] SSE 客户端连接 #{}, 当前 {} 个", id, emitters.size());

        // 先发最近 50 条历史
        try {
            for (IotMessageEnvelope env : spyBuffer.snapshot(50)) {
                emitter.send(SseEmitter.event().name("msg").data(env));
            }
            emitter.send(SseEmitter.event().name("ping").data("{}"));
        } catch (IOException e) {
            removeEmitter(emitter);
        }

        // 订阅新消息
        Consumer<IotMessageEnvelope> listener = env -> {
            try {
                emitter.send(SseEmitter.event().name("msg").data(env));
            } catch (IOException e) {
                removeEmitter(emitter);
            }
        };
        spyBuffer.subscribe(listener);
        emitter.onCompletion(() -> spyBuffer.unsubscribe(listener));
        emitter.onTimeout(() -> spyBuffer.unsubscribe(listener));
        emitter.onError(t -> spyBuffer.unsubscribe(listener));

        return emitter;
    }

    private void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }
}
