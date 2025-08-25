package com.aria.interview_orchestrator.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session Management Service for handling JWT tokens and session lifecycle
 * Maintains sessions for 24 hours with automatic cleanup
 */
@Service
public class SessionManagerService {

    @Value("${aria.jwt.secret:ariasecretkey12345678901234567890}")
    private String jwtSecret;

    @Value("${aria.jwt.expiration:86400}") // 24 hours in seconds
    private int jwtExpirationInSeconds;

    // In-memory session store - in production, use Redis or database
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Generate JWT token for interview session
     */
    public String generateSessionToken(String sessionId, String userId, String userType, Map<String, Object> additionalClaims) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sessionId", sessionId);
        claims.put("userId", userId);
        claims.put("userType", userType); // recruiter, candidate, ai_avatar
        claims.put("issuedAt", System.currentTimeMillis());
        
        if (additionalClaims != null) {
            claims.putAll(additionalClaims);
        }

        Date expirationDate = new Date(System.currentTimeMillis() + (jwtExpirationInSeconds * 1000L));
        
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(expirationDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();

        // Store session info
        SessionInfo sessionInfo = new SessionInfo(sessionId, userId, userType, 
                LocalDateTime.now().plusSeconds(jwtExpirationInSeconds), token);
        activeSessions.put(token, sessionInfo);
        
        return token;
    }

    /**
     * Validate JWT token and extract claims
     */
    public Claims validateToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            // Check if session exists in our store
            SessionInfo sessionInfo = activeSessions.get(token);
            if (sessionInfo == null || sessionInfo.isExpired()) {
                activeSessions.remove(token);
                return null;
            }
            
            return claims;
        } catch (Exception e) {
            // Token is invalid
            activeSessions.remove(token);
            return null;
        }
    }

    /**
     * Check if token is valid and not expired
     */
    public boolean isTokenValid(String token) {
        return validateToken(token) != null;
    }

    /**
     * Get session info from token
     */
    public SessionInfo getSessionInfo(String token) {
        SessionInfo sessionInfo = activeSessions.get(token);
        if (sessionInfo != null && !sessionInfo.isExpired()) {
            return sessionInfo;
        }
        activeSessions.remove(token);
        return null;
    }

    /**
     * Refresh token with new expiration
     */
    public String refreshToken(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return null;
        }

        // Extract existing claims
        String sessionId = claims.get("sessionId", String.class);
        String userId = claims.get("userId", String.class);
        String userType = claims.get("userType", String.class);
        
        // Remove old session
        invalidateToken(token);
        
        // Create new token with fresh expiration
        Map<String, Object> additionalClaims = new HashMap<>();
        claims.forEach((key, value) -> {
            if (!"exp".equals(key) && !"iat".equals(key)) {
                additionalClaims.put(key, value);
            }
        });
        
        return generateSessionToken(sessionId, userId, userType, additionalClaims);
    }

    /**
     * Invalidate token and remove from active sessions
     */
    public void invalidateToken(String token) {
        activeSessions.remove(token);
    }

    /**
     * Invalidate all sessions for a user
     */
    public void invalidateUserSessions(String userId) {
        activeSessions.entrySet().removeIf(entry -> 
            userId.equals(entry.getValue().getUserId())
        );
    }

    /**
     * Invalidate all sessions for an interview session
     */
    public void invalidateInterviewSessions(String sessionId) {
        activeSessions.entrySet().removeIf(entry -> 
            sessionId.equals(entry.getValue().getSessionId())
        );
    }

    /**
     * Get all active sessions for monitoring
     */
    public Map<String, SessionInfo> getActiveSessions() {
        // Clean up expired sessions before returning
        cleanupExpiredSessions();
        return new HashMap<>(activeSessions);
    }

    /**
     * Clean up expired sessions
     */
    public void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> 
            entry.getValue().isExpired()
        );
    }

    /**
     * Get session count for monitoring
     */
    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return activeSessions.size();
    }

    /**
     * Check if user has active session
     */
    public boolean hasActiveSession(String userId) {
        cleanupExpiredSessions();
        return activeSessions.values().stream()
                .anyMatch(session -> userId.equals(session.getUserId()) && !session.isExpired());
    }

    /**
     * Session Information holder class
     */
    public static class SessionInfo {
        private final String sessionId;
        private final String userId;
        private final String userType;
        private final LocalDateTime expiresAt;
        private final String token;
        private LocalDateTime lastAccessed;

        public SessionInfo(String sessionId, String userId, String userType, LocalDateTime expiresAt, String token) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.userType = userType;
            this.expiresAt = expiresAt;
            this.token = token;
            this.lastAccessed = LocalDateTime.now();
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }

        public void updateLastAccessed() {
            this.lastAccessed = LocalDateTime.now();
        }

        // Getters
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public String getUserType() { return userType; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public String getToken() { return token; }
        public LocalDateTime getLastAccessed() { return lastAccessed; }
    }
}
