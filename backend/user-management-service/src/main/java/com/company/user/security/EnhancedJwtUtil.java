package com.company.user.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Enhanced JWT utility with proper security implementation
 * Replaces simple token generation with industry-standard JWT practices
 */
@Component
public class EnhancedJwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedJwtUtil.class);
    
    // JWT Claims
    public static final String CLAIM_USER_TYPE = "userType";
    public static final String CLAIM_SESSION_ID = "sessionId";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_ISSUED_FOR = "issuedFor";
    
    private final SecretKey jwtSecretKey;
    private final long jwtExpirationMs;
    private final long refreshExpirationMs;
    private final String jwtIssuer;

    public EnhancedJwtUtil(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiry-ms:3600000}") long jwtExpirationMs,
            @Value("${app.jwt.refresh-expiry-ms:604800000}") long refreshExpirationMs,
            @Value("${app.jwt.issuer:ARIA-System}") String jwtIssuer) {
        
        // Ensure we have a valid JWT secret - extend if too short
        String validatedSecret = jwtSecret;
        if (jwtSecret.getBytes().length < 64) {
            logger.warn("JWT secret key is too short ({} bytes), extending to meet HS512 requirements", 
                       jwtSecret.getBytes().length);
            // Extend the key by repeating it until we have at least 64 bytes
            StringBuilder extendedSecret = new StringBuilder(jwtSecret);
            while (extendedSecret.toString().getBytes().length < 64) {
                extendedSecret.append(jwtSecret);
            }
            validatedSecret = extendedSecret.toString();
            logger.info("Extended JWT secret to {} bytes for HS512 compatibility", 
                       validatedSecret.getBytes().length);
        }
        
        // Create a secure key from the validated secret
        this.jwtSecretKey = Keys.hmacShaKeyFor(validatedSecret.getBytes());
        this.jwtExpirationMs = jwtExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.jwtIssuer = jwtIssuer;
        
        logger.info("Enhanced JWT utility initialized with expiration: {}ms", jwtExpirationMs);
    }

    /**
     * Generate JWT access token for authenticated users
     */
    public String generateAccessToken(Long userId, Map<String, Object> claims) {
        return createToken(userId, claims, jwtExpirationMs, "access");
    }

    /**
     * Overload: Generate JWT access token using a String userId (e.g., UUID)
     */
    public String generateAccessToken(String userId, Map<String, Object> claims) {
        return createTokenFromSubject(userId, claims, jwtExpirationMs, "access");
    }

    /**
     * Generate JWT refresh token
     */
    public String generateRefreshToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_ISSUED_FOR, "refresh");
        return createToken(userId, claims, refreshExpirationMs, "refresh");
    }

    /**
     * Generate session token for interview participants (candidates, recruiters, AI avatars)
     */
    public String generateSessionToken(String userId, String userType, String sessionId, Map<String, Object> additionalClaims) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_TYPE, userType);
        claims.put(CLAIM_SESSION_ID, sessionId);
        claims.put(CLAIM_ISSUED_FOR, "session");
        
        // Add additional claims if provided
        if (additionalClaims != null) {
            claims.putAll(additionalClaims);
        }
        
        // Session tokens have longer expiration (interview duration + buffer)
        long sessionExpirationMs = 4 * 60 * 60 * 1000; // 4 hours
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuer(jwtIssuer)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusMillis(sessionExpirationMs)))
                .setId(java.util.UUID.randomUUID().toString())
                .signWith(jwtSecretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Create JWT token with specified parameters
     */
    private String createToken(Long userId, Map<String, Object> claims, long expirationMs, String tokenType) {
        return createTokenFromSubject(userId != null ? userId.toString() : null, claims, expirationMs, tokenType);
    }

    /**
     * Create JWT token using a String subject (userId as String)
     */
    private String createTokenFromSubject(String subject, Map<String, Object> claims, long expirationMs, String tokenType) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(jwtIssuer)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusMillis(expirationMs)))
                .setId(java.util.UUID.randomUUID().toString())
                .signWith(jwtSecretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Extract username (subject) from JWT token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract user ID as Long from JWT token
     */
    public Long extractUserId(String token) {
        String subject = extractClaim(token, Claims::getSubject);
        return subject != null ? Long.parseLong(subject) : null;
    }

    /**
     * Extract user type from JWT token
     */
    public String extractUserType(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_USER_TYPE, String.class));
    }

    /**
     * Extract session ID from JWT token
     */
    public String extractSessionId(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_SESSION_ID, String.class));
    }

    /**
     * Extract expiration date from JWT token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract any claim from JWT token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from JWT token
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token is expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            logger.error("JWT token is malformed: {}", e.getMessage());
            throw e;
        } catch (SignatureException e) {
            logger.error("JWT signature validation failed: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            logger.error("JWT token compact is empty: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Check if JWT token is expired
     */
    public Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * Validate JWT token against user details
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (JwtException e) {
            logger.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate session token for interview participants
     */
    public Boolean validateSessionToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String issuedFor = claims.get(CLAIM_ISSUED_FOR, String.class);
            
            // Verify it's a session token
            if (!"session".equals(issuedFor)) {
                logger.warn("Token is not a session token");
                return false;
            }
            
            // Check expiration
            if (isTokenExpired(token)) {
                logger.warn("Session token is expired");
                return false;
            }
            
            // Verify required claims
            String userType = claims.get(CLAIM_USER_TYPE, String.class);
            String sessionId = claims.get(CLAIM_SESSION_ID, String.class);
            
            if (userType == null || sessionId == null) {
                logger.warn("Session token missing required claims");
                return false;
            }
            
            return true;
        } catch (JwtException e) {
            logger.warn("Session token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Refresh an access token if it's valid but close to expiration
     */
    public String refreshAccessToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Long userId = Long.parseLong(claims.getSubject());
            
            // Create new claims excluding standard JWT claims
            Map<String, Object> newClaims = new HashMap<>();
            claims.entrySet().stream()
                    .filter(entry -> !isStandardClaim(entry.getKey()))
                    .forEach(entry -> newClaims.put(entry.getKey(), entry.getValue()));
            
            return generateAccessToken(userId, newClaims);
            
        } catch (Exception e) {
            logger.error("Failed to refresh token: {}", e.getMessage());
            throw new JwtException("Token refresh failed", e);
        }
    }

    /**
     * Check if claim is a standard JWT claim
     */
    private boolean isStandardClaim(String claimName) {
        return "sub".equals(claimName) || "iat".equals(claimName) || 
               "exp".equals(claimName) || "iss".equals(claimName) || 
               "jti".equals(claimName);
    }

    /**
     * Create token validation result
     */
    public TokenValidationResult createValidationResult(String token) {
        try {
            Claims claims = extractAllClaims(token);
            
            return TokenValidationResult.builder()
                    .valid(true)
                    .userId(extractUserId(token))
                    .userType(extractUserType(token))
                    .sessionId(extractSessionId(token))
                    .issuedAt(claims.getIssuedAt())
                    .expiresAt(claims.getExpiration())
                    .issuer(claims.getIssuer())
                    .message("Token validation successful")
                    .build();
                    
        } catch (JwtException e) {
            return TokenValidationResult.builder()
                    .valid(false)
                    .message("Token validation failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Token validation result class
     */
    public static class TokenValidationResult {
        private boolean valid;
        private Long userId;
        private String userType;
        private String sessionId;
        private Date issuedAt;
        private Date expiresAt;
        private String issuer;
        private String message;

        private TokenValidationResult() {}

        public static TokenValidationResultBuilder builder() {
            return new TokenValidationResultBuilder();
        }

        // Getters
        public boolean isValid() { return valid; }
        public Long getUserId() { return userId; }
        public String getUserType() { return userType; }
        public String getSessionId() { return sessionId; }
        public Date getIssuedAt() { return issuedAt; }
        public Date getExpiresAt() { return expiresAt; }
        public String getIssuer() { return issuer; }
        public String getMessage() { return message; }

        public static class TokenValidationResultBuilder {
            private final TokenValidationResult result = new TokenValidationResult();

            public TokenValidationResultBuilder valid(boolean valid) {
                result.valid = valid;
                return this;
            }

            public TokenValidationResultBuilder userId(Long userId) {
                result.userId = userId;
                return this;
            }

            public TokenValidationResultBuilder userType(String userType) {
                result.userType = userType;
                return this;
            }

            public TokenValidationResultBuilder sessionId(String sessionId) {
                result.sessionId = sessionId;
                return this;
            }

            public TokenValidationResultBuilder issuedAt(Date issuedAt) {
                result.issuedAt = issuedAt;
                return this;
            }

            public TokenValidationResultBuilder expiresAt(Date expiresAt) {
                result.expiresAt = expiresAt;
                return this;
            }

            public TokenValidationResultBuilder issuer(String issuer) {
                result.issuer = issuer;
                return this;
            }

            public TokenValidationResultBuilder message(String message) {
                result.message = message;
                return this;
            }

            public TokenValidationResult build() {
                return result;
            }
        }
    }
}
