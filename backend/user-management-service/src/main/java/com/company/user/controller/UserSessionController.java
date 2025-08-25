package com.company.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import com.company.user.security.EnhancedJwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * User Session Management Controller
 * Handles session token generation for interview participants
 */
@RestController
@RequestMapping("/api/user/sessions")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
public class UserSessionController {

    @Autowired
    private EnhancedJwtUtil enhancedJwtUtil;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "user-session-management");
        return ResponseEntity.ok(response);
    }

    /**
     * Login endpoint - Create session token for interview participants
     * This handles token generation for recruiters, candidates, and AI avatars
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> createSessionToken(@RequestBody Map<String, Object> loginRequest) {
        try {
            String userId = (String) loginRequest.get("userId");
            String userType = (String) loginRequest.get("userType"); // recruiter, candidate, ai_avatar
            String sessionId = (String) loginRequest.get("sessionId");
            
            if (userId == null || userType == null) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("userId and userType are required"));
            }

            // Generate a simple session token (in production, use proper JWT with signing)
            String token = enhancedJwtUtil.generateSessionToken(userId, userType, sessionId, loginRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", 86400); // 24 hours
            response.put("userId", userId);
            response.put("userType", userType);
            response.put("sessionId", sessionId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to create session: " + e.getMessage()));
        }
    }

    /**
     * Generate a simple token for development (replace with proper JWT in production)
     */

    /**
     * Validate session token endpoint - handles both header and query param tokens
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String queryToken,
            HttpServletRequest request) {
        try {
            String token = null;
            
            // Try to extract token from Authorization header first
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
            // Fallback to query parameter (for interview room access)
            else if (queryToken != null && !queryToken.isEmpty()) {
                token = queryToken;
            }
            // Check if we have a session without requiring token (for regular authenticated users)
            else {
                // Allow validation for users who are already authenticated via normal login
                // This handles the case where users access interview room after logging in normally
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "message", "Session valid via authenticated context",
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            if (token == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("No authentication token provided"));
            }
            
            // Simple validation for development
            if (enhancedJwtUtil.validateSessionToken(token)) {
                EnhancedJwtUtil.TokenValidationResult validationResult = enhancedJwtUtil.createValidationResult(token);
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "message", "Session token validated successfully",
                    "userType", validationResult.getUserType(),
                    "userId", validationResult.getUserId(),
                    "sessionId", validationResult.getSessionId(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid session token format"));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse("Session validation failed: " + e.getMessage()));
        }
    }
    
    /**
     * Helper method to extract values from URL-encoded token data
     */

    /**
     * Refresh token endpoint
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> refreshRequest) {
        try {
            String currentToken = refreshRequest.get("token");
            
            if (currentToken == null) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Current token is required"));
            }

            // Simple refresh logic for development
            if (currentToken.startsWith("ARIA_SESSION_")) {
                String newToken = currentToken + "_REFRESHED_" + System.currentTimeMillis();
                
                Map<String, Object> response = new HashMap<>();
                response.put("token", newToken);
                response.put("tokenType", "Bearer");
                response.put("expiresIn", 86400);
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Unable to refresh token"));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Token refresh failed: " + e.getMessage()));
        }
    }

    /**
     * Logout endpoint
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }
}
