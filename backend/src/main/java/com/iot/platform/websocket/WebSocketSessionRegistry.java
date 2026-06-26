package com.iot.platform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket session 注册表(Spring WebSocket)
 */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long tenantId, WebSocketSession session) {
        sessions.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(WebSocketSession session) {
        for (Set<WebSocketSession> set : sessions.values()) {
            set.remove(session);
        }
    }

    public Set<WebSocketSession> getSessions(Long tenantId) {
        return sessions.getOrDefault(tenantId, java.util.Collections.emptySet());
    }

    public int totalSessions() {
        return sessions.values().stream().mapToInt(Set::size).sum();
    }
}