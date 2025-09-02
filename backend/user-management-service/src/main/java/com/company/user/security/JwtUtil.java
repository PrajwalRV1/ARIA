package com.company.user.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long jwtExpiryMs;
    private final long refreshExpiryMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiry-ms}") long jwtExpiryMs,
            @Value("${app.jwt.refresh-expiry-ms}") long refreshExpiryMs) {
        
        // Ensure we have a valid JWT secret - extend if too short
        String validatedSecret = jwtSecret;
        if (jwtSecret.getBytes().length < 64) {
            // Extend the key by repeating it until we have at least 64 bytes
            StringBuilder extendedSecret = new StringBuilder(jwtSecret);
            while (extendedSecret.toString().getBytes().length < 64) {
                extendedSecret.append(jwtSecret);
            }
            validatedSecret = extendedSecret.toString();
        }
        
        this.signingKey = Keys.hmacShaKeyFor(validatedSecret.getBytes());
        this.jwtExpiryMs = jwtExpiryMs;
        this.refreshExpiryMs = refreshExpiryMs;
    }

    public String generateAccessToken(String subject, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + jwtExpiryMs))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateRefreshToken(String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + refreshExpiryMs))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public Jws<Claims> validateAndParse(String token) {
        return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token);
    }
}
