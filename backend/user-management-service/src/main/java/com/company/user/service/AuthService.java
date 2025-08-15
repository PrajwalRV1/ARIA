package com.company.user.service;

import com.company.user.dto.*;
import com.company.user.model.Recruiter;
import com.company.user.model.PasswordResetToken;
import com.company.user.model.RefreshToken;
import com.company.user.repository.RecruiterRepository;
import com.company.user.repository.PasswordResetTokenRepository;
import com.company.user.security.JwtUtil;
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
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenRepository resetTokenRepo;
    private final EmailService emailService;
    private final long resetTokenExpirySeconds;

    public AuthService(RecruiterRepository recruiterRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       OtpService otpService,
                       RefreshTokenService refreshTokenService,
                       PasswordResetTokenRepository resetTokenRepo,
                       EmailService emailService,
                       @Value("${app.reset-token.expiry-seconds:3600}") long resetTokenExpirySeconds) {
        this.recruiterRepository = recruiterRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
        this.refreshTokenService = refreshTokenService;
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

        String access = jwtUtil.generateAccessToken(r.getId(), Map.of("email", r.getEmail(), "fullName", r.getFullName()));
        RefreshToken refresh = refreshTokenService.createTokenForUser(r.getId());

        AuthResponse resp = new AuthResponse();
        resp.token = access;
        resp.refreshToken = refresh.getToken();
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

        var access = jwtUtil.generateAccessToken(recruiter.getId(), Map.of("email", recruiter.getEmail(), "fullName", recruiter.getFullName()));
        var refresh = refreshTokenService.createTokenForUser(recruiter.getId());

        AuthResponse resp = new AuthResponse();
        resp.token = access;
        resp.refreshToken = refresh.getToken();
        resp.userId = recruiter.getId();
        resp.fullName = recruiter.getFullName();
        resp.email = recruiter.getEmail();
        return resp;
    }

    public AuthResponse refreshToken(RefreshTokenRequest req) {
        var existing = refreshTokenService.findValidToken(req.refreshToken);
        if (existing == null) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }
        // rotate: revoke old, create new
        refreshTokenService.revokeToken(existing);
        var recruiter = recruiterRepository.findById(existing.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        var access = jwtUtil.generateAccessToken(recruiter.getId(), Map.of("email", recruiter.getEmail(), "fullName", recruiter.getFullName()));
        var newRefresh = refreshTokenService.createTokenForUser(recruiter.getId());

        AuthResponse resp = new AuthResponse();
        resp.token = access;
        resp.refreshToken = newRefresh.getToken();
        resp.userId = recruiter.getId();
        resp.fullName = recruiter.getFullName();
        resp.email = recruiter.getEmail();
        return resp;
    }

    public void logout(String refreshToken) {
        var existing = refreshTokenService.findValidToken(refreshToken);
        if (existing != null) {
            refreshTokenService.revokeToken(existing);
        }
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
        // revoke all refresh tokens for the user (optional security measure)
        refreshTokenService.revokeAllForUser(user.getId());
    }
}
