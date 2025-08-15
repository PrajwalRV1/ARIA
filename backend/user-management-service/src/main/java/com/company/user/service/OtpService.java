package com.company.user.service;

import com.company.user.model.OtpEntry;
import com.company.user.repository.OtpRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

@Service
public class OtpService {

    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final long otpExpirySeconds;
    private final long resendCooldownSeconds;
    private final int maxAttempts;

    public OtpService(OtpRepository otpRepository,
                      EmailService emailService,
                      @Value("${app.otp.expiry-seconds:300}") long otpExpirySeconds,
                      @Value("${app.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds,
                      @Value("${app.otp.max-attempts:3}") int maxAttempts) {
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.otpExpirySeconds = otpExpirySeconds;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxAttempts = maxAttempts;
    }

    private String generateOtp() {
        Random rnd = new Random();
        int number = rnd.nextInt(900000) + 100000;
        return Integer.toString(number);
    }

    @Transactional
    public void sendOtp(String email) {
        Instant now = Instant.now();
        Optional<OtpEntry> existing = otpRepository.findByEmail(email);

        if (existing.isPresent()) {
            OtpEntry e = existing.get();
            if (now.minusSeconds(resendCooldownSeconds).isBefore(e.getLastSentAt())) {
                throw new IllegalStateException("OTP resend cooldown not elapsed");
            }
        }

        String otp = generateOtp();
        OtpEntry entry = existing.orElse(new OtpEntry());
        entry.setEmail(email);
        entry.setOtpCode(otp);
        entry.setAttempts(0);
        entry.setCreatedAt(now);
        entry.setLastSentAt(now);
        entry.setExpiresAt(now.plusSeconds(otpExpirySeconds));
        otpRepository.save(entry);

        emailService.sendOtpEmail(email, otp);
    }

    @Transactional
    public boolean verifyOtp(String email, String otp) {
        OtpEntry entry = otpRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No OTP found for email"));

        if (Instant.now().isAfter(entry.getExpiresAt())) {
            otpRepository.delete(entry);
            throw new IllegalArgumentException("OTP expired");
        }

        if (entry.getAttempts() >= maxAttempts) {
            otpRepository.delete(entry);
            throw new IllegalStateException("Max OTP attempts exceeded");
        }

        entry.setAttempts(entry.getAttempts() + 1);
        otpRepository.save(entry);

        if (!entry.getOtpCode().equals(otp)) {
            if (entry.getAttempts() >= maxAttempts) {
                otpRepository.delete(entry);
            }
            return false;
        }

        otpRepository.delete(entry);
        return true;
    }

    public void deleteOtp(String email) {
        otpRepository.deleteByEmail(email);
    }
}
