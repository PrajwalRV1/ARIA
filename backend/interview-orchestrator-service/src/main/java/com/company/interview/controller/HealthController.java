package com.company.interview.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple health check controller for debugging Render deployment
 */
@RestController
@RequestMapping("/api/interview")
public class HealthController {

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @GetMapping("/health-check")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Interview Orchestrator Service");
        response.put("port", serverPort);
        response.put("profile", activeProfile);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Interview Orchestrator Service is running");
        response.put("status", "ACTIVE");
        response.put("port", serverPort);
        response.put("endpoints", Map.of(
            "health", "/api/interview/actuator/health",
            "health-check", "/api/interview/health-check"
        ));
        
        return ResponseEntity.ok(response);
    }
}
