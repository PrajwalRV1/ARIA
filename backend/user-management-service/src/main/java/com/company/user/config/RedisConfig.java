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
    @Profile("neon")
    public RedisConnectionFactory upstashRedisConnectionFactory() {
        if (redisUrl == null || redisUrl.isEmpty()) {
            // Fallback to default Upstash configuration if URL is not set
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName("renewing-falcon-41265.upstash.io");
            config.setPort(6379);
            config.setPassword("AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU");
            
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.setUseSsl(true);
            factory.setVerifyPeer(false);
            
            return factory;
        }
        
        try {
            // Parse the Upstash Redis URL (rediss://default:password@host:port)
            URI redisUri = URI.create(redisUrl);
            
            // Extract connection details
            String host = redisUri.getHost();
            int port = redisUri.getPort() != -1 ? redisUri.getPort() : 6379;
            String password = null;
            
            // Extract password from URL
            String userInfo = redisUri.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                password = userInfo.split(":", 2)[1];
            }
            
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(host);
            config.setPort(port);
            if (password != null) {
                config.setPassword(password);
            }

            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.setUseSsl(redisUrl.startsWith("rediss://"));
            factory.setVerifyPeer(false); // For Upstash compatibility
            
            return factory;
        } catch (Exception e) {
            // Fallback to default configuration if URL parsing fails
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName("renewing-falcon-41265.upstash.io");
            config.setPort(6379);
            config.setPassword("AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU");
            
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.setUseSsl(true);
            factory.setVerifyPeer(false);
            
            return factory;
        }
    }

    @Bean
    @Profile("!neon")
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
