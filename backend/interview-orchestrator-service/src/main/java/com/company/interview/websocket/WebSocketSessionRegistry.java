package com.company.interview.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final Map<String, List<WebSocketSession>> orchestratorSessions = new ConcurrentHashMap<>();
    private final Map<String, List<WebSocketSession>> analyticsSessions = new ConcurrentHashMap<>();

    public void addOrchestratorSession(String sessionId, WebSocketSession session) {
        orchestratorSessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(session);
    }

    public void removeOrchestratorSession(String sessionId, WebSocketSession session) {
        orchestratorSessions.computeIfPresent(sessionId, (k, list) -> { list.remove(session); return list; });
    }

    public List<WebSocketSession> getOrchestratorSessions(String sessionId) {
        return orchestratorSessions.getOrDefault(sessionId, List.of());
    }

    public void addAnalyticsSession(String sessionId, WebSocketSession session) {
        analyticsSessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(session);
    }

    public void removeAnalyticsSession(String sessionId, WebSocketSession session) {
        analyticsSessions.computeIfPresent(sessionId, (k, list) -> { list.remove(session); return list; });
    }

    public List<WebSocketSession> getAnalyticsSessions(String sessionId) {
        return analyticsSessions.getOrDefault(sessionId, List.of());
    }
}
