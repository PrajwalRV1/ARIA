package com.company.interview.service;

import com.company.interview.client.InterviewEmailClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing Jitsi Meet rooms (Free Version - No API Key Required)
 */
@Service
@Slf4j
public class MeetingRoomService {

    @Value("${app.webrtc.jitsi.domain:meet.jit.si}")
    private String jitsiDomain;
    
    @Autowired
    private InterviewEmailClient interviewEmailClient;

    /**
     * Create a Jitsi Meet room for the interview session (FREE - No API key required)
     */
    public Map<String, String> createRoom(String sessionId, String candidateName) {
        try {
            // Generate unique room name for Jitsi Meet
            String roomName = "ARIA-Interview-" + sessionId + "-" + System.currentTimeMillis();
            String roomUrl = "https://" + jitsiDomain + "/" + roomName;
            String roomId = "jitsi-" + UUID.randomUUID().toString();
            
            log.info("Creating FREE Jitsi Meet room for session: {}", sessionId);
            log.info("Room name: {}", roomName);
            log.info("Room URL: {}", roomUrl);
            log.info("Using Jitsi domain: {}", jitsiDomain);
            
            // Jitsi Meet rooms are created instantly - no API call needed!
            // The room exists as soon as someone joins the URL
            
            log.info("✅ Successfully created FREE Jitsi Meet room: {} with URL: {}", roomId, roomUrl);
            
            return Map.of(
                "roomUrl", roomUrl,
                "roomName", roomName,
                "roomId", roomId
            );
            
        } catch (Exception e) {
            log.error("Error creating Jitsi Meet room for session {}: {}", sessionId, e.getMessage(), e);
            // Fallback to basic room
            return createFallbackJitsiRoom(sessionId, candidateName);
        }
    }

    /**
     * Create a fallback Jitsi Meet room
     */
    private Map<String, String> createFallbackJitsiRoom(String sessionId, String candidateName) {
        String roomName = "ARIA-Fallback-" + sessionId;
        String roomUrl = "https://" + jitsiDomain + "/" + roomName;
        String roomId = "jitsi-fallback-" + sessionId;
        
        log.info("Created fallback Jitsi Meet room for session {}: {}", sessionId, roomUrl);
        log.info("Using Jitsi domain: {}", jitsiDomain);
        log.info("✅ FREE Jitsi Meet room ready - No API key required!");
        
        return Map.of(
            "roomUrl", roomUrl,
            "roomName", roomName,
            "roomId", roomId
        );
    }

