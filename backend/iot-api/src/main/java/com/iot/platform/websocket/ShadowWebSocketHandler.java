package com.iot.platform.websocket;

import com.iot.platform.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;

/**
 * 影子实时推送 - Spring WebSocket Handler
 *
 * <p>URL: /ws/shadow?token=eyJhbGc...</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShadowWebSocketHandler extends TextWebSocketHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final WebSocketSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从 query string 解析 token
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("missing token"));
            return;
        }
        String token = null;
        for (String pair : uri.getQuery().split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && "token".equals(pair.substring(0, idx))) {
                token = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                break;
            }
        }
        if (token == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("missing token"));
            return;
        }
        try {
            Claims claims = jwtTokenProvider.parse(token);
            Long userId = claims.get("uid", Long.class);
            Long tenantId = claims.get("tenantId", Long.class);
            String username = claims.get("username", String.class);
            if (userId == null || tenantId == null) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
                return;
            }
            session.getAttributes().put("userId", userId);
            session.getAttributes().put("tenantId", tenantId);
            session.getAttributes().put("username", username);
            registry.register(tenantId, session);
            log.info("[WS] 连接建立: tenantId={} user={} sid={}", tenantId, username, session.getId());
            sendJson(session, Map.of(
                    "type", "hello",
                    "tenantId", tenantId,
                    "userId", userId
            ));
        } catch (Exception e) {
            log.warn("[WS] 认证失败: {}", e.getMessage());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("token invalid"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("ping".equals(message.getPayload())) {
            try { session.sendMessage(new TextMessage("{\"type\":\"pong\"}")); } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
        log.info("[WS] 连接关闭: sid={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[WS] 传输错误 sid={}: {}", session.getId(), exception.getMessage());
    }

    private void sendJson(WebSocketSession session, Map<String, ?> data) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            session.sendMessage(new TextMessage(om.writeValueAsString(data)));
        } catch (Exception e) {
            log.warn("[WS] 发送失败: {}", e.getMessage());
        }
    }
}