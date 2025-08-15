package com.company.user.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final int maxRetries = 3;
    // APP_BASE_URL used for reset links; set as env or fallback to localhost
    private final String appBaseUrl = System.getenv().getOrDefault("APP_BASE_URL", "http://localhost:8080");

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send OTP email (used by OTP flow).
     */
    @Async
    public void sendOtpEmail(String to, String otp) {
        int tries = 0;
        while (tries < maxRetries) {
            tries++;
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(to);
                msg.setSubject("Your verification code");
                msg.setText("Your verification code is: " + otp + ". It expires shortly.");
                mailSender.send(msg);
                return;
            } catch (Exception ex) {
                if (tries >= maxRetries) {
                    // final failure — rethrow so caller can handle/alert
                    throw new RuntimeException("Failed to send OTP email after retries", ex);
                }
                try { Thread.sleep(1000L * tries); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Send password reset email with tokenized URL.
     */
    @Async
    public void sendPasswordResetEmail(String to, String token) {
        int tries = 0;
        while (tries < maxRetries) {
            tries++;
            try {
                String resetUrl = appBaseUrl + "/reset-password?token=" + token;
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(to);
                msg.setSubject("Password reset request");
                msg.setText("You requested a password reset. Use the following link to reset your password (valid for a short time):\n\n" + resetUrl);
                mailSender.send(msg);
                return;
            } catch (Exception ex) {
                if (tries >= maxRetries) {
                    throw new RuntimeException("Failed to send reset email after retries", ex);
                }
                try { Thread.sleep(1000L * tries); } catch (InterruptedException ignored) {}
            }
        }
    }
}
