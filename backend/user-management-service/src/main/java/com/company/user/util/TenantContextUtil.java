package com.company.user.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Utility class for extracting tenant and user context from authentication.
 * Provides secure methods to get tenant isolation data from JWT tokens.
 */
@Component
@Slf4j
public class TenantContextUtil {
    
    /**
     * Extract tenant ID from authentication context.
     * 
     * @param authentication The authentication object
     * @return Tenant ID for data isolation
     * @throws SecurityException if no valid tenant context found
     */
    public static String extractTenantFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new SecurityException("No authentication context found");
        }
        
        // Method 1: Try to extract from JWT token
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtToken.getToken();
            
            // Check for tenant_id claim in JWT
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (tenantId != null && !tenantId.trim().isEmpty()) {
                log.debug("Extracted tenant from JWT: {}", tenantId);
                return tenantId.trim();
            }
            
            // Fallback: extract from organization claim
            String orgId = jwt.getClaimAsString("organization_id");
            if (orgId != null && !orgId.trim().isEmpty()) {
                log.debug("Extracted tenant from organization claim: {}", orgId);
                return orgId.trim();
            }
            
            // Fallback: extract from email domain
            String email = jwt.getClaimAsString("email");
            if (email != null && email.contains("@")) {
                String domain = email.substring(email.indexOf("@") + 1);
                log.debug("Extracted tenant from email domain: {}", domain);
                return domain;
            }
        }
        
        // Method 2: Try to extract from UserDetails
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) principal;
            String tenantId = extractTenantFromUserDetails(userDetails);
            if (tenantId != null) {
                return tenantId;
            }
        }
        
        // Method 3: Extract from authentication name (if it contains tenant info)
        String authName = authentication.getName();
        if (authName != null && authName.contains("@")) {
            String domain = authName.substring(authName.indexOf("@") + 1);
            log.debug("Extracted tenant from auth name domain: {}", domain);
            return domain;
        }
        
        // Default tenant for development/testing
        log.warn("Could not extract tenant from authentication, using default. Auth: {}", 
                authentication.getClass().getSimpleName());
        return "default";
    }
    
    /**
     * Extract recruiter/user ID from authentication context.
     * 
     * @param authentication The authentication object
     * @return User ID for ownership validation
     * @throws SecurityException if no valid user context found
     */
    public static String extractRecruiterIdFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new SecurityException("No authentication context found");
        }
        
        // Method 1: Try to extract from JWT token
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtToken.getToken();
            
            // Check for user_id claim
            String userId = jwt.getClaimAsString("user_id");
            if (userId != null && !userId.trim().isEmpty()) {
                return userId.trim();
            }
            
            // Check for sub claim (standard JWT subject)
            String sub = jwt.getClaimAsString("sub");
            if (sub != null && !sub.trim().isEmpty()) {
                return sub.trim();
            }
            
            // Check for email
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.trim().isEmpty()) {
                return email.trim();
            }
        }
        
        // Fallback: use authentication name
        String name = authentication.getName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        
        throw new SecurityException("Could not extract user ID from authentication");
    }
    
    /**
     * Extract tenant ID from UserDetails if available.
     */
    private static String extractTenantFromUserDetails(UserDetails userDetails) {
        // If using custom UserDetails that implements tenant info
        if (userDetails instanceof TenantAwareUserDetails) {
            return ((TenantAwareUserDetails) userDetails).getTenantId();
        }
        
        // Extract from username if it contains tenant info (e.g., user@tenant.com)
        String username = userDetails.getUsername();
        if (username != null && username.contains("@")) {
            return username.substring(username.indexOf("@") + 1);
        }
        
        return null;
    }
    
    /**
     * Check if the current user has admin privileges.
     */
    public static boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));
    }
    
    /**
     * Interface for tenant-aware UserDetails implementations.
     */
    public interface TenantAwareUserDetails {
        String getTenantId();
    }
}
