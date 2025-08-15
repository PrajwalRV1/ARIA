package com.company.user.service;

import com.company.user.model.RefreshToken;
import com.company.user.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpirySeconds;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${app.jwt.refresh-expiry-ms:1209600000}") long refreshExpiryMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpirySeconds = refreshExpiryMs / 1000;
    }

    public RefreshToken createTokenForUser(String userId) {
        RefreshToken token = new RefreshToken();
        token.setToken(UUID.randomUUID().toString() + "." + UUID.randomUUID().toString());
        token.setUserId(userId);
        Instant now = Instant.now();
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusSeconds(refreshTokenExpirySeconds));
        token.setRevoked(false);
        return refreshTokenRepository.save(token);
    }

    public RefreshToken findValidToken(String tokenStr) {
        return refreshTokenRepository.findByToken(tokenStr)
                .filter(t -> !t.isRevoked() && Instant.now().isBefore(t.getExpiresAt()))
                .orElse(null);
    }

    public void revokeToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    public void revokeAllForUser(String userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        for (RefreshToken t : tokens) {
            t.setRevoked(true);
        }
        refreshTokenRepository.saveAll(tokens);
    }
}
