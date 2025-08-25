package com.company.interview.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrchestratorWebSocketHandler orchestratorHandler;
    private final AnalyticsWebSocketHandler analyticsHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orchestratorHandler, "/ws/interview/{sessionId}").setAllowedOrigins("*");
        registry.addHandler(analyticsHandler, "/ws/analytics/{sessionId}").setAllowedOrigins("*");
    }
}
