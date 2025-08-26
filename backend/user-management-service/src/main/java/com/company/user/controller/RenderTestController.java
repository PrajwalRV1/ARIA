package com.company.user.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Simple test controller for Render deployment debugging
 * Only active when render-minimal profile is used
 */
@RestController
@Profile("render-minimal")
public class RenderTestController {

    private static final Logger logger = LoggerFactory.getLogger(RenderTestController.class);

    @Value("${server.port}")
    private String serverPort;

    @Value("${server.address}")
    private String serverAddress;

    /**
     * Simple test endpoint to verify the service is running
     */
    @GetMapping("/test")
    public ResponseEntity<?> testEndpoint() {
        logger.info("Test endpoint called - service is running on {}:{}", serverAddress, serverPort);
        
        Map<String, Object> response = Map.of(
            "status", "OK",
            "message", "User Management Service is running (minimal mode)",
            "server", serverAddress + ":" + serverPort,
            "profile", "render-minimal",
            "timestamp", Instant.now().toString(),
            "renderPort", System.getenv("PORT") != null ? System.getenv("PORT") : "not-set"
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Debug endpoint to show environment information
     */
    @GetMapping("/debug")
    public ResponseEntity<?> debugEndpoint() {
        logger.info("Debug endpoint called");
        
        Map<String, Object> response = Map.of(
            "server_address", serverAddress,
            "server_port", serverPort,
            "render_port_env", System.getenv("PORT") != null ? System.getenv("PORT") : "not-set",
            "java_version", System.getProperty("java.version"),
            "os_name", System.getProperty("os.name"),
            "timestamp", Instant.now().toString()
        );
        
        return ResponseEntity.ok(response);
    }
}
