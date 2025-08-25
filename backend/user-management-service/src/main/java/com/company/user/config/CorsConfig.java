package com.company.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS Configuration - DISABLED
 * 
 * CORS is now handled in SecurityConfig.java to avoid conflicts.
 * Spring Security CORS takes precedence over WebMvc CORS,
 * so having both can cause issues.
 */
@Configuration
public class CorsConfig {
    
    // CORS configuration moved to SecurityConfig.java
    // This prevents conflicts between Spring Security CORS and WebMvc CORS
    
    /*
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:4200")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
    */
}
