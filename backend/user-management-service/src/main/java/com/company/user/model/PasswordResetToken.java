package com.company.user.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_reset_token_token", columnList = "token")
})
public class PasswordResetToken {
    @Id
    @Column(nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    public PasswordResetToken() {}

    public String getId() { return id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
}
