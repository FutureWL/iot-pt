package com.iot.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventPublisher {

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public void publishShadowUpdate(Long tenantId, Long deviceId, String deviceKey,
                                    String identifier, Object value, String updatedAt) {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("type", "shadow.update");
        evt.put("deviceId", deviceId);
        evt.put("deviceKey", deviceKey);
        evt.put("identifier", identifier);
        evt.put("value", value);
        evt.put("updatedAt", updatedAt);
        broadcast(tenantId, evt);
    }

    public void publishDeviceStatus(Long tenantId, Long deviceId, String deviceKey, int status) {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("type", "device.status");
        evt.put("deviceId", deviceId);
        evt.put("deviceKey", deviceKey);
        evt.put("status", status);
        broadcast(tenantId, evt);
    }

    private void broadcast(Long tenantId, Map<String, Object> evt) {
        Set<WebSocketSession> sessions = registry.getSessions(tenantId);
        if (sessions.isEmpty()) return;
        String payload;
        try { payload = objectMapper.writeValueAsString(evt); }
        catch (Exception e) { log.warn("[WS] 序列化失败: {}", e.getMessage()); return; }
        TextMessage msg = new TextMessage(payload);
        int sent = 0;
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) continue;
            try { s.sendMessage(msg); sent++; } catch (Exception e) { /* skip */ }
        }
        if (sent > 0) {
            log.debug("[WS] 广播 {} 给 {} 个会话", evt.get("type"), sent);
        }
    }
}