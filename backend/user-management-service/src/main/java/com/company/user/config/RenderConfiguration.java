package com.company.user.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Configuration specific to Render deployment
 * Handles health check routing and CORS for production
 */
@Configuration
@Profile("render")
public class RenderConfiguration implements WebMvcConfigurer {

    /**
     * Root path health check filter - bypasses context path for /health endpoint
     * This ensures Render can access health checks at http://host:port/health
     */
    @Bean
    public FilterRegistrationBean<Filter> renderHealthCheckFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                
                String path = httpRequest.getRequestURI();
                System.out.println("DEBUG: Request path: " + path);
                
                // Handle root health check requests outside context path
                if ("/health".equals(path) || "/".equals(path)) {
                    httpResponse.setContentType("text/plain");
                    httpResponse.setStatus(200);
                    if ("/health".equals(path)) {
                        httpResponse.getWriter().write("OK");
                    } else {
                        httpResponse.getWriter().write("ARIA User Management Service is running");
                    }
                    return;
                }
                
                chain.doFilter(request, response);
            }
            
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {}
            
            @Override
            public void destroy() {}
        });
        
        // Set highest precedence to catch requests before context path processing
        registration.setOrder(Integer.MIN_VALUE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * CORS configuration for production
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                    "https://aria-frontend.onrender.com",
                    "https://aria-user-management.onrender.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
