package com.company.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;

/**
 * Redis configuration for Spring Boot application
 * Supports both traditional Redis connections and Upstash Redis REST API
 */
@Configuration
public class RedisConfig {

    @Value("${spring.redis.url:}")
    private String redisUrl;
    
    @Value("${spring.redis.password:}")
    private String redisPassword;
    
    @Value("${spring.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Bean
    @Profile("supabase")
    public RedisConnectionFactory upstashRedisConnectionFactory() {
        try {
            // Parse the Upstash Redis URL
            URI redisUri = URI.create(redisUrl.replace("redis://", "").replace("rediss://", ""));
            
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(redisUri.getHost());
            config.setPort(redisUri.getPort() != -1 ? redisUri.getPort() : (sslEnabled ? 6380 : 6379));
            config.setPassword(redisPassword);

            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.setUseSsl(sslEnabled);
            factory.setVerifyPeer(false); // For Upstash compatibility
            
            return factory;
        } catch (Exception e) {
            // Fallback to default configuration if URL parsing fails
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName("renewing-falcon-41265.upstash.io");
            config.setPort(6380);
            config.setPassword(redisPassword);
            
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.setUseSsl(true);
            factory.setVerifyPeer(false);
            
            return factory;
        }
    }

    @Bean
    @Profile("!supabase")
    public RedisConnectionFactory localRedisConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6379);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values  
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }
}
