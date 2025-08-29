package com.company.user.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Backup CORS Filter to ensure CORS headers are always set.
 * This runs at the highest priority to handle CORS before Spring Security.
 */
@Component
@Order(1)
public class CorsFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CorsFilter.class);

    @Value("${CORS_ORIGINS:https://localhost:4200}")
    private String corsOrigins;

    private static final List<String> ALLOWED_METHODS = Arrays.asList(
        "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = request.getHeader("Origin");
        String method = request.getMethod();
        
        logger.debug("CORS Filter - Origin: {}, Method: {}", origin, method);

        // Set CORS headers for all requests
        if (isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            logger.debug("CORS Filter - Set Access-Control-Allow-Origin: {}", origin);
        } else {
            // For development, allow localhost
            if (origin != null && (origin.startsWith("http://localhost") || 
                                  origin.startsWith("https://localhost") ||
                                  origin.startsWith("http://127.0.0.1") ||
                                  origin.startsWith("https://127.0.0.1"))) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                logger.debug("CORS Filter - Set Access-Control-Allow-Origin for localhost: {}", origin);
            }
        }

        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");
        response.setHeader("Access-Control-Allow-Headers", 
            "Origin, Content-Type, Accept, Authorization, X-Requested-With, Access-Control-Request-Method, Access-Control-Request-Headers");
        response.setHeader("Access-Control-Expose-Headers", 
            "Authorization, Content-Type, X-Total-Count, Access-Control-Allow-Origin, Access-Control-Allow-Credentials");
        response.setHeader("Access-Control-Max-Age", "3600");

        // Handle preflight requests
        if ("OPTIONS".equalsIgnoreCase(method)) {
            logger.debug("CORS Filter - Handling OPTIONS preflight request");
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null) {
            return false;
        }

        // Check exact match with configured origins
        String[] allowedOrigins = corsOrigins.split(",");
        for (String allowedOrigin : allowedOrigins) {
            if (origin.equals(allowedOrigin.trim())) {
                return true;
            }
        }

        // Check for aria-frontend pattern
        if (origin.matches("https://aria-frontend.*\\.onrender\\.com")) {
            return true;
        }

        return false;
    }
}
