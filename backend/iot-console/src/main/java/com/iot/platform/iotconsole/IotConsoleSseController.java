package com.iot.platform.iotconsole;

import com.iot.platform.protocol.core.IotMessageEnvelope;
import com.iot.platform.protocol.core.ProtocolDispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * IoT 控制台 SSE 实时推送
 *
 * <p>前端 EventSource 订阅 /api/iot-console/stream
 * 收到的事件类型:
 *   - event: "msg"     data: envelope JSON        新设备消息(每条 envelope)
 *   - event: "status"  data: 概览统计 JSON        每 5 秒(在线数/TPS/总数)
 *   - event: "devices" data: 在线设备列表 JSON     每 5 秒 + 启动时立即推一次
 *   - event: "ping"    data: {}                   30s 心跳(防超时)
 * </p>
 *
 * <p>前端不再轮询 REST 端点,所有状态通过 SSE 推送。</p>
 */
@Slf4j
@RestController
@RequestMapping("/iot-console")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.console", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IotConsoleSseController {

    private final SpyBuffer spyBuffer;
    private final IotMetricsService metrics;
    private final ProtocolDispatcher dispatcher;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong emitterSeq = new AtomicLong();

    // status/devices 推送定时器(5 秒间隔)
    // 不是 final,因为 Lombok @RequiredArgsConstructor 会要求构造器注入
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "iot-console-sse-pusher");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void startPusher() {
        // 启动后 1 秒开始,之后每 5 秒推一次 status + devices
        scheduler.scheduleAtFixedRate(this::pushStatusAndDevices, 1, 5, TimeUnit.SECONDS);
        log.info("[iot-console] SSE 推送定时器启动 (status/devices 每 5 秒)");
    }

    @PreDestroy
    public void stopPusher() {
        scheduler.shutdownNow();
    }

    /** 推送 status + devices 给所有 SSE 客户端 */
    private void pushStatusAndDevices() {
        if (emitters.isEmpty()) return;
        Map<String, Object> statusMap = buildStatus();
        List<Map<String, Object>> devs = buildDevices();
        // 清理已断开的连接 + 推数据
        emitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("status").data(statusMap));
                emitter.send(SseEmitter.event().name("devices").data(devs));
                return false;
            } catch (Exception e) {
                return true; // 移除
            }
        });
    }

    private Map<String, Object> buildStatus() {
        return Map.of(
            "onlineDevices", dispatcher.onlineSessions().size(),
            "txTotal", metrics.getTxTotal(),
            "rxTotal", metrics.getRxTotal(),
            "errTotal", metrics.getErrTotal(),
            "txTps", metrics.getTxSinceTick(),
            "rxTps", metrics.getRxSinceTick(),
            "spyBufferSize", spyBuffer.size(),
            "ts", System.currentTimeMillis()
        );
    }

    private List<Map<String, Object>> buildDevices() {
        List<Map<String, Object>> out = new ArrayList<>();
        var sessions = dispatcher.onlineSessions();
        for (var session : sessions.values()) {
            out.add(Map.of(
                "deviceKey", session.getDeviceKey(),
                "productKey", session.getProductKey(),
                "protocol", session.getProtocol(),
                "remoteAddress", session.getRemoteAddress() == null ? "" : session.getRemoteAddress(),
                "connectTime", session.getConnectTime() == null ? 0 : session.getConnectTime().toEpochMilli(),
                "lastActiveTime", session.getLastActiveTime() == null ? 0 : session.getLastActiveTime().toEpochMilli()
            ));
        }
        return out;
    }

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

        // 连接后立即推: status + devices + 最近 50 条历史消息
        try {
            emitter.send(SseEmitter.event().name("status").data(buildStatus()));
            emitter.send(SseEmitter.event().name("devices").data(buildDevices()));
            for (IotMessageEnvelope env : spyBuffer.snapshot(50)) {
                emitter.send(SseEmitter.event().name("msg").data(env));
            }
            emitter.send(SseEmitter.event().name("ping").data("{}"));
        } catch (IOException e) {
            removeEmitter(emitter);
        }

        // 订阅新消息(msg 事件走事件驱动,不走定时器)
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