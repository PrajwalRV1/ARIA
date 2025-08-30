package com.company.user.config;

import com.company.user.security.RateLimitingFilter;
import com.company.user.security.EnhancedJwtUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * ✅ SECURITY: Configuration for rate limiting filter registration
 * Ensures the rate limiting filter is applied to all endpoints with proper ordering
 */
@Configuration
public class RateLimitFilterConfig {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitConfig rateLimitConfig;
    private final EnhancedJwtUtil jwtUtil;
    
    public RateLimitFilterConfig(@Qualifier("rateLimitRedisTemplate") RedisTemplate<String, String> redisTemplate,
                                RateLimitConfig rateLimitConfig,
                                EnhancedJwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.rateLimitConfig = rateLimitConfig;
        this.jwtUtil = jwtUtil;
    }
    
    /**
     * Register rate limiting filter with proper ordering
     */
    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration() {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>();
        
        // Create and register the rate limiting filter
        RateLimitingFilter rateLimitingFilter = new RateLimitingFilter(redisTemplate, rateLimitConfig, jwtUtil);
        registration.setFilter(rateLimitingFilter);
        
        // Apply to all endpoints
        registration.addUrlPatterns("/*");
        
        // Set high priority (before authentication and authorization filters)
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        
        // Set name for debugging
        registration.setName("rateLimitingFilter");
        
        return registration;
    }
}
