package com.company.user.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recruiters", indexes = {
        @Index(name = "idx_recruiter_email", columnList = "email", unique = true)
})
public class Recruiter {
    @Id
    @Column(nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_otp_verified", nullable = false)
    private boolean otpVerified = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Recruiter() {}

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isOtpVerified() { return otpVerified; }
    public void setOtpVerified(boolean otpVerified) { this.otpVerified = otpVerified; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
