package com.company.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * ✅ SECURITY: Rate limiting configuration to prevent DoS attacks
 * Uses Redis-based sliding window algorithm for distributed rate limiting
 */
@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
@Data
public class RateLimitConfig {
    
    // Rate limiting parameters
    private int requestsPerMinute = 100;          // Max requests per minute per user
    private int burstCapacity = 150;              // Burst capacity for temporary spikes
    private int replenishRate = 2;                // Rate at which tokens are replenished per second
    private long windowSizeMinutes = 1;           // Sliding window size in minutes
    
    // Different limits for different user types
    private int adminRequestsPerMinute = 200;     // Higher limit for admins
    private int anonymousRequestsPerMinute = 20;  // Lower limit for anonymous users
    
    // Rate limiting by endpoint type
    private int authRequestsPerMinute = 10;       // Stricter for auth endpoints
    private int uploadRequestsPerMinute = 5;      // Very strict for file uploads
    private int searchRequestsPerMinute = 50;     // Moderate for search operations
    
    // Time windows
    private long blockDurationMinutes = 5;        // How long to block after rate limit exceeded
    
    /**
     * Redis template specifically for rate limiting operations
     * Separate from main Redis template for better isolation
     */
    @Bean(name = "rateLimitRedisTemplate")
    public RedisTemplate<String, String> rateLimitRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializers for simplicity and performance
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        
        // Disable transactions for better performance
        template.setEnableTransactionSupport(false);
        
        template.afterPropertiesSet();
        return template;
    }
    
    /**
     * Get rate limit for specific endpoint pattern
     */
    public int getRateLimitForEndpoint(String endpoint) {
        if (endpoint == null) {
            return requestsPerMinute;
        }
        
        // More restrictive limits for sensitive endpoints
        if (endpoint.contains("/login") || endpoint.contains("/register") || 
            endpoint.contains("/reset-password") || endpoint.contains("/verify-otp")) {
            return authRequestsPerMinute;
        }
        
        if (endpoint.contains("/upload") || endpoint.contains("multipart")) {
            return uploadRequestsPerMinute;
        }
        
        if (endpoint.contains("/search") || endpoint.contains("/by-")) {
            return searchRequestsPerMinute;
        }
        
        return requestsPerMinute;
    }
    
    /**
     * Get rate limit based on user role
     */
    public int getRateLimitForRole(String role) {
        if (role == null) {
            return anonymousRequestsPerMinute;
        }
        
        return switch (role.toUpperCase()) {
            case "ADMIN" -> adminRequestsPerMinute;
            case "RECRUITER" -> requestsPerMinute;
            case "CANDIDATE" -> requestsPerMinute / 2;  // Lower limit for candidates
            default -> anonymousRequestsPerMinute;
        };
    }
}
