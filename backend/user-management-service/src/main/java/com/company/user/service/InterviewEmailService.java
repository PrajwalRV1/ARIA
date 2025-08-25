package com.company.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Enhanced Email Service for ARIA Interview Notifications
 * Handles all email communications including calendar invites and meeting links
 */
@Service
@Slf4j
public class InterviewEmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:workwithrvprajwal@gmail.com}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${app.support.email:support@ariaa.com}")
    private String supportEmail;

    @Value("${app.company.name:ARIA}")
    private String companyName;

    /**
     * Send comprehensive interview invitation to candidate
     */
    public boolean sendCandidateInterviewInvitation(CandidateInvitationData invitationData) {
        try {
            log.info("📧 Sending candidate interview invitation to: {}", invitationData.getCandidateEmail());
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            // Email headers
            helper.setFrom(fromEmail, companyName + " Interview Team");
            helper.setTo(invitationData.getCandidateEmail());
            helper.setSubject("🎯 Your AI Interview Invitation - " + invitationData.getJobRole() + " Position");
            
            // Generate interview access token
            String interviewToken = generateInterviewToken(invitationData);
            String interviewUrl = buildCandidateInterviewUrl(invitationData.getSessionId(), interviewToken);
            
            // HTML email content
            String htmlContent = buildCandidateInvitationHtml(invitationData, interviewUrl);
            helper.setText(htmlContent, true);
            
            // Generate and attach calendar invite
            byte[] calendarInvite = generateCalendarInvite(invitationData);
            if (calendarInvite != null) {
                helper.addAttachment("interview_invite.ics", 
                    new ByteArrayResource(calendarInvite), "text/calendar");
            }
            
            mailSender.send(message);
            
            log.info("✅ Successfully sent candidate invitation to: {}", invitationData.getCandidateEmail());
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to send candidate invitation to {}: {}", 
                invitationData.getCandidateEmail(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send interview notification to recruiter
     */
    public boolean sendRecruiterInterviewNotification(RecruiterNotificationData notificationData) {
        try {
            log.info("📧 Sending recruiter notification to: {}", notificationData.getRecruiterEmail());
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, companyName + " System");
            helper.setTo(notificationData.getRecruiterEmail());
            helper.setSubject("🎯 Interview Scheduled - " + notificationData.getCandidateName() + 
                " (" + notificationData.getJobRole() + ")");
            
            String monitorUrl = buildRecruiterMonitorUrl(notificationData.getSessionId());
            String htmlContent = buildRecruiterNotificationHtml(notificationData, monitorUrl);
            helper.setText(htmlContent, true);
            
            // Add calendar invite for recruiter
            byte[] calendarInvite = generateRecruiterCalendarInvite(notificationData);
            if (calendarInvite != null) {
                helper.addAttachment("interview_monitoring.ics", 
                    new ByteArrayResource(calendarInvite), "text/calendar");
            }
            
            mailSender.send(message);
            
            log.info("✅ Successfully sent recruiter notification to: {}", notificationData.getRecruiterEmail());
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to send recruiter notification to {}: {}", 
                notificationData.getRecruiterEmail(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send AI Avatar activation notification (for monitoring/debugging)
     */
    public boolean sendAIAvatarActivationNotification(AIAvatarActivationData activationData) {
        try {
            log.info("🤖 Sending AI Avatar activation notification for session: {}", activationData.getSessionId());
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "ARIA AI System");
            helper.setTo(activationData.getMonitoringEmail());
            helper.setSubject("🤖 AI Avatar Activated - Session " + activationData.getSessionId());
            
            String htmlContent = buildAIAvatarActivationHtml(activationData);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            
            log.info("✅ Successfully sent AI Avatar notification");
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to send AI Avatar notification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send interview reminder (24 hours and 1 hour before)
     */
    public boolean sendInterviewReminder(InterviewReminderData reminderData) {
        try {
            log.info("⏰ Sending interview reminder to: {}", reminderData.getEmail());
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, companyName + " Interview Team");
            helper.setTo(reminderData.getEmail());
            helper.setSubject("⏰ Interview Reminder - " + reminderData.getTimeUntil() + " to go!");
            
            String htmlContent = buildReminderHtml(reminderData);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            
            log.info("✅ Successfully sent interview reminder");
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to send interview reminder: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send post-interview completion notification
     */
    public boolean sendInterviewCompletionNotification(InterviewCompletionData completionData) {
        try {
            log.info("🏁 Sending interview completion notification to: {}", completionData.getRecruiterEmail());
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "ARIA AI System");
            helper.setTo(completionData.getRecruiterEmail());
            helper.setSubject("🏁 Interview Completed - " + completionData.getCandidateName());
            
            String reportUrl = buildInterviewReportUrl(completionData.getSessionId());
            String htmlContent = buildCompletionNotificationHtml(completionData, reportUrl);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            
            log.info("✅ Successfully sent interview completion notification");
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to send interview completion notification: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    @Autowired
    private com.company.user.security.EnhancedJwtUtil enhancedJwtUtil;
    
    private String generateInterviewToken(CandidateInvitationData data) {
        // Generate proper JWT token for candidate interview access
        java.util.Map<String, Object> additionalClaims = new java.util.HashMap<>();
        additionalClaims.put("candidateName", data.getCandidateName());
        additionalClaims.put("candidateEmail", data.getCandidateEmail());
        additionalClaims.put("jobRole", data.getJobRole());
        additionalClaims.put("interviewType", "candidate_invitation");
        additionalClaims.put("scheduledTime", data.getScheduledTime().toString());
        
        return enhancedJwtUtil.generateSessionToken(
            "candidate_" + data.getSessionId(), // userId
            "candidate", // userType
            data.getSessionId(), // sessionId
            additionalClaims
        );
    }

    private String buildCandidateInterviewUrl(String sessionId, String token) {
        return frontendUrl + "/interview-room/" + sessionId + "?token=" + token + "&role=candidate";
    }

    private String buildRecruiterMonitorUrl(String sessionId) {
        return frontendUrl + "/interview-room/" + sessionId + "?role=recruiter";
    }

    private String buildInterviewReportUrl(String sessionId) {
        return frontendUrl + "/interview-report/" + sessionId;
    }

    private String buildCandidateInvitationHtml(CandidateInvitationData data, String interviewUrl) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .header { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px; max-width: 600px; margin: 0 auto; }
                    .meeting-info { background: #f8fafc; border-left: 4px solid #4f46e5; padding: 20px; margin: 20px 0; }
                    .cta-button { 
                        display: inline-block; background: #4f46e5; color: white; 
                        padding: 15px 30px; text-decoration: none; border-radius: 8px; 
                        font-weight: bold; margin: 20px 0;
                    }
                    .tech-requirements { background: #fffbeb; border: 1px solid #fbbf24; padding: 15px; border-radius: 6px; }
                    .footer { background: #f1f5f9; padding: 20px; text-align: center; color: #64748b; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>🎯 Your AI Interview Invitation</h1>
                    <p>%s Position at %s</p>
                </div>
                
                <div class="content">
                    <p>Dear %s,</p>
                    
                    <p>Congratulations! You've been selected for an AI-powered interview for the <strong>%s</strong> position. Our advanced ARIA AI system will conduct a comprehensive technical and behavioral assessment.</p>
                    
                    <div class="meeting-info">
                        <h3>📅 Interview Details</h3>
                        <p><strong>Date:</strong> %s</p>
                        <p><strong>Duration:</strong> %d minutes</p>
                        <p><strong>Interview Type:</strong> AI-Powered Technical Assessment</p>
                        <p><strong>Technologies:</strong> %s</p>
                        
                        <a href="%s" class="cta-button">🚀 Join Interview Room</a>
                    </div>
                    
                    <div class="tech-requirements">
                        <h4>💻 Technical Requirements:</h4>
                        <ul>
                            <li>Chrome, Firefox, or Safari browser (latest version)</li>
                            <li>Stable internet connection (minimum 2 Mbps)</li>
                            <li>Working webcam and microphone</li>
                            <li>Quiet environment for the duration of the interview</li>
                        </ul>
                    </div>
                    
                    <h4>🤖 What to Expect:</h4>
                    <ol>
                        <li><strong>Introduction (3 mins):</strong> AI avatar welcomes you and explains the process</li>
                        <li><strong>Technical Questions (20 mins):</strong> Adaptive questions based on your experience</li>
                        <li><strong>Coding Challenges (25 mins):</strong> Live coding with our integrated editor</li>
                        <li><strong>Behavioral Assessment (15 mins):</strong> Situational and culture fit questions</li>
                        <li><strong>Your Questions (5 mins):</strong> Ask about the role and company</li>
                    </ol>
                    
                    <p><strong>📋 Interview ID:</strong> %s</p>
                    <p><strong>🔗 Backup Link:</strong> %s</p>
                    
                    <p>If you encounter any technical issues, please contact our support team at <a href="mailto:%s">%s</a></p>
                    
                    <p>Best of luck with your interview!</p>
                </div>
                
                <div class="footer">
                    <p>This interview is powered by ARIA - Advanced Recruitment Intelligence Assistant</p>
                    <p>© 2024 %s. All rights reserved.</p>
                </div>
            </body>
            </html>
            """, 
            data.getJobRole(), companyName,
            data.getCandidateName(), 
            data.getJobRole(),
            data.getScheduledTime().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")),
            data.getDurationMinutes(),
            String.join(", ", data.getTechnologies()),
            interviewUrl,
            data.getSessionId(),
            interviewUrl,
            supportEmail, supportEmail,
            companyName
        );
    }

    private String buildRecruiterNotificationHtml(RecruiterNotificationData data, String monitorUrl) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .header { background: linear-gradient(135deg, #059669, #0d9488); color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px; max-width: 600px; margin: 0 auto; }
                    .candidate-info { background: #f0fdf4; border-left: 4px solid #059669; padding: 20px; margin: 20px 0; }
                    .monitor-button { 
                        display: inline-block; background: #059669; color: white; 
                        padding: 15px 30px; text-decoration: none; border-radius: 8px; 
                        font-weight: bold; margin: 20px 0;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>👁️ Interview Monitoring Available</h1>
                    <p>AI Interview Session Scheduled</p>
                </div>
                
                <div class="content">
                    <p>Hi %s,</p>
                    
                    <p>An AI interview has been scheduled and is ready for monitoring.</p>
                    
                    <div class="candidate-info">
                        <h3>👤 Candidate Information</h3>
                        <p><strong>Name:</strong> %s</p>
                        <p><strong>Position:</strong> %s</p>
                        <p><strong>Experience:</strong> %d years</p>
                        <p><strong>Interview Time:</strong> %s</p>
                        <p><strong>Session ID:</strong> %s</p>
                        
                        <a href="%s" class="monitor-button">👁️ Monitor Interview</a>
                    </div>
                    
                    <h4>📊 Monitoring Features Available:</h4>
                    <ul>
                        <li>Real-time candidate performance tracking</li>
                        <li>AI avatar interaction controls</li>
                        <li>Live transcription and scoring</li>
                        <li>Bias detection monitoring</li>
                        <li>Technical issue intervention tools</li>
                    </ul>
                    
                    <p>The system will automatically generate a comprehensive report upon completion.</p>
                </div>
            </body>
            </html>
            """, 
            data.getRecruiterName(),
            data.getCandidateName(),
            data.getJobRole(),
            data.getCandidateExperience(),
            data.getScheduledTime().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")),
            data.getSessionId(),
            monitorUrl
        );
    }

    private String buildAIAvatarActivationHtml(AIAvatarActivationData data) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <div style="background: linear-gradient(135deg, #7c3aed, #a855f7); color: white; padding: 20px; text-align: center;">
                    <h1>🤖 AI Avatar Activated</h1>
                </div>
                
                <div style="padding: 30px;">
                    <p><strong>Session ID:</strong> %s</p>
                    <p><strong>Activation Time:</strong> %s</p>
                    <p><strong>Status:</strong> %s</p>
                    <p><strong>AI Model:</strong> %s</p>
                    
                    <h3>🔧 System Status:</h3>
                    <ul>
                        <li>WebRTC Connection: ✅ Established</li>
                        <li>Speech Synthesis: ✅ Ready</li>
                        <li>Question Engine: ✅ Loaded</li>
                        <li>Analytics: ✅ Active</li>
                    </ul>
                </div>
            </body>
            </html>
            """,
            data.getSessionId(),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            data.getStatus(),
            data.getAiModel()
        );
    }

    private String buildReminderHtml(InterviewReminderData data) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <div style="background: linear-gradient(135deg, #f59e0b, #d97706); color: white; padding: 20px; text-align: center;">
                    <h1>⏰ Interview Reminder</h1>
                    <h2>%s to go!</h2>
                </div>
                
                <div style="padding: 30px;">
                    <p>Dear %s,</p>
                    <p>This is a friendly reminder about your upcoming interview.</p>
                    
                    <div style="background: #fef3c7; padding: 15px; border-radius: 6px; margin: 20px 0;">
                        <p><strong>Interview:</strong> %s Position</p>
                        <p><strong>Time:</strong> %s</p>
                        <p><strong>Join Link:</strong> <a href="%s">Click here to join</a></p>
                    </div>
                    
                    <p>Please ensure you're in a quiet environment with good internet connection.</p>
                </div>
            </body>
            </html>
            """,
            data.getTimeUntil(),
            data.getCandidateName(),
            data.getJobRole(),
            data.getScheduledTime().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")),
            data.getInterviewUrl()
        );
    }

    private String buildCompletionNotificationHtml(InterviewCompletionData data, String reportUrl) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <div style="background: linear-gradient(135deg, #059669, #0d9488); color: white; padding: 20px; text-align: center;">
                    <h1>🏁 Interview Completed</h1>
                </div>
                
                <div style="padding: 30px;">
                    <p><strong>Candidate:</strong> %s</p>
                    <p><strong>Position:</strong> %s</p>
                    <p><strong>Duration:</strong> %d minutes</p>
                    <p><strong>Completion Time:</strong> %s</p>
                    
                    <div style="background: #dcfce7; padding: 15px; border-radius: 6px; margin: 20px 0;">
                        <h3>📊 Quick Summary:</h3>
                        <p><strong>Overall Score:</strong> %s/10</p>
                        <p><strong>Questions Answered:</strong> %d</p>
                        <p><strong>Technical Performance:</strong> %s</p>
                    </div>
                    
                    <a href="%s" style="display: inline-block; background: #059669; color: white; padding: 15px 30px; text-decoration: none; border-radius: 8px; font-weight: bold; margin: 20px 0;">📋 View Full Report</a>
                </div>
            </body>
            </html>
            """,
            data.getCandidateName(),
            data.getJobRole(),
            data.getDurationMinutes(),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            data.getOverallScore(),
            data.getQuestionsAnswered(),
            data.getTechnicalPerformance(),
            reportUrl
        );
    }

    private byte[] generateCalendarInvite(CandidateInvitationData data) {
        try {
            String calendarContent = String.format("""
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//ARIA//Interview System//EN
                METHOD:REQUEST
                BEGIN:VEVENT
                UID:%s@ariaa.com
                DTSTAMP:%s
                DTSTART:%s
                DTEND:%s
                SUMMARY:AI Interview - %s Position
                DESCRIPTION:Your AI-powered technical interview for the %s position.\\n\\nJoin URL: %s
                LOCATION:Virtual Interview Room
                ORGANIZER;CN=ARIA Interview System:MAILTO:%s
                ATTENDEE;CN=%s;RSVP=TRUE:MAILTO:%s
                STATUS:CONFIRMED
                TRANSP:OPAQUE
                BEGIN:VALARM
                TRIGGER:-PT15M
                ACTION:DISPLAY
                DESCRIPTION:Interview starting in 15 minutes
                END:VALARM
                END:VEVENT
                END:VCALENDAR
                """,
                data.getSessionId(),
                formatForCalendar(LocalDateTime.now()),
                formatForCalendar(data.getScheduledTime()),
                formatForCalendar(data.getScheduledTime().plusMinutes(data.getDurationMinutes())),
                data.getJobRole(),
                data.getJobRole(),
                buildCandidateInterviewUrl(data.getSessionId(), generateInterviewToken(data)),
                fromEmail,
                data.getCandidateName(),
                data.getCandidateEmail()
            );
            
            return calendarContent.getBytes("UTF-8");
            
        } catch (Exception e) {
            log.error("Failed to generate calendar invite: {}", e.getMessage());
            return null;
        }
    }

    private byte[] generateRecruiterCalendarInvite(RecruiterNotificationData data) {
        try {
            String calendarContent = String.format("""
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//ARIA//Interview System//EN
                METHOD:REQUEST
                BEGIN:VEVENT
                UID:%s-monitor@ariaa.com
                DTSTAMP:%s
                DTSTART:%s
                DTEND:%s
                SUMMARY:Interview Monitoring - %s
                DESCRIPTION:Monitor AI interview session for %s (%s position)\\n\\nMonitor URL: %s
                LOCATION:ARIA Monitoring Dashboard
                ORGANIZER;CN=ARIA System:MAILTO:%s
                ATTENDEE;CN=%s:MAILTO:%s
                STATUS:CONFIRMED
                END:VEVENT
                END:VCALENDAR
                """,
                data.getSessionId(),
                formatForCalendar(LocalDateTime.now()),
                formatForCalendar(data.getScheduledTime()),
                formatForCalendar(data.getScheduledTime().plusMinutes(60)),
                data.getCandidateName(),
                data.getCandidateName(),
                data.getJobRole(),
                buildRecruiterMonitorUrl(data.getSessionId()),
                fromEmail,
                data.getRecruiterName(),
                data.getRecruiterEmail()
            );
            
            return calendarContent.getBytes("UTF-8");
            
        } catch (Exception e) {
            log.error("Failed to generate recruiter calendar invite: {}", e.getMessage());
            return null;
        }
    }

    private String formatForCalendar(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
    }

    // ==================== DATA CLASSES ====================

    public static class CandidateInvitationData {
        private String sessionId;
        private String candidateEmail;
        private String candidateName;
        private String jobRole;
        private LocalDateTime scheduledTime;
        private int durationMinutes;
        private List<String> technologies;

        // Constructors
        public CandidateInvitationData() {}
        
        public CandidateInvitationData(String sessionId, String candidateEmail, String candidateName, 
                String jobRole, LocalDateTime scheduledTime, int durationMinutes, List<String> technologies) {
            this.sessionId = sessionId;
            this.candidateEmail = candidateEmail;
            this.candidateName = candidateName;
            this.jobRole = jobRole;
            this.scheduledTime = scheduledTime;
            this.durationMinutes = durationMinutes;
            this.technologies = technologies;
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getCandidateEmail() { return candidateEmail; }
        public void setCandidateEmail(String candidateEmail) { this.candidateEmail = candidateEmail; }
        public String getCandidateName() { return candidateName; }
        public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }
        public LocalDateTime getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
        public int getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
        public List<String> getTechnologies() { return technologies; }
        public void setTechnologies(List<String> technologies) { this.technologies = technologies; }
    }

    public static class RecruiterNotificationData {
        private String sessionId;
        private String recruiterEmail;
        private String recruiterName;
        private String candidateName;
        private String jobRole;
        private int candidateExperience;
        private LocalDateTime scheduledTime;

        // Constructors
        public RecruiterNotificationData() {}
        
        public RecruiterNotificationData(String sessionId, String recruiterEmail, String recruiterName,
                String candidateName, String jobRole, int candidateExperience, LocalDateTime scheduledTime) {
            this.sessionId = sessionId;
            this.recruiterEmail = recruiterEmail;
            this.recruiterName = recruiterName;
            this.candidateName = candidateName;
            this.jobRole = jobRole;
            this.candidateExperience = candidateExperience;
            this.scheduledTime = scheduledTime;
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getRecruiterEmail() { return recruiterEmail; }
        public void setRecruiterEmail(String recruiterEmail) { this.recruiterEmail = recruiterEmail; }
        public String getRecruiterName() { return recruiterName; }
        public void setRecruiterName(String recruiterName) { this.recruiterName = recruiterName; }
        public String getCandidateName() { return candidateName; }
        public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }
        public int getCandidateExperience() { return candidateExperience; }
        public void setCandidateExperience(int candidateExperience) { this.candidateExperience = candidateExperience; }
        public LocalDateTime getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
    }

    public static class AIAvatarActivationData {
        private String sessionId;
        private String monitoringEmail;
        private String status;
        private String aiModel;

        public AIAvatarActivationData() {}
        
        public AIAvatarActivationData(String sessionId, String monitoringEmail, String status, String aiModel) {
            this.sessionId = sessionId;
            this.monitoringEmail = monitoringEmail;
            this.status = status;
            this.aiModel = aiModel;
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getMonitoringEmail() { return monitoringEmail; }
        public void setMonitoringEmail(String monitoringEmail) { this.monitoringEmail = monitoringEmail; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getAiModel() { return aiModel; }
        public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    }

    public static class InterviewReminderData {
        private String email;
        private String candidateName;
        private String jobRole;
        private LocalDateTime scheduledTime;
        private String timeUntil;
        private String interviewUrl;

        public InterviewReminderData() {}
        
        public InterviewReminderData(String email, String candidateName, String jobRole, 
                LocalDateTime scheduledTime, String timeUntil, String interviewUrl) {
            this.email = email;
            this.candidateName = candidateName;
            this.jobRole = jobRole;
            this.scheduledTime = scheduledTime;
            this.timeUntil = timeUntil;
            this.interviewUrl = interviewUrl;
        }

        // Getters and Setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getCandidateName() { return candidateName; }
        public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }
        public LocalDateTime getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
        public String getTimeUntil() { return timeUntil; }
        public void setTimeUntil(String timeUntil) { this.timeUntil = timeUntil; }
        public String getInterviewUrl() { return interviewUrl; }
        public void setInterviewUrl(String interviewUrl) { this.interviewUrl = interviewUrl; }
    }

    public static class InterviewCompletionData {
        private String sessionId;
        private String recruiterEmail;
        private String candidateName;
        private String jobRole;
        private int durationMinutes;
        private String overallScore;
        private int questionsAnswered;
        private String technicalPerformance;

        public InterviewCompletionData() {}
        
        public InterviewCompletionData(String sessionId, String recruiterEmail, String candidateName,
                String jobRole, int durationMinutes, String overallScore, int questionsAnswered, String technicalPerformance) {
            this.sessionId = sessionId;
            this.recruiterEmail = recruiterEmail;
            this.candidateName = candidateName;
            this.jobRole = jobRole;
            this.durationMinutes = durationMinutes;
            this.overallScore = overallScore;
            this.questionsAnswered = questionsAnswered;
            this.technicalPerformance = technicalPerformance;
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getRecruiterEmail() { return recruiterEmail; }
        public void setRecruiterEmail(String recruiterEmail) { this.recruiterEmail = recruiterEmail; }
        public String getCandidateName() { return candidateName; }
        public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }
        public int getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
        public String getOverallScore() { return overallScore; }
        public void setOverallScore(String overallScore) { this.overallScore = overallScore; }
        public int getQuestionsAnswered() { return questionsAnswered; }
        public void setQuestionsAnswered(int questionsAnswered) { this.questionsAnswered = questionsAnswered; }
        public String getTechnicalPerformance() { return technicalPerformance; }
        public void setTechnicalPerformance(String technicalPerformance) { this.technicalPerformance = technicalPerformance; }
    }
}
