package com.company.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionTrackingMode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

/**
 * Web configuration to handle resource mapping, CORS, and session management
 * Fixes session tracking and prevents URL rewriting issues
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map URL /uploads/** to the filesystem uploads directory
        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        String uploadPath = uploadDir.toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }

    /**
     * CORS Configuration - MOVED TO SecurityConfig.java
     * 
     * CORS is now handled in SecurityConfig to avoid conflicts.
     * Spring Security CORS takes precedence over WebMvc CORS.
     */
    /*
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORS configuration moved to SecurityConfig.java
        // This prevents conflicts between Spring Security CORS and WebMvc CORS
    }
    */

    /**
     * Configure servlet context to disable URL session rewriting
     * This prevents jsessionid from appearing in URLs
     */
    @Bean
    public ServletContextInitializer servletContextInitializer() {
        return new ServletContextInitializer() {
            @Override
            public void onStartup(ServletContext servletContext) {
                // Only use cookies for session tracking, disable URL rewriting
                servletContext.setSessionTrackingModes(
                    EnumSet.of(SessionTrackingMode.COOKIE)
                );
                
                // Set additional servlet context parameters for security
                servletContext.setInitParameter("httpOnlyCookies", "true");
                servletContext.setInitParameter("secureCookies", "false"); // Set to true in production with HTTPS
            }
        };
    }
}
