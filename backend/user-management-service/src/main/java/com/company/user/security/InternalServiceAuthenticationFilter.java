package com.company.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Authentication filter for internal service-to-service communication
 * Allows requests from internal services with proper headers to bypass JWT authentication
 */
@Component
// @Slf4j - Temporarily disabled due to compilation issues
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthenticationFilter.class);
    
    @Value("${app.services.internal.api-key:ARIA_INTERNAL_SERVICE_KEY_2024}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
            FilterChain filterChain) throws ServletException, IOException {

        String internalService = request.getHeader("X-Internal-Service");
        String apiKey = request.getHeader("X-Internal-API-Key");

        // Check if this is an internal service request
        if (internalService != null && apiKey != null) {
            log.debug("Processing internal service request from: {}", internalService);

            // Validate the API key
            if (expectedApiKey.equals(apiKey)) {
                // Check if this is a valid internal service
                if (isValidInternalService(internalService)) {
                    // Create an authentication token for the internal service
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                        "internal-service-" + internalService,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
                    );
                    
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("✅ Internal service authenticated: {}", internalService);
                } else {
                    log.warn("⚠️ Unknown internal service: {}", internalService);
                }
            } else {
                log.warn("❌ Invalid API key for internal service: {}", internalService);
            }
        }

        // Continue with the filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Validate if the service name is a known internal service
     */
    private boolean isValidInternalService(String serviceName) {
        return "interview-orchestrator".equals(serviceName) || 
               "adaptive-engine".equals(serviceName) ||
               "ai-analytics".equals(serviceName);
    }
}
