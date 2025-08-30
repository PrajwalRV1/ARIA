package com.company.user.security;

import com.company.user.config.RateLimitConfig;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * ✅ SECURITY: Rate limiting filter to prevent DoS attacks
 * Uses Redis-based sliding window algorithm for distributed rate limiting
 */
@Slf4j
public class RateLimitingFilter implements Filter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitConfig rateLimitConfig;
    private final EnhancedJwtUtil jwtUtil;
    
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final String BLOCK_PREFIX = "blocked:";
    
    public RateLimitingFilter(@Qualifier("rateLimitRedisTemplate") RedisTemplate<String, String> redisTemplate, 
                             RateLimitConfig rateLimitConfig,
                             EnhancedJwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.rateLimitConfig = rateLimitConfig;
        this.jwtUtil = jwtUtil;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Extract client identifier and context
        String clientId = extractClientId(httpRequest);
        String endpoint = httpRequest.getRequestURI();
        String userRole = extractUserRole(httpRequest);
        
        // Check if client is currently blocked
        if (isClientBlocked(clientId)) {
            log.warn("Blocked client attempted request: {} endpoint: {}", clientId, endpoint);
            sendRateLimitResponse(httpResponse, "Client is temporarily blocked due to rate limit violations");
            return;
        }
        
        // Check rate limit
        if (!isWithinRateLimit(clientId, endpoint, userRole)) {
            log.warn("Rate limit exceeded for client: {} endpoint: {} role: {}", clientId, endpoint, userRole);
            
            // Block client for repeated violations
            blockClient(clientId);
            
            sendRateLimitResponse(httpResponse, "Rate limit exceeded. Try again later.");
            return;
        }
        
        // Add rate limit headers for client information
        addRateLimitHeaders(httpResponse, clientId, endpoint, userRole);
        
        chain.doFilter(request, response);
    }
    
    /**
     * Extract client identifier with priority: JWT subject > IP address
     */
    private String extractClientId(HttpServletRequest request) {
        // Priority 1: Try to extract user ID from JWT token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String userId = extractUserIdFromToken(authHeader.substring(7));
                if (userId != null) {
                    return "user:" + userId;
                }
            } catch (Exception e) {
                log.debug("Could not extract user from token: {}", e.getMessage());
            }
        }
        
        // Priority 2: Fallback to IP address
        return "ip:" + getClientIpAddress(request);
    }
    
    /**
     * Extract user role from JWT token for role-based rate limiting
     */
    private String extractUserRole(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                return extractRoleFromToken(authHeader.substring(7));
            } catch (Exception e) {
                log.debug("Could not extract role from token: {}", e.getMessage());
            }
        }
        return null; // Anonymous user
    }
    
    /**
     * Check if client is within rate limit using sliding window algorithm
     */
    private boolean isWithinRateLimit(String clientId, String endpoint, String userRole) {
        // Determine rate limit based on endpoint and user role
        int endpointLimit = rateLimitConfig.getRateLimitForEndpoint(endpoint);
        int roleLimit = rateLimitConfig.getRateLimitForRole(userRole);
        int effectiveLimit = Math.min(endpointLimit, roleLimit);
        
        String key = RATE_LIMIT_PREFIX + clientId + ":" + normalizeEndpoint(endpoint);
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (rateLimitConfig.getWindowSizeMinutes() * 60 * 1000);
        
        try {
            // Use Redis sorted set for sliding window implementation
            // Remove old entries outside the time window
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
            
            // Count current requests in the window
            Long currentCount = redisTemplate.opsForZSet().count(key, windowStart, currentTime);
            
            if (currentCount < effectiveLimit) {
                // Add current request timestamp
                redisTemplate.opsForZSet().add(key, String.valueOf(currentTime), currentTime);
                
                // Set expiration for cleanup (window size + buffer)
                redisTemplate.expire(key, Duration.ofMinutes(rateLimitConfig.getWindowSizeMinutes() + 1));
                
                log.debug("Rate limit check passed for {}: {}/{}", clientId, currentCount + 1, effectiveLimit);
                return true;
            }
            
            log.debug("Rate limit exceeded for {}: {}/{}", clientId, currentCount, effectiveLimit);
            return false;
            
        } catch (Exception e) {
            log.error("Rate limiting check failed for client {}: {}", clientId, e.getMessage());
            // Fail open - allow request if Redis is unavailable
            return true;
        }
    }
    
    /**
     * Check if client is currently blocked
     */
    private boolean isClientBlocked(String clientId) {
        try {
            String blockKey = BLOCK_PREFIX + clientId;
            return redisTemplate.hasKey(blockKey);
        } catch (Exception e) {
            log.error("Block check failed for client {}: {}", clientId, e.getMessage());
            return false; // Fail open
        }
    }
    
    /**
     * Block client for repeated rate limit violations
     */
    private void blockClient(String clientId) {
        try {
            String blockKey = BLOCK_PREFIX + clientId;
            redisTemplate.opsForValue().set(blockKey, String.valueOf(System.currentTimeMillis()));
            redisTemplate.expire(blockKey, Duration.ofMinutes(rateLimitConfig.getBlockDurationMinutes()));
            
            log.warn("Blocked client {} for {} minutes due to rate limit violations", 
                    clientId, rateLimitConfig.getBlockDurationMinutes());
        } catch (Exception e) {
            log.error("Failed to block client {}: {}", clientId, e.getMessage());
        }
    }
    
    /**
     * Add rate limit information headers to response
     */
    private void addRateLimitHeaders(HttpServletResponse response, String clientId, String endpoint, String userRole) {
        try {
            int endpointLimit = rateLimitConfig.getRateLimitForEndpoint(endpoint);
            int roleLimit = rateLimitConfig.getRateLimitForRole(userRole);
            int effectiveLimit = Math.min(endpointLimit, roleLimit);
            
            String key = RATE_LIMIT_PREFIX + clientId + ":" + normalizeEndpoint(endpoint);
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - (rateLimitConfig.getWindowSizeMinutes() * 60 * 1000);
            
            Long currentCount = redisTemplate.opsForZSet().count(key, windowStart, currentTime);
            int remaining = Math.max(0, effectiveLimit - currentCount.intValue());
            
            // Add standard rate limit headers
            response.setHeader("X-RateLimit-Limit", String.valueOf(effectiveLimit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(windowStart + (rateLimitConfig.getWindowSizeMinutes() * 60 * 1000)));
            
        } catch (Exception e) {
            log.debug("Failed to add rate limit headers: {}", e.getMessage());
        }
    }
    
    /**
     * Send rate limit exceeded response
     */
    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String errorResponse = String.format("""
            {
                "timestamp": "%s",
                "status": 429,
                "error": "Too Many Requests",
                "message": "%s",
                "path": "rate-limited"
            }
            """, Instant.now().toString(), message);
        
        response.getWriter().write(errorResponse);
    }
    
    /**
     * Extract client IP address considering proxies
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Check for X-Forwarded-For header (load balancers, proxies)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check for X-Real-IP header (Nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        
        // Fallback to remote address
        return request.getRemoteAddr();
    }
    
    /**
     * Normalize endpoint for consistent rate limiting
     * Removes path variables and query parameters
     */
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null) return "unknown";
        
        // Remove query parameters
        int queryIndex = endpoint.indexOf('?');
        if (queryIndex != -1) {
            endpoint = endpoint.substring(0, queryIndex);
        }
        
        // Normalize path variables (replace with placeholder)
        endpoint = endpoint.replaceAll("/\\d+", "/{id}")
                          .replaceAll("/[a-f0-9-]{36}", "/{uuid}")
                          .replaceAll("/[a-zA-Z0-9_-]{10,}", "/{param}");
        
        return endpoint;
    }
    
    /**
     * Extract user ID from JWT token using EnhancedJwtUtil
     */
    private String extractUserIdFromToken(String token) {
        try {
            Long userId = jwtUtil.extractUserId(token);
            return userId != null ? userId.toString() : null;
        } catch (Exception e) {
            log.debug("Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract user role from JWT token using EnhancedJwtUtil
     * Maps user type to role for rate limiting purposes
     */
    private String extractRoleFromToken(String token) {
        try {
            String userType = jwtUtil.extractUserType(token);
            // Map user types to roles for rate limiting
            if (userType != null) {
                switch (userType.toUpperCase()) {
                    case "RECRUITER":
                    case "ADMIN":
                        return "RECRUITER";
                    case "CANDIDATE":
                        return "CANDIDATE";
                    case "AI_AVATAR":
                        return "AI_AVATAR";
                    default:
                        return userType;
                }
            }
            return null; // Anonymous user
        } catch (Exception e) {
            log.debug("Failed to extract role from token: {}", e.getMessage());
            return null;
        }
    }
}
