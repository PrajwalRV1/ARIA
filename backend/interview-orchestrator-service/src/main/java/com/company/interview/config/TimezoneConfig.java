package com.company.interview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Configuration class to ensure consistent timezone handling across the application
 * Sets the application to use the system timezone instead of defaulting to UTC
 */
@Configuration
@Slf4j
public class TimezoneConfig {

    /**
     * Set the default timezone for the JVM to the system timezone
     * This ensures that all time operations use the local timezone
     */
    @PostConstruct
    public void configureTimezone() {
        // Get the system default timezone
        ZoneId systemZone = ZoneId.systemDefault();
        TimeZone systemTimeZone = TimeZone.getTimeZone(systemZone);
        
        log.info("🕐 Configuring application timezone:");
        log.info("🌍 System timezone: {}", systemZone.getId());
        log.info("🌍 System timezone offset: {}", systemTimeZone.getDisplayName());
        log.info("🌍 Current time in system timezone: {}", java.time.LocalDateTime.now());
        
        // Set the JVM default timezone to system timezone
        TimeZone.setDefault(systemTimeZone);
        
        // Log confirmation
        log.info("✅ Application timezone set to: {}", TimeZone.getDefault().getID());
        log.info("✅ This ensures frontend and backend use the same timezone for scheduling");
        
        // Additional validation
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.ZonedDateTime zonedNow = now.atZone(systemZone);
        
        log.info("🕐 Validation - Current local time: {}", now);
        log.info("🕐 Validation - Current zoned time: {}", zonedNow);
        log.info("🕐 Validation - UTC equivalent: {}", zonedNow.withZoneSameInstant(ZoneId.of("UTC")));
    }

    /**
     * Configure Jackson ObjectMapper for consistent JSON datetime serialization
     * This ensures API responses use the correct timezone
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register JavaTimeModule for Java 8 time support
        mapper.registerModule(new JavaTimeModule());
        
        // Set timezone to system default
        mapper.setTimeZone(TimeZone.getDefault());
        
        // Configure serialization options
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        log.info("✅ Jackson ObjectMapper configured with timezone: {}", TimeZone.getDefault().getID());
        
        return mapper;
    }
}
