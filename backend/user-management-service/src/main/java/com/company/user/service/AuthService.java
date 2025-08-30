package com.company.user.service;

import com.company.user.dto.*;
import com.company.user.model.Recruiter;
import com.company.user.model.PasswordResetToken;
import com.company.user.repository.RecruiterRepository;
import com.company.user.repository.PasswordResetTokenRepository;
import com.company.user.security.EnhancedJwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final RecruiterRepository recruiterRepository;
    private final PasswordEncoder passwordEncoder;
private final EnhancedJwtUtil jwtUtil;
    private final OtpService otpService;
    private final PasswordResetTokenRepository resetTokenRepo;
    private final EmailService emailService;
    private final long resetTokenExpirySeconds;

    public AuthService(RecruiterRepository recruiterRepository,
                       PasswordEncoder passwordEncoder,
EnhancedJwtUtil jwtUtil,
                       OtpService otpService,
                       PasswordResetTokenRepository resetTokenRepo,
                       EmailService emailService,
                       @Value("${app.reset-token.expiry-seconds:3600}") long resetTokenExpirySeconds) {
        this.recruiterRepository = recruiterRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
        this.resetTokenRepo = resetTokenRepo;
        this.emailService = emailService;
        this.resetTokenExpirySeconds = resetTokenExpirySeconds;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (recruiterRepository.existsByEmail(req.email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        Recruiter r = new Recruiter();
        r.setFullName(req.fullName);
        r.setEmail(req.email);
        r.setPasswordHash(passwordEncoder.encode(req.password));
        r.setOtpVerified(true);
        recruiterRepository.save(r);

        String access = jwtUtil.generateAccessToken(r.getId(), Map.of(
            "email", r.getEmail(), 
            "fullName", r.getFullName(),
            "userType", "RECRUITER",
            "tenantId", deriveTenantId(r.getEmail())
        ));

        AuthResponse resp = new AuthResponse();
        resp.token = access;
        resp.refreshToken = null; // Simplified - no refresh tokens for now
        resp.userId = r.getId();
        resp.fullName = r.getFullName();
        resp.email = r.getEmail();
        return resp;
    }

    public AuthResponse login(LoginRequest req) {
        var recruiter = recruiterRepository.findByEmail(req.email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(req.password, recruiter.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        var access = jwtUtil.generateAccessToken(recruiter.getId(), Map.of(
            "email", recruiter.getEmail(), 
            "fullName", recruiter.getFullName(),
            "userType", "RECRUITER",
            "tenantId", deriveTenantId(recruiter.getEmail())
        ));

        AuthResponse resp = new AuthResponse();
        resp.token = access;
        resp.refreshToken = null; // Simplified - no refresh tokens for now
        resp.userId = recruiter.getId();
        resp.fullName = recruiter.getFullName();
        resp.email = recruiter.getEmail();
        return resp;
    }

    public AuthResponse refreshToken(RefreshTokenRequest req) {
        // Simplified - not implemented for now
        throw new UnsupportedOperationException("Refresh token not implemented in simplified setup");
    }

    public void logout(String refreshToken) {
        // Simplified - no refresh tokens to revoke
    }

    // Password reset flow
    public void createPasswordResetToken(String email) {
        var user = recruiterRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Do not reveal existence - return silently
            return;
        }
        // invalidate previous tokens for user
        resetTokenRepo.deleteByUserId(user.getId());

        PasswordResetToken t = new PasswordResetToken();
        t.setToken(UUID.randomUUID().toString() + "." + UUID.randomUUID().toString());
        t.setUserId(user.getId());
        Instant now = Instant.now();
        t.setCreatedAt(now);
        t.setExpiresAt(now.plusSeconds(resetTokenExpirySeconds));
        t.setUsed(false);
        resetTokenRepo.save(t);

        emailService.sendPasswordResetEmail(user.getEmail(), t.getToken());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        var opt = resetTokenRepo.findByToken(token).orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));
        var t = opt;
        if (t.isUsed() || Instant.now().isAfter(t.getExpiresAt())) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }
        var user = recruiterRepository.findById(t.getUserId()).orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        recruiterRepository.save(user);
        t.setUsed(true);
        resetTokenRepo.save(t);
        // Simplified - no refresh tokens to revoke
    }
    
    /**
     * Derive tenant ID from user email for consistent tenant isolation.
     * For backward compatibility with existing data.
     */
    private String deriveTenantId(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "default";
        }
        
        // For the specific recruiter email that has existing data, use tenant_456
        if ("ciwojeg982@lanipe.com".equals(email.trim())) {
            return "tenant_456";
        }
        
        // For other emails, derive tenant from email domain or use default
        // This can be customized based on business requirements
        String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : "default";
        return "tenant_" + Math.abs(domain.hashCode() % 1000);
    }
}
