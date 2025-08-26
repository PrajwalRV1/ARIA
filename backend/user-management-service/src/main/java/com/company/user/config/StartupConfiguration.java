package com.company.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Startup configuration to ensure proper port binding for Render deployment
 */
@Configuration
@Profile("render")
public class StartupConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StartupConfiguration.class);

    @Value("${server.port}")
    private String serverPort;

    @Value("${server.address}")
    private String serverAddress;

    @Bean
    public ApplicationRunner logStartupInfo() {
        return args -> {
            logger.info("=================================================");
            logger.info("🚀 USER MANAGEMENT SERVICE STARTING UP");
            logger.info("=================================================");
            logger.info("Server Address: {}", serverAddress);
            logger.info("Server Port: {}", serverPort);
            logger.info("Context Path: /api/auth");
            logger.info("Profile: render");
            logger.info("Expected Health Check: http://{}:{}/api/auth/actuator/health", serverAddress, serverPort);
            logger.info("=================================================");
            
            // Explicitly log environment variables that Render provides
            String renderPort = System.getenv("PORT");
            if (renderPort != null) {
                logger.info("🔧 Render PORT environment variable: {}", renderPort);
            } else {
                logger.warn("⚠️ Render PORT environment variable not found, using default: {}", serverPort);
            }
            
            // Log system properties for debugging
            logger.info("System Property server.port: {}", System.getProperty("server.port"));
            logger.info("Environment PORT: {}", System.getenv("PORT"));
        };
    }
}
