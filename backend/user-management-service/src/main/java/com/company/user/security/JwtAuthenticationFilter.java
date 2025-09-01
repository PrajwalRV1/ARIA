package com.company.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final EnhancedJwtUtil enhancedJwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, EnhancedJwtUtil enhancedJwtUtil) {
        this.jwtUtil = jwtUtil;
        this.enhancedJwtUtil = enhancedJwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                // Validate token using JwtUtil
                Jws<Claims> claims = jwtUtil.validateAndParse(token);
                String subject = claims.getBody().getSubject();
                
                // Extract user type and roles using EnhancedJwtUtil for better claim extraction
                String userType = null;
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                
                try {
                    // Try to extract user type from enhanced JWT util
                    userType = enhancedJwtUtil.extractUserType(token);
                    log.debug("Extracted user type from JWT: {}", userType);
                    
                    // Map user types to Spring Security roles
                    if (userType != null) {
                        switch (userType.toUpperCase()) {
                            case "RECRUITER":
                                authorities.add(new SimpleGrantedAuthority("ROLE_RECRUITER"));
                                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                                log.debug("Granted RECRUITER and USER roles");
                                break;
                            case "ADMIN":
                                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                                authorities.add(new SimpleGrantedAuthority("ROLE_RECRUITER"));
                                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                                log.debug("Granted ADMIN, RECRUITER, and USER roles");
                                break;
                            case "CANDIDATE":
                                authorities.add(new SimpleGrantedAuthority("ROLE_CANDIDATE"));
                                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                                log.debug("Granted CANDIDATE and USER roles");
                                break;
                            case "AI_AVATAR":
                                authorities.add(new SimpleGrantedAuthority("ROLE_AI_AVATAR"));
                                log.debug("Granted AI_AVATAR role");
                                break;
                            default:
                                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                                log.debug("Granted default USER role for userType: {}", userType);
                                break;
                        }
                    } else {
                        // Fallback: if no userType found, grant basic USER role
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                        log.debug("No userType found, granted default USER role");
                    }
                } catch (Exception e) {
                    log.warn("Could not extract userType from token, granting USER role: {}", e.getMessage());
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                }
                
                // Create authentication token with proper authorities
                var authToken = new UsernamePasswordAuthenticationToken(subject, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authToken);
                
                log.debug("Successfully authenticated user: {} with authorities: {}", subject, authorities);
                
            } catch (JwtException ex) {
                log.warn("Invalid JWT token: {}", ex.getMessage());
                // Invalid token — leave context empty, will result in 401 Unauthorized
            } catch (Exception ex) {
                log.error("Error processing JWT token: {}", ex.getMessage());
                // Any other error — leave context empty
            }
        } else {
            log.debug("No Authorization header or Bearer token found");
        }
        
        chain.doFilter(req, res);
    }
}