    /**
     * Share meeting link with participants via comprehensive email service integration
     * FIXED: Check email service availability and provide clear error messages when it fails
     */
    public boolean shareMeetingLink(String sessionId, String meetingLink, List<Map<String, String>> participants) {
        try {
            log.info("🚀 Sharing meeting link for session {} with {} participants via InterviewEmailService", sessionId, participants.size());
            
            // Check if email service is available before attempting to send emails
            if (!isEmailServiceAvailable()) {
                log.error("❌ Email service is not available. Cannot send interview invitations.");
                log.error("❌ Please ensure user-management-service is running on http://localhost:8080");
                return false;
            }
            
            boolean overallSuccess = true;
            int successCount = 0;
            
            for (Map<String, String> participant : participants) {
                String email = participant.get("email");
                String role = participant.get("role");
                String name = participant.get("name");
                
                log.info("📧 Sending comprehensive interview email to {} ({}) for session: {}", email, role, sessionId);
                
                boolean emailSent = false;
                
                if ("candidate".equalsIgnoreCase(role)) {
                    // Send comprehensive candidate invitation with calendar integration
                    emailSent = sendCandidateInvitation(sessionId, participant, meetingLink);
                } else if ("recruiter".equalsIgnoreCase(role)) {
                    // Send recruiter monitoring notification
                    emailSent = sendRecruiterNotification(sessionId, participant, meetingLink);
                } else {
                    // Unknown roles are not supported - fail explicitly
                    log.error("❌ Unknown participant role '{}' for {}. Supported roles: candidate, recruiter", role, email);
                    emailSent = false;
                }
                
                if (emailSent) {
                    successCount++;
                } else {
                    overallSuccess = false;
                    log.error("❌ Failed to send email to {} ({})", email, role);
                }
            }
            
            log.info("📨 Email sending completed: {}/{} successful", successCount, participants.size());
            return overallSuccess && successCount > 0;
            
        } catch (Exception e) {
            log.error("❌ Failed to share meeting link for session {}: {}", sessionId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send comprehensive candidate invitation via InterviewEmailService
     */
    private boolean sendCandidateInvitation(String sessionId, Map<String, String> participantData, String meetingLink) {
        try {
            InterviewEmailClient.CandidateInvitationRequest request = new InterviewEmailClient.CandidateInvitationRequest(
                sessionId,
                participantData.get("email"),
                participantData.getOrDefault("name", "Candidate"),
                participantData.getOrDefault("jobRole", "Software Engineer"),
                LocalDateTime.now().plusMinutes(30), // Default scheduled time (30 mins from now)
                45, // Default duration
                Arrays.asList("JavaScript", "Python", "Java") // Default technologies
            );
            
            return interviewEmailClient.sendCandidateInvitation(request);
            
        } catch (Exception e) {
            log.error("❌ Error sending candidate invitation via InterviewEmailService: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send recruiter monitoring notification via InterviewEmailService
     */
    private boolean sendRecruiterNotification(String sessionId, Map<String, String> participantData, String meetingLink) {
        try {
            InterviewEmailClient.RecruiterNotificationRequest request = new InterviewEmailClient.RecruiterNotificationRequest(
                sessionId,
                participantData.get("email"),
                participantData.getOrDefault("name", "Recruiter"),
                participantData.getOrDefault("candidateName", "Candidate"),
                participantData.getOrDefault("jobRole", "Software Engineer"),
                Integer.parseInt(participantData.getOrDefault("candidateExperience", "3")),
                LocalDateTime.now().plusMinutes(30) // Default scheduled time
            );
            
            return interviewEmailClient.sendRecruiterNotification(request);
            
        } catch (Exception e) {
            log.error("❌ Error sending recruiter notification via InterviewEmailService: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Fallback email method for unknown roles (simplified implementation)
     */
    private void sendMeetingInviteFallback(String email, String role, String meetingLink, String sessionId) {
        log.info("📧 FALLBACK EMAIL TO: {}", email);
        log.info("📧 SUBJECT: Interview Session Invitation - {}", sessionId);
        log.info("📧 BODY: Hello! You've been invited to join an interview session as {}.", role);
        log.info("📧 Meeting Link: {}", meetingLink);
        log.info("📧 Please join the session at the scheduled time.");
        log.info("💡 For production, implement proper email service integration for role: {}", role);
    }

    /**
     * Send AI Avatar activation notification
     */
    public boolean sendAIAvatarActivation(String sessionId, String monitoringEmail) {
        try {
            return interviewEmailClient.sendAIAvatarActivation(
                sessionId, 
                monitoringEmail, 
                "ACTIVE", 
                "ARIA-GPT-4"
            );
        } catch (Exception e) {
            log.error("❌ Error sending AI Avatar activation notification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Delete a Jitsi Meet room (cleanup after interview)
     * Note: Jitsi Meet rooms are automatically cleaned up - no API call needed
     */
    public boolean deleteRoom(String roomName) {
        try {
            log.info("✅ Jitsi Meet room cleanup for: {} - No action needed (auto-cleanup)", roomName);
            log.info("💡 Jitsi Meet rooms are automatically cleaned up when empty");
            return true;
            
        } catch (Exception e) {
            log.error("Error during Jitsi Meet room cleanup for {}: {}", roomName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get room information for Jitsi Meet
     */
    public Map<String, Object> getRoomInfo(String roomName) {
        try {
            // Jitsi Meet rooms don't have a REST API to check status
            // But we can provide basic room info
            return Map.of(
                "name", roomName,
                "url", "https://" + jitsiDomain + "/" + roomName,
                "status", "available",
                "provider", "jitsi-meet-free",
                "features", List.of("video", "audio", "chat", "screen-sharing", "recording")
            );
            
        } catch (Exception e) {
            log.error("Error getting Jitsi Meet room info for {}: {}", roomName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check if the email service (user-management-service) is available
     * Uses a simple connectivity check since the health endpoint may require authentication
     */
    private boolean isEmailServiceAvailable() {
        try {
            // Try to connect to the service port to check basic connectivity
            java.net.Socket socket = new java.net.Socket();
            java.net.SocketAddress socketAddress = new java.net.InetSocketAddress("localhost", 8080);
            
            socket.connect(socketAddress, 2000); // 2 second timeout
            socket.close();
            
            log.debug("Email service is available - port 8080 is accessible");
            return true;
            
        } catch (Exception e) {
            log.debug("Email service is not available - port 8080 is not accessible: {}", e.getMessage());
            return false;
        }
    }
}
