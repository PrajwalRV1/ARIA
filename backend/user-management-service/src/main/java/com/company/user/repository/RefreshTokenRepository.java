package com.company.user.repository;

import com.company.user.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUserIdAndRevokedFalse(String userId);
}
