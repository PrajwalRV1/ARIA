package com.company.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;

/**
 * Health controller for Render.com port detection
 * Provides multiple endpoints for Render to detect the service
 */
@RestController
@Profile("render")
public class RenderHealthController {

    /**
     * Primary health check endpoint for Render
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        System.out.println("🔍 DEBUG: /health endpoint accessed! Render should detect this.");
        return ResponseEntity.ok("OK");
    }

    /**
     * Root endpoint for basic connectivity testing
     */
    @GetMapping("/")
    public ResponseEntity<String> root() {
        System.out.println("🔍 DEBUG: Root / endpoint accessed!");
        return ResponseEntity.ok("ARIA User Management Service - READY");
    }
    
    /**
     * Alternative health endpoint
     */
    @GetMapping("/healthz")
    public ResponseEntity<String> healthz() {
        System.out.println("🔍 DEBUG: /healthz endpoint accessed!");
        return ResponseEntity.ok("HEALTHY");
    }
    
    /**
     * Status endpoint
     */
    @GetMapping("/status")
    public ResponseEntity<String> status() {
        System.out.println("🔍 DEBUG: /status endpoint accessed!");
        return ResponseEntity.ok("UP");
    }
}
