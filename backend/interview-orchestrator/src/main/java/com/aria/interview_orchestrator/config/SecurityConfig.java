package com.aria.interview_orchestrator.config;

import com.aria.interview_orchestrator.service.SessionManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Security Configuration for JWT-based authentication
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private SessionManagerService sessionManagerService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints - no authentication required (health checks and docs)
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        
                        // Interview scheduling endpoints - require JWT authentication for recruiters
                        .requestMatchers("/api/interview/schedule").authenticated()
                        .requestMatchers("/api/interview/meeting/create").authenticated()
                        .requestMatchers("/api/interview/meeting/share").authenticated()
                        
                        // Session management endpoints
                        .requestMatchers("/api/sessions/login").permitAll()
                        .requestMatchers("/api/sessions/refresh").permitAll()
                        
                        // Protected endpoints - require valid session token
                        .requestMatchers("/api/interviews/session/**").authenticated()
                        .requestMatchers("/api/interviews/*/questions").authenticated()
                        .requestMatchers("/api/interviews/*/responses").authenticated()
                        .requestMatchers("/api/interviews/*/results").authenticated()
                        .requestMatchers("/api/sessions/validate").authenticated()
                        .requestMatchers("/api/sessions/logout").authenticated()
                        
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(sessionManagerService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow frontend origins
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:4200",
                "http://localhost:3000", 
                "https://*.aria.com",
                "https://*.aria.io"
        ));
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-Session-Token", "X-Refresh-Token"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
