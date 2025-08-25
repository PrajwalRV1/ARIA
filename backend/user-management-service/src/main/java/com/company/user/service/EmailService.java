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

    /**
     * Send interview invitation email to candidate with access token
     */
    @Async
    public void sendInterviewInvitation(String candidateEmail, String candidateName, 
                                       String sessionToken, String sessionId, 
                                       String scheduledDateTime, String position,
                                       String recruiterName, String companyName) {
        int tries = 0;
        while (tries < maxRetries) {
            tries++;
            try {
                // Create interview join URL with embedded token
                String interviewUrl = appBaseUrl + "/interview/" + sessionId + "?token=" + sessionToken;
                
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(candidateEmail);
                msg.setSubject("Interview Invitation - " + position + " at " + companyName);
                
                String emailBody = createInterviewInvitationText(
                    candidateName, interviewUrl, scheduledDateTime, 
                    position, recruiterName, companyName, sessionId
                );
                
                msg.setText(emailBody);
                mailSender.send(msg);
                return;
            } catch (Exception ex) {
                if (tries >= maxRetries) {
                    throw new RuntimeException("Failed to send interview invitation after retries", ex);
                }
                try { Thread.sleep(1000L * tries); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Send interview invitation email to recruiter with monitoring access
     */
    @Async
    public void sendRecruiterInterviewNotification(String recruiterEmail, String recruiterName,
                                                  String candidateName, String sessionToken, 
                                                  String sessionId, String scheduledDateTime, 
                                                  String position) {
        int tries = 0;
        while (tries < maxRetries) {
            tries++;
            try {
                // Create recruiter monitoring URL
                String monitorUrl = appBaseUrl + "/interview/" + sessionId + "?token=" + sessionToken + "&role=recruiter";
                
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(recruiterEmail);
                msg.setSubject("Interview Scheduled - " + candidateName + " for " + position);
                
                String emailBody = createRecruiterNotificationText(
                    recruiterName, candidateName, monitorUrl, 
                    scheduledDateTime, position, sessionId
                );
                
                msg.setText(emailBody);
                mailSender.send(msg);
                return;
            } catch (Exception ex) {
                if (tries >= maxRetries) {
                    throw new RuntimeException("Failed to send recruiter notification after retries", ex);
                }
                try { Thread.sleep(1000L * tries); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Create interview invitation email text content
     */
    private String createInterviewInvitationText(String candidateName, String interviewUrl,
                                                String scheduledDateTime, String position,
                                                String recruiterName, String companyName,
                                                String sessionId) {
        return String.format(
            "Dear %s,\n\n" +
            "You have been invited to an AI-powered interview for the %s position at %s.\n\n" +
            "📅 Interview Details:\n" +
            "   • Position: %s\n" +
            "   • Scheduled: %s\n" +
            "   • Interviewer: %s\n" +
            "   • Session ID: %s\n\n" +
            "🎥 JOIN YOUR INTERVIEW:\n" +
            "Click the link below to join your interview at the scheduled time:\n" +
            "%s\n\n" +
            "📋 IMPORTANT INSTRUCTIONS:\n" +
            "• Join 5-10 minutes before your scheduled time\n" +
            "• Ensure you have a stable internet connection\n" +
            "• Use Chrome or Firefox browser for best experience\n" +
            "• Have your camera and microphone ready\n" +
            "• Find a quiet, well-lit space for the interview\n\n" +
            "🔒 SECURE ACCESS:\n" +
            "This link is unique to you and expires after the interview.\n" +
            "Do not share this link with anyone else.\n\n" +
            "💡 TECHNICAL REQUIREMENTS:\n" +
            "• Modern web browser (Chrome/Firefox recommended)\n" +
            "• Working camera and microphone\n" +
            "• Stable internet connection (minimum 1 Mbps)\n\n" +
            "❓ NEED HELP?\n" +
            "If you experience any technical issues, please contact the recruiter immediately.\n\n" +
            "Good luck with your interview!\n\n" +
            "Best regards,\n" +
            "ARIA Interview Platform\n" +
            "%s",
            candidateName, position, companyName, position, scheduledDateTime, 
            recruiterName, sessionId, interviewUrl, companyName
        );
    }

    /**
     * Create recruiter notification email text content
     */
    private String createRecruiterNotificationText(String recruiterName, String candidateName,
                                                  String monitorUrl, String scheduledDateTime,
                                                  String position, String sessionId) {
        return String.format(
            "Dear %s,\n\n" +
            "An interview has been successfully scheduled in the ARIA platform.\n\n" +
            "📅 Interview Details:\n" +
            "   • Candidate: %s\n" +
            "   • Position: %s\n" +
            "   • Scheduled: %s\n" +
            "   • Session ID: %s\n\n" +
            "👁️ MONITOR THE INTERVIEW:\n" +
            "Use the link below to join as a proctor and monitor the interview:\n" +
            "%s\n\n" +
            "🔧 PROCTOR CAPABILITIES:\n" +
            "• Real-time audio/video monitoring\n" +
            "• Live transcript viewing\n" +
            "• Join/leave at any time during interview\n" +
            "• Access to AI analytics and scoring\n" +
            "• Interview session controls\n\n" +
            "📊 POST-INTERVIEW:\n" +
            "• Detailed analytics will be available after completion\n" +
            "• AI-generated interview summary and recommendations\n" +
            "• Candidate scoring and skill assessment\n\n" +
            "🔒 SECURE ACCESS:\n" +
            "This monitoring link is unique to your session and expires after the interview.\n\n" +
            "The candidate has been notified and will receive their interview invitation separately.\n\n" +
            "Best regards,\n" +
            "ARIA Interview Platform",
            recruiterName, candidateName, position, scheduledDateTime, 
            sessionId, monitorUrl
        );
    }
}
