package com.company.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.time.Instant;

/**
 * Fallback controller to handle common unmapped paths gracefully
 * Prevents redirect loops and provides helpful responses for misguided requests
 */
@RestController
public class FallbackController {

    private static final Logger logger = LoggerFactory.getLogger(FallbackController.class);

    /**
     * Handle requests to /login - a common mistake when users expect a login page
     * Instead of causing redirect loops, return helpful information
     */
    @RequestMapping("/login")
    public ResponseEntity<?> handleLoginPath(HttpServletRequest request) {
        logger.info("Request to /login path detected from IP: {}", 
            request.getRemoteAddr());
        
        Map<String, Object> response = Map.of(
            "error", "Endpoint Not Available",
            "message", "This is a REST API backend. Login is available at POST /api/auth/login",
            "frontend_url", "http://localhost:4200/login",
            "api_endpoint", "POST /api/auth/login", 
            "timestamp", Instant.now().toString(),
            "path", request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle requests to /register - another common mistake
     */
    @RequestMapping("/register") 
    public ResponseEntity<?> handleRegisterPath(HttpServletRequest request) {
        logger.info("Request to /register path detected from IP: {}", 
            request.getRemoteAddr());
        
        Map<String, Object> response = Map.of(
            "error", "Endpoint Not Available",
            "message", "This is a REST API backend. Registration is available at POST /api/auth/register", 
            "frontend_url", "http://localhost:4200/register",
            "api_endpoint", "POST /api/auth/register",
            "timestamp", Instant.now().toString(), 
            "path", request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle requests to root path - RENDER HEALTH CHECK
     * Simple OK response for Render.com port detection
     */
    @RequestMapping("/")
    public ResponseEntity<?> handleRootPath(HttpServletRequest request) {
        logger.info("🎯 RENDER: Root path request from IP: {} - USER-AGENT: {}", 
            request.getRemoteAddr(), request.getHeader("User-Agent"));
        
        // Check if this looks like a Render health check
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && (userAgent.contains("curl") || userAgent.contains("wget") || userAgent.contains("health"))) {
            logger.info("🎯 RENDER: Health check request detected! Returning simple OK response.");
            return ResponseEntity.ok("OK");
        }
        
        // For other requests, return API info
        Map<String, Object> response = Map.of(
            "service", "ARIA User Management API",
            "status", "HEALTHY",
            "version", "1.0.0",
            "message", "REST API backend for ARIA recruitment platform",
            "api_base", "/api/auth",
            "health_check", "/api/auth/actuator/health",
            "timestamp", Instant.now().toString()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Handle favicon requests to prevent 404 errors in logs
     */
    @RequestMapping("/favicon.ico")
    public ResponseEntity<?> handleFavicon() {
        // Return empty response with appropriate status
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
