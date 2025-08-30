package com.company.user.util;

import com.company.user.security.EnhancedJwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * ✅ SECURITY: Utility class for extracting tenant and user context from authentication.
 * Provides secure methods to get tenant isolation data from JWT tokens without OAuth2 dependencies.
 */
@Component
@Slf4j
public class TenantContextUtil {
    
    private final EnhancedJwtUtil jwtUtil;
    
    private static final String DEFAULT_TENANT = "default";
    private static final String TENANT_CLAIM = "tenantId";
    private static final String RECRUITER_CLAIM = "recruiterId";
    private static final String USER_TYPE_CLAIM = "userType";
    
    public TenantContextUtil(EnhancedJwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }
    
    /**
     * Extract tenant ID from current authentication context or HTTP request
     * 
     * @return Tenant ID for data isolation, defaults to "default" if extraction fails
     */
    public String getCurrentTenantId() {
        try {
            // First try to get from JWT token in request header
            String tenantId = extractTenantFromRequest();
            if (tenantId != null && !tenantId.trim().isEmpty()) {
                log.debug("Extracted tenant ID from request: {}", tenantId);
                return tenantId;
            }
            
            // Fallback: try security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                String authTenantId = extractTenantFromAuth(authentication);
                if (authTenantId != null && !authTenantId.trim().isEmpty()) {
                    return authTenantId;
                }
            }
            
            log.warn("No tenant information found, using default tenant");
            return DEFAULT_TENANT;
            
        } catch (Exception e) {
            log.error("Error extracting tenant ID, falling back to default: {}", e.getMessage());
            return DEFAULT_TENANT;
        }
    }
    
    /**
     * Extract tenant ID from authentication context (static method for backward compatibility)
     * 
     * @param authentication The authentication object
     * @return Tenant ID for data isolation
     */
    public String extractTenantFromAuth(Authentication authentication) {
        if (authentication == null) {
            return DEFAULT_TENANT;
        }
        
        // Method 1: Try to extract from UserDetails
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) principal;
            String tenantId = extractTenantFromUserDetails(userDetails);
            if (tenantId != null) {
                return tenantId;
            }
        }
        
        // Method 2: Extract from authentication name (if it contains tenant info)
        String authName = authentication.getName();
        if (authName != null && authName.contains("@")) {
            String domain = authName.substring(authName.indexOf("@") + 1);
            log.debug("Extracted tenant from auth name domain: {}", domain);
            return domain;
        }
        
        // Method 3: Use principal as user ID for tenant derivation
        if (principal instanceof String userId) {
            return deriveTenantFromUser(userId);
        }
        
        // Default tenant for development/testing
        log.debug("Could not extract tenant from authentication, using default. Auth: {}", 
                authentication.getClass().getSimpleName());
        return DEFAULT_TENANT;
    }
    
    /**
     * Extract recruiter ID from current authentication context or HTTP request
     * Returns null if not available
     */
    public Long getCurrentRecruiterId() {
        try {
            // First try to get from JWT token in request header
            Long recruiterId = extractRecruiterFromRequest();
            if (recruiterId != null) {
                log.debug("Extracted recruiter ID from request: {}", recruiterId);
                return recruiterId;
            }
            
            // Fallback: try security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                String userIdStr = extractRecruiterIdFromAuth(authentication);
                if (userIdStr != null) {
                    try {
                        return Long.parseLong(userIdStr);
                    } catch (NumberFormatException e) {
                        log.debug("User ID is not numeric: {}", userIdStr);
                    }
                }
            }
            
            log.debug("No recruiter ID found in authentication context");
            return null;
            
        } catch (Exception e) {
            log.error("Error extracting recruiter ID: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract recruiter/user ID from authentication context (static method for backward compatibility)
     * 
     * @param authentication The authentication object
     * @return User ID for ownership validation, null if not found
     */
    public String extractRecruiterIdFromAuth(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        
        // Fallback: use authentication name
        String name = authentication.getName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        
        return null;
    }
    
    /**
     * Check if current user is authenticated and has valid tenant context
     */
    public boolean hasValidTenantContext() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                String tenantId = getCurrentTenantId();
                return tenantId != null && !DEFAULT_TENANT.equals(tenantId);
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking tenant context validity: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get user type from JWT authentication context or HTTP request
     */
    public String getCurrentUserType() {
        try {
            // First try to get from JWT token in request header
            String userType = extractUserTypeFromRequest();
            if (userType != null && !userType.trim().isEmpty()) {
                log.debug("Extracted user type from request: {}", userType);
                return userType;
            }
            
            log.debug("No user type found in authentication context");
            return null;
            
        } catch (Exception e) {
            log.error("Error extracting user type: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract tenant ID from UserDetails if available.
     */
    private String extractTenantFromUserDetails(UserDetails userDetails) {
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
     * Extract tenant ID from JWT token in current request
     */
    private String extractTenantFromRequest() {
        try {
            String token = getJwtTokenFromRequest();
            if (token != null) {
                // Try to extract tenant from custom JWT claim
                return extractCustomClaim(token, TENANT_CLAIM);
            }
        } catch (Exception e) {
            log.debug("Could not extract tenant from request: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract recruiter ID from JWT token in current request
     */
    private Long extractRecruiterFromRequest() {
        try {
            String token = getJwtTokenFromRequest();
            if (token != null) {
                // First try recruiter claim
                String recruiterIdStr = extractCustomClaim(token, RECRUITER_CLAIM);
                if (recruiterIdStr != null) {
                    return Long.parseLong(recruiterIdStr);
                }
                
                // Fallback: if user is recruiter, use user ID
                String userType = jwtUtil.extractUserType(token);
                if ("RECRUITER".equals(userType)) {
                    Long userId = jwtUtil.extractUserId(token);
                    if (userId != null) {
                        return userId;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract recruiter from request: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract user type from JWT token in current request
     */
    private String extractUserTypeFromRequest() {
        try {
            String token = getJwtTokenFromRequest();
            if (token != null) {
                return jwtUtil.extractUserType(token);
            }
        } catch (Exception e) {
            log.debug("Could not extract user type from request: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get JWT token from current HTTP request
     */
    private String getJwtTokenFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
            }
        } catch (Exception e) {
            log.debug("Could not get JWT token from request: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract custom claim from JWT token
     */
    private String extractCustomClaim(String token, String claimName) {
        try {
            // Use existing JWT util to extract custom claims
            return jwtUtil.extractClaim(token, claims -> claims.get(claimName, String.class));
        } catch (Exception e) {
            log.debug("Could not extract claim {} from token: {}", claimName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Derive tenant ID from user information (fallback mechanism)
     */
    private String deriveTenantFromUser(String userId) {
        try {
            if (userId != null && userId.length() > 0) {
                // Simple fallback: use first character of user ID to determine tenant
                // Replace this with your actual business logic
                char firstChar = userId.charAt(0);
                return "tenant_" + firstChar;
            }
            
            return DEFAULT_TENANT;
            
        } catch (Exception e) {
            log.error("Error deriving tenant from user ID {}: {}", userId, e.getMessage());
            return DEFAULT_TENANT;
        }
    }
    
    /**
     * Check if the current user has admin privileges.
     */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
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
