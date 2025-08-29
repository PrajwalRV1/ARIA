package com.company.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.company.user.security.JwtAuthenticationFilter;
import com.company.user.security.InternalServiceAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${CORS_ORIGINS:https://aria-frontend-fs01.onrender.com}")
    private String corsOrigins;
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);

    // Password encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS configuration source
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        logger.info("Configuring CORS with origins: {}", corsOrigins);
        
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        
        // Set allowed origins - use both explicit and patterns
        configuration.addAllowedOrigin("https://aria-frontend-fs01.onrender.com");
        configuration.addAllowedOrigin(corsOrigins.trim());
        
        // Add origin patterns for flexibility
        configuration.addAllowedOriginPattern("http://localhost:*");
        configuration.addAllowedOriginPattern("https://localhost:*");
        configuration.addAllowedOriginPattern("http://127.0.0.1:*");
        configuration.addAllowedOriginPattern("https://127.0.0.1:*");
        configuration.addAllowedOriginPattern("https://aria-frontend-*.onrender.com");
        
        // Set allowed methods
        configuration.addAllowedMethod("GET");
        configuration.addAllowedMethod("POST");
        configuration.addAllowedMethod("PUT");
        configuration.addAllowedMethod("DELETE");
        configuration.addAllowedMethod("OPTIONS");
        configuration.addAllowedMethod("PATCH");
        configuration.addAllowedMethod("HEAD");
        
        // Set allowed headers
        configuration.addAllowedHeader("*");
        
        // Set exposed headers
        configuration.addExposedHeader("Authorization");
        configuration.addExposedHeader("Content-Type");
        configuration.addExposedHeader("X-Total-Count");
        
        configuration.setMaxAge(3600L);
        
        logger.info("CORS configuration - Allowed Origins: {}", configuration.getAllowedOrigins());
        logger.info("CORS configuration - Allowed Origin Patterns: {}", configuration.getAllowedOriginPatterns());
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // Security filter chain - FIXED for Spring Security 6
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, 
            InternalServiceAuthenticationFilter internalServiceFilter) throws Exception {
        http
                // Modern Spring Security 6 syntax
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Disable frame options for H2 console
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                // Add internal service authentication filter (before JWT filter)
                .addFilterBefore(internalServiceFilter, UsernamePasswordAuthenticationFilter.class)
                // Add JWT authentication filter (after internal service filter)
                .addFilterAfter(jwtAuthenticationFilter, InternalServiceAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no authentication required)
                        // NOTE: With context path /api/auth, Spring Security sees paths relative to context
                        .requestMatchers(
                                "/send-otp",       // /api/auth/send-otp
                                "/verify-otp",     // /api/auth/verify-otp
                                "/resend-otp",     // /api/auth/resend-otp
                                "/register",       // /api/auth/register
                                "/login",          // /api/auth/login
                                "/forgot-password", // /api/auth/forgot-password
                                "/reset-password", // /api/auth/reset-password
                                "/refresh-token",  // /api/auth/refresh-token
                                "/cors-test",      // /api/auth/cors-test
                                "/health",         // /api/auth/health (if exists)
                                "/actuator/**",    // /api/auth/actuator/**
                                "/h2-console/**",  // /api/auth/h2-console/**
                                "/error",          // /api/auth/error
                                "/swagger-ui/**",  // /api/auth/swagger-ui/**
                                "/v3/api-docs/**"  // /api/auth/v3/api-docs/**
                        ).permitAll()
                        // Email service endpoints (accessible by internal services and authenticated users)
                        .requestMatchers("/api/email/**").hasAnyRole("INTERNAL_SERVICE", "USER", "ADMIN")
                        // TEMPORARY: Allow GET access to candidates for testing (must be before general candidates rules)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/candidates").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/candidates/**").permitAll()
                        // Authenticated endpoints (require valid JWT token)
                        .requestMatchers("/candidates/**").authenticated()
                        .requestMatchers("/api/interview/**").authenticated()
                        // Allow preflight OPTIONS requests for CORS
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
