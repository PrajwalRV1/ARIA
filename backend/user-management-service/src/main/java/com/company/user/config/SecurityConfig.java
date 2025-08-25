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

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Password encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS configuration source
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*", 
            "http://127.0.0.1:*",
            "https://localhost:*", 
            "https://127.0.0.1:*"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
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
                // Add internal service authentication filter (before JWT filter)
                .addFilterBefore(internalServiceFilter, UsernamePasswordAuthenticationFilter.class)
                // Add JWT authentication filter (after internal service filter)
                .addFilterAfter(jwtAuthenticationFilter, InternalServiceAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no authentication required)
                        .requestMatchers(
                                "/api/auth/send-otp",
                                "/api/auth/verify-otp",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                // Support both /auth/** and /api/auth/** patterns
                                "/auth/send-otp",
                                "/auth/verify-otp", 
                                "/auth/register",
                                "/auth/login",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                // User session endpoints for interview token generation
                                "/api/user/sessions/login",
                                "/api/user/sessions/health",
                                "/api/user/sessions/refresh",
                                "/api/user/sessions/logout",
                                "/error",
                                "/actuator/health",
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
