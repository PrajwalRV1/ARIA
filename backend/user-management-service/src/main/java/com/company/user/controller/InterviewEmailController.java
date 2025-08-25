package com.company.user.controller;

import com.company.user.service.InterviewEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Interview Email Services
 * Exposes the comprehensive InterviewEmailService via REST endpoints
 */
@RestController
@RequestMapping("/api/email/interview")
@CrossOrigin(origins = {"http://localhost:4200", "https://localhost:4200", "http://localhost:8081", "https://localhost:8081"}, allowCredentials = "true")
@Slf4j
public class InterviewEmailController {

    @Autowired
    private InterviewEmailService emailService;

    /**
     * Send candidate interview invitation
     * POST /api/email/interview/candidate-invitation
     */
    @PostMapping("/candidate-invitation")
    public ResponseEntity<Map<String, Object>> sendCandidateInvitation(
            @RequestBody InterviewEmailService.CandidateInvitationData invitationData) {
        try {
            log.info("📧 Received candidate invitation request for: {}", invitationData.getCandidateEmail());
            
            boolean success = emailService.sendCandidateInterviewInvitation(invitationData);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Candidate invitation sent successfully",
                    "recipient", invitationData.getCandidateEmail(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Failed to send candidate invitation",
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Error processing candidate invitation request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Send recruiter interview notification
     * POST /api/email/interview/recruiter-notification
     */
    @PostMapping("/recruiter-notification")
    public ResponseEntity<Map<String, Object>> sendRecruiterNotification(
            @RequestBody InterviewEmailService.RecruiterNotificationData notificationData) {
        try {
            log.info("📧 Received recruiter notification request for: {}", notificationData.getRecruiterEmail());
            
            boolean success = emailService.sendRecruiterInterviewNotification(notificationData);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Recruiter notification sent successfully",
                    "recipient", notificationData.getRecruiterEmail(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Failed to send recruiter notification",
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Error processing recruiter notification request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Send AI Avatar activation notification
     * POST /api/email/interview/ai-avatar-activation
     */
    @PostMapping("/ai-avatar-activation")
    public ResponseEntity<Map<String, Object>> sendAIAvatarActivation(
            @RequestBody InterviewEmailService.AIAvatarActivationData activationData) {
        try {
            log.info("🤖 Received AI Avatar activation request for session: {}", activationData.getSessionId());
            
            boolean success = emailService.sendAIAvatarActivationNotification(activationData);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "AI Avatar activation notification sent successfully",
                    "sessionId", activationData.getSessionId(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Failed to send AI Avatar notification",
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Error processing AI Avatar activation request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Send interview reminder
     * POST /api/email/interview/reminder
     */
    @PostMapping("/reminder")
    public ResponseEntity<Map<String, Object>> sendInterviewReminder(
            @RequestBody InterviewEmailService.InterviewReminderData reminderData) {
        try {
            log.info("⏰ Received interview reminder request for: {}", reminderData.getEmail());
            
            boolean success = emailService.sendInterviewReminder(reminderData);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Interview reminder sent successfully",
                    "recipient", reminderData.getEmail(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Failed to send interview reminder",
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Error processing interview reminder request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Send interview completion notification
     * POST /api/email/interview/completion
     */
    @PostMapping("/completion")
    public ResponseEntity<Map<String, Object>> sendInterviewCompletion(
            @RequestBody InterviewEmailService.InterviewCompletionData completionData) {
        try {
            log.info("🏁 Received interview completion request for session: {}", completionData.getSessionId());
            
            boolean success = emailService.sendInterviewCompletionNotification(completionData);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Interview completion notification sent successfully",
                    "sessionId", completionData.getSessionId(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Failed to send completion notification",
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Error processing completion notification request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Health check endpoint
     * GET /api/email/interview/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "interview-email-service",
            "timestamp", System.currentTimeMillis(),
            "capabilities", java.util.List.of(
                "candidate_invitations",
                "recruiter_notifications", 
                "ai_avatar_notifications",
                "interview_reminders",
                "completion_notifications",
                "calendar_integration",
                "html_templates"
            )
        ));
    }

    /**
     * Bulk send interview notifications (for multiple participants)
     * POST /api/email/interview/bulk-send
     */
    @PostMapping("/bulk-send")
    public ResponseEntity<Map<String, Object>> bulkSendInterviewNotifications(
            @RequestBody Map<String, Object> bulkRequest) {
        try {
            log.info("📨 Processing bulk interview notification request");
            
            String sessionId = (String) bulkRequest.get("sessionId");
            @SuppressWarnings("unchecked")
            Map<String, Object> candidateData = (Map<String, Object>) bulkRequest.get("candidateData");
            @SuppressWarnings("unchecked")
            Map<String, Object> recruiterData = (Map<String, Object>) bulkRequest.get("recruiterData");
            
            int successCount = 0;
            int totalCount = 0;
            
            // Send candidate invitation
            if (candidateData != null) {
                totalCount++;
                InterviewEmailService.CandidateInvitationData candidate = mapToCandidateInvitation(candidateData, sessionId);
                if (emailService.sendCandidateInterviewInvitation(candidate)) {
                    successCount++;
                }
            }
            
            // Send recruiter notification
            if (recruiterData != null) {
                totalCount++;
                InterviewEmailService.RecruiterNotificationData recruiter = mapToRecruiterNotification(recruiterData, sessionId);
                if (emailService.sendRecruiterInterviewNotification(recruiter)) {
                    successCount++;
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "completed",
                "sessionId", sessionId,
                "totalEmails", totalCount,
                "successfulEmails", successCount,
                "failedEmails", totalCount - successCount,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("❌ Error processing bulk notification request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    // ==================== HELPER METHODS ====================

    private InterviewEmailService.CandidateInvitationData mapToCandidateInvitation(Map<String, Object> data, String sessionId) {
        InterviewEmailService.CandidateInvitationData candidate = new InterviewEmailService.CandidateInvitationData();
        candidate.setSessionId(sessionId);
        candidate.setCandidateEmail((String) data.get("email"));
        candidate.setCandidateName((String) data.get("name"));
        candidate.setJobRole((String) data.get("jobRole"));
        // Add other mappings as needed
        return candidate;
    }

    private InterviewEmailService.RecruiterNotificationData mapToRecruiterNotification(Map<String, Object> data, String sessionId) {
        InterviewEmailService.RecruiterNotificationData recruiter = new InterviewEmailService.RecruiterNotificationData();
        recruiter.setSessionId(sessionId);
        recruiter.setRecruiterEmail((String) data.get("email"));
        recruiter.setRecruiterName((String) data.get("name"));
        // Add other mappings as needed
        return recruiter;
    }
}
