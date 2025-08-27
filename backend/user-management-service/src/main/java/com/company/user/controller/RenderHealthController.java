package com.company.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;

/**
 * Health controller for Render.com port detection
 * Maps to ROOT context to bypass /api/auth context path
 */
@RestController
@Profile("render")
public class RenderHealthController {

    /**
     * Simple health check endpoint that Render can access
     * Available at: http://host:port/health (bypasses /api/auth context)
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        System.out.println("DEBUG: Health endpoint accessed!");
        return ResponseEntity.ok("OK");
    }

    /**
     * Root endpoint for basic connectivity testing
     */
    @GetMapping("/")
    public ResponseEntity<String> root() {
        System.out.println("DEBUG: Root endpoint accessed!");
        return ResponseEntity.ok("ARIA User Management Service is running");
    }
}
