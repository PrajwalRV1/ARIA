package com.company.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

/**
 * Simple health controller for Render.com port detection
 * This provides a root-level endpoint that Render can access to verify the service is running
 */
@RestController
public class RenderHealthController {

    /**
     * Simple health check endpoint that Render can access
     * Available at: http://host:port/health (not under /api/auth context)
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    /**
     * Root endpoint for basic connectivity testing
     */
    @GetMapping("/")
    public ResponseEntity<String> root() {
        return ResponseEntity.ok("ARIA User Management Service is running");
    }
}
