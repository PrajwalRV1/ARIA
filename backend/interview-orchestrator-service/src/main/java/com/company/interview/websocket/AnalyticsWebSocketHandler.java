package com.company.interview.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsWebSocketHandler extends TextWebSocketHandler {
    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session.getUri());
        registry.addAnalyticsSession(sessionId, session);
        log.info("Analytics WS connected: {}", sessionId);
        session.sendMessage(new TextMessage("{\"type\":\"analytics_status\",\"status\":\"ready\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("Analytics WS message: {}", message.getPayload());
        // Expect frontend signals: {type: 'focus_change'|'visibility_change'|'suspicious', ...}
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session.getUri());
        registry.removeAnalyticsSession(sessionId, session);
        log.info("Analytics WS disconnected: {}", sessionId);
    }

    private String extractSessionId(URI uri) {
        String path = uri != null ? uri.getPath() : "";
        String[] parts = path.split("/");
        return parts.length > 3 ? parts[3] : "unknown";
    }

    public void broadcast(String sessionId, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            for (WebSocketSession s : registry.getAnalyticsSessions(sessionId)) {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.error("Failed to broadcast analytics message: {}", e.getMessage(), e);
        }
    }
}
