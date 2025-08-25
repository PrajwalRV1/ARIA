package com.aria.interview_orchestrator.config;

import com.aria.interview_orchestrator.service.SessionManagerService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT Authentication Filter for validating session tokens
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SessionManagerService sessionManagerService;

    public JwtAuthenticationFilter(SessionManagerService sessionManagerService) {
        this.sessionManagerService = sessionManagerService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String token = extractTokenFromRequest(request);
        
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = sessionManagerService.validateToken(token);
                
                if (claims != null) {
                    String userId = claims.getSubject();
                    String userType = claims.get("userType", String.class);
                    String sessionId = claims.get("sessionId", String.class);
                    
                    // Update session last accessed time
                    SessionManagerService.SessionInfo sessionInfo = sessionManagerService.getSessionInfo(token);
                    if (sessionInfo != null) {
                        sessionInfo.updateLastAccessed();
                    }
                    
                    // Create authorities based on user type
                    List<SimpleGrantedAuthority> authorities = getAuthoritiesForUserType(userType);
                    
                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken = 
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    
                    // Add additional details
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                    // Add session info to request attributes for controllers to access
                    request.setAttribute("sessionId", sessionId);
                    request.setAttribute("userId", userId);
                    request.setAttribute("userType", userType);
                    request.setAttribute("sessionToken", token);
                }
            } catch (Exception e) {
                logger.error("JWT authentication failed", e);
                // Clear security context on token validation failure
                SecurityContextHolder.clearContext();
            }
        }
        
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from request headers
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        // Check Authorization header first (Bearer token)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        // Check X-Session-Token header
        String sessionHeader = request.getHeader("X-Session-Token");
        if (sessionHeader != null && !sessionHeader.isEmpty()) {
            return sessionHeader;
        }
        
        // Check query parameter (for WebSocket connections)
        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isEmpty()) {
            return queryToken;
        }
        
        return null;
    }

    /**
     * Get authorities based on user type
     */
    private List<SimpleGrantedAuthority> getAuthoritiesForUserType(String userType) {
        switch (userType.toLowerCase()) {
            case "recruiter":
                return Arrays.asList(
                    new SimpleGrantedAuthority("ROLE_RECRUITER"),
                    new SimpleGrantedAuthority("PERMISSION_SCHEDULE_INTERVIEW"),
                    new SimpleGrantedAuthority("PERMISSION_VIEW_RESULTS"),
                    new SimpleGrantedAuthority("PERMISSION_MANAGE_SESSIONS")
                );
            case "candidate":
                return Arrays.asList(
                    new SimpleGrantedAuthority("ROLE_CANDIDATE"),
                    new SimpleGrantedAuthority("PERMISSION_JOIN_INTERVIEW"),
                    new SimpleGrantedAuthority("PERMISSION_SUBMIT_RESPONSES")
                );
            case "ai_avatar":
                return Arrays.asList(
                    new SimpleGrantedAuthority("ROLE_AI_AVATAR"),
                    new SimpleGrantedAuthority("PERMISSION_CONDUCT_INTERVIEW"),
                    new SimpleGrantedAuthority("PERMISSION_ACCESS_QUESTIONS")
                );
            default:
                return Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        String method = request.getMethod();
        
        // Skip filter for public endpoints
        return path.equals("/api/sessions/health") ||
               path.equals("/api/sessions/login") ||
               path.equals("/api/sessions/refresh") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs/") ||
               // Allow CORS preflight requests
               "OPTIONS".equals(method) ||
               (path.equals("/api/interviews/schedule") && "POST".equals(method)) ||
               (path.equals("/api/interviews/create-meeting-room") && "POST".equals(method));
    }
}
