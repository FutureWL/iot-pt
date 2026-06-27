package com.iot.platform.iotconsole;

import com.iot.platform.protocol.core.IotMessageEnvelope;
import com.iot.platform.protocol.core.ProtocolAdapter;
import com.iot.platform.protocol.core.ProtocolDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IoT 控制台 REST 端点
 *
 * <p>提供:
 *   - GET  /api/iot-console/status        概览(在线数/消息数/TPS)
 *   - GET  /api/iot-console/devices       设备列表
 *   - GET  /api/iot-console/messages      最近 N 条(默认 100,最多 1000)
 *   - POST /api/iot-console/devices/{key}/kick  踢设备下线(断开 TCP/MQTT)
 *   - POST /api/iot-console/protocols/{name}/restart  重启某个协议适配器
 * </p>
 *
 * <p>激活条件: iot.console.enabled=true(默认)
 * 鉴权交给 SecurityConfig(同 API)。</p>
 */
@Slf4j
@RestController
@RequestMapping("/iot-console")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "iot.console", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IotConsoleController {

    private final ProtocolDispatcher dispatcher;
    private final IotMetricsService metrics;
    private final SpyBuffer spyBuffer;

    @GetMapping("/status")
    public Map<String, Object> status() {
        var online = dispatcher.onlineSessions();
        return Map.of(
            "onlineDevices", online.size(),
            "txTotal", metrics.getTxTotal(),
            "rxTotal", metrics.getRxTotal(),
            "errTotal", metrics.getErrTotal(),
            "txTps", metrics.getTxSinceTick(),
            "rxTps", metrics.getRxSinceTick(),
            "spyBufferSize", spyBuffer.size(),
            "ts", System.currentTimeMillis()
        );
    }

    @GetMapping("/devices")
    public List<Map<String, Object>> devices() {
        List<Map<String, Object>> out = new ArrayList<>();
        var sessions = dispatcher.onlineSessions();
        for (var session : sessions.values()) {
            out.add(Map.of(
                "deviceKey", session.getDeviceKey(),
                "productKey", session.getProductKey(),
                "protocol", session.getProtocol(),
                "remoteAddress", nullSafe(session.getRemoteAddress()),
                "connectTime", session.getConnectTime() == null ? 0 : session.getConnectTime().toEpochMilli(),
                "lastActiveTime", session.getLastActiveTime() == null ? 0 : session.getLastActiveTime().toEpochMilli()
            ));
        }
        return out;
    }

    @GetMapping("/messages")
    public List<IotMessageEnvelope> messages(
            @RequestParam(defaultValue = "100") int limit) {
        return spyBuffer.snapshot(Math.min(limit, 1000));
    }

    @PostMapping("/devices/{key}/kick")
    public Map<String, Object> kick(@PathVariable String key) {
        var session = dispatcher.onlineSessions().get(key);
        if (session == null) {
            return Map.of("ok", false, "msg", "设备不在线或无 session");
        }
        try {
            // 通过 dispatcher 找协议适配器 → 调 sendDownMessage 关闭连接
            var protocol = dispatcher.get(session.getProtocol());
            if (protocol != null) {
                // 注: 这里只是标记意图,实际关连接在适配器内部
                dispatcher.onSessionDisconnect(session);
            }
            return Map.of("ok", true, "msg", "已请求断开 " + key);
        } catch (Exception e) {
            log.error("[iot-console] 踢设备失败: {}", key, e);
            return Map.of("ok", false, "msg", e.getMessage());
        }
    }

    @PostMapping("/protocols/{name}/restart")
    public Map<String, Object> restartProtocol(@PathVariable String name) {
        try {
            var adapter = dispatcher.get(name);
            if (adapter == null) {
                return Map.of("ok", false, "msg", "协议不存在: " + name);
            }
            // 起新线程避免阻塞
            new Thread(() -> {
                try { adapter.stop(); } catch (Exception ignored) {}
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                try { adapter.start(); } catch (Exception ignored) {}
            }, "iot-console-restart-" + name).start();
            return Map.of("ok", true, "msg", "重启中: " + name);
        } catch (Exception e) {
            return Map.of("ok", false, "msg", e.getMessage());
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
