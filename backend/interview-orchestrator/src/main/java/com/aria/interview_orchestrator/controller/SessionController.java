package com.aria.interview_orchestrator.controller;

import com.aria.interview_orchestrator.service.SessionManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Session Management Controller for handling authentication and session lifecycle
 */
@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionController {

    @Autowired
    private SessionManagerService sessionManagerService;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", System.currentTimeMillis());
        response.put("activeSessions", sessionManagerService.getActiveSessionCount());
        return ResponseEntity.ok(response);
    }

    /**
     * Login endpoint - Create new session token
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> loginRequest) {
        try {
            String userId = (String) loginRequest.get("userId");
            String userType = (String) loginRequest.get("userType"); // recruiter, candidate, ai_avatar
            String sessionId = (String) loginRequest.get("sessionId");
            
            if (userId == null || userType == null) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("userId and userType are required"));
            }

            // Generate session token
            Map<String, Object> additionalClaims = new HashMap<>();
            if (loginRequest.containsKey("candidateName")) {
                additionalClaims.put("candidateName", loginRequest.get("candidateName"));
            }
            if (loginRequest.containsKey("recruiterName")) {
                additionalClaims.put("recruiterName", loginRequest.get("recruiterName"));
            }
            if (loginRequest.containsKey("position")) {
                additionalClaims.put("position", loginRequest.get("position"));
            }

            String token = sessionManagerService.generateSessionToken(sessionId, userId, userType, additionalClaims);

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
     * Validate session token
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateSession(HttpServletRequest request) {
        try {
            String sessionId = (String) request.getAttribute("sessionId");
            String userId = (String) request.getAttribute("userId");
            String userType = (String) request.getAttribute("userType");
            String token = (String) request.getAttribute("sessionToken");

            SessionManagerService.SessionInfo sessionInfo = sessionManagerService.getSessionInfo(token);
            
            if (sessionInfo == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid session"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("userId", userId);
            response.put("userType", userType);
            response.put("sessionId", sessionId);
            response.put("expiresAt", sessionInfo.getExpiresAt().toString());
            response.put("lastAccessed", sessionInfo.getLastAccessed().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse("Session validation failed"));
        }
    }

    /**
     * Refresh session token
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> refreshRequest) {
        try {
            String currentToken = refreshRequest.get("token");
            
            if (currentToken == null) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Current token is required"));
            }

            String newToken = sessionManagerService.refreshToken(currentToken);
            
            if (newToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Unable to refresh token"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("token", newToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", 86400); // 24 hours
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Token refresh failed: " + e.getMessage()));
        }
    }

    /**
     * Logout endpoint - Invalidate session token
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        try {
            String token = (String) request.getAttribute("sessionToken");
            
            if (token != null) {
                sessionManagerService.invalidateToken(token);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Logged out successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Logout failed: " + e.getMessage()));
        }
    }

    /**
     * Logout all sessions for a user
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, Object>> logoutAll(HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("userId");
            
            if (userId != null) {
                sessionManagerService.invalidateUserSessions(userId);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "All sessions logged out successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Logout all failed: " + e.getMessage()));
        }
    }

    /**
     * Get active sessions (admin endpoint)
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveSessions(HttpServletRequest request) {
        try {
            String userType = (String) request.getAttribute("userType");
            
            // Only allow recruiters to view active sessions
            if (!"recruiter".equals(userType)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(createErrorResponse("Access denied"));
            }

            Map<String, SessionManagerService.SessionInfo> activeSessions = sessionManagerService.getActiveSessions();
            
            Map<String, Object> response = new HashMap<>();
            response.put("activeSessions", activeSessions.size());
            response.put("sessions", activeSessions.values().stream()
                .map(session -> Map.of(
                    "sessionId", session.getSessionId(),
                    "userId", session.getUserId(),
                    "userType", session.getUserType(),
                    "expiresAt", session.getExpiresAt().toString(),
                    "lastAccessed", session.getLastAccessed().toString()
                )).toList());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to get active sessions: " + e.getMessage()));
        }
    }

    /**
     * End interview session - Invalidate all tokens for interview
     */
    @PostMapping("/end-interview/{sessionId}")
    public ResponseEntity<Map<String, Object>> endInterviewSession(@PathVariable String sessionId,
                                                                   HttpServletRequest request) {
        try {
            String userType = (String) request.getAttribute("userType");
            
            // Only allow recruiters to end interview sessions
            if (!"recruiter".equals(userType)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(createErrorResponse("Access denied"));
            }

            sessionManagerService.invalidateInterviewSessions(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Interview session ended successfully");
            response.put("sessionId", sessionId);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to end interview session: " + e.getMessage()));
        }
    }

    /**
     * Cleanup expired sessions
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupSessions() {
        try {
            int beforeCount = sessionManagerService.getActiveSessionCount();
            sessionManagerService.cleanupExpiredSessions();
            int afterCount = sessionManagerService.getActiveSessionCount();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Cleanup completed");
            response.put("sessionsRemoved", beforeCount - afterCount);
            response.put("activeSessionsRemaining", afterCount);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Cleanup failed: " + e.getMessage()));
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }
}
