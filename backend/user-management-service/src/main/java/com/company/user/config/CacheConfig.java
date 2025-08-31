package com.company.user.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache Configuration for User Management Service
 * Optimizes performance by caching frequently accessed data
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.redis.url")
public class CacheConfig {

    /**
     * Configure Redis Cache Manager with optimized settings
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // Default TTL: 10 minutes
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues(); // Don't cache null values

        // Cache-specific configurations with different TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // User data cache - 30 minutes (less frequently changed)
        cacheConfigurations.put("users", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Candidate lists cache - 5 minutes (frequently changed)
        cacheConfigurations.put("candidateList", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Individual candidate cache - 15 minutes
        cacheConfigurations.put("candidates", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Auth-related cache - 1 hour (token validation, etc.)
        cacheConfigurations.put("auth", defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        
        // OTP cache - 5 minutes (short lived)
        cacheConfigurations.put("otp", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
