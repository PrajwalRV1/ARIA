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

    @Value("${CORS_ORIGINS:https://localhost:4200}")
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
        
        // Parse comma-separated origins if multiple origins are provided
        String[] origins = corsOrigins.split(",");
        for (int i = 0; i < origins.length; i++) {
            origins[i] = origins[i].trim();
        }
        
        // Add explicit allowed origins for production
        configuration.setAllowedOrigins(Arrays.asList(
            "https://aria-frontend-fs01.onrender.com",
            corsOrigins.trim()
        ));
        
        // Add allowed origin patterns for development and flexibility
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*", 
            "http://127.0.0.1:*",
            "https://localhost:*", 
            "https://127.0.0.1:*",
            "https://aria-frontend-*.onrender.com"
        ));
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Total-Count", "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));
        configuration.setAllowCredentials(true);
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
                        .requestMatchers(
                                "/api/auth/send-otp",
                                "/api/auth/verify-otp",
                                "/api/auth/resend-otp",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/refresh-token",
                                "/health",
                                "/actuator/**",
                                "/h2-console/**",
                                "/error",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Email service endpoints (accessible by internal services and authenticated users)
                        .requestMatchers("/api/email/**").hasAnyRole("INTERNAL_SERVICE", "USER", "ADMIN")
                        // Authenticated endpoints (require valid JWT token)
                        .requestMatchers("/api/candidates/**").authenticated()
                        .requestMatchers("/api/interview/**").authenticated()
                        // Allow preflight OPTIONS requests for CORS
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
