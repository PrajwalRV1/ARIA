package com.company.interview.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Client for integrating with InterviewEmailService 
 * Bridges the gap between Interview Orchestrator and Email Service
 */
@Component
@Slf4j
public class InterviewEmailClient {

    private final RestTemplate restTemplate;
    
    @Value("${app.services.user-management.url:http://localhost:8080}")
    private String userManagementUrl;
    
    @Value("${app.services.internal.api-key:ARIA_INTERNAL_SERVICE_KEY_2024}")
    private String internalApiKey;

    public InterviewEmailClient() {
        this.restTemplate = createSSLIgnoreRestTemplate();
    }
    
    /**
     * Create a RestTemplate that accepts self-signed SSL certificates for localhost development
     */
    private RestTemplate createSSLIgnoreRestTemplate() {
        try {
            // Create a trust manager that accepts all certificates
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
            
            // Create SSL context that uses the trust manager
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // Create hostname verifier that accepts all hostnames
            javax.net.ssl.HostnameVerifier hostnameVerifier = (hostname, session) -> true;
            
            // Set default SSL socket factory and hostname verifier
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
            
            // Create HTTP client factory with SSL context  
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                    if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                        ((javax.net.ssl.HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
                        ((javax.net.ssl.HttpsURLConnection) connection).setHostnameVerifier(hostnameVerifier);
                    }
                    super.prepareConnection(connection, httpMethod);
                }
            };
            
            // Configure timeouts
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(10000);
            
            RestTemplate template = new RestTemplate(factory);
            
            log.info("✅ RestTemplate configured to accept self-signed SSL certificates for localhost development");
            return template;
            
        } catch (Exception e) {
            log.error("❌ Failed to configure SSL-ignoring RestTemplate, using default: {}", e.getMessage(), e);
            return new RestTemplate();
        }
    }
    
    /**
     * Create HTTP headers with service authentication for internal API calls
     */
    private HttpHeaders createServiceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Add service-to-service authentication header
        headers.set("X-Internal-Service", "interview-orchestrator");
        headers.set("X-Internal-API-Key", internalApiKey);
        return headers;
    }

    /**
     * Send comprehensive interview invitation to candidate
     */
    public boolean sendCandidateInvitation(CandidateInvitationRequest request) {
        try {
            log.info("📧 Sending candidate invitation via InterviewEmailService to: {}", request.getCandidateEmail());
            
            String url = userManagementUrl + "/email/interview/candidate-invitation";
            
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<CandidateInvitationRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Candidate invitation sent successfully to: {}", request.getCandidateEmail());
                return true;
            } else {
                log.error("❌ Failed to send candidate invitation. Status: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Error sending candidate invitation to {}: {}", request.getCandidateEmail(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send recruiter notification with monitoring capabilities
     */
    public boolean sendRecruiterNotification(RecruiterNotificationRequest request) {
        try {
            log.info("📧 Sending recruiter notification via InterviewEmailService to: {}", request.getRecruiterEmail());
            
            String url = userManagementUrl + "/email/interview/recruiter-notification";
            
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<RecruiterNotificationRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Recruiter notification sent successfully to: {}", request.getRecruiterEmail());
                return true;
            } else {
                log.error("❌ Failed to send recruiter notification. Status: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Error sending recruiter notification to {}: {}", request.getRecruiterEmail(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send AI Avatar activation notification
     */
    public boolean sendAIAvatarActivation(String sessionId, String monitoringEmail, String status, String aiModel) {
        try {
            log.info("🤖 Sending AI Avatar activation notification for session: {}", sessionId);
            
            String url = userManagementUrl + "/email/interview/ai-avatar-activation";
            
            Map<String, Object> request = Map.of(
                "sessionId", sessionId,
                "monitoringEmail", monitoringEmail,
                "status", status,
                "aiModel", aiModel
            );
            
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (Exception e) {
            log.error("❌ Error sending AI Avatar activation notification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send interview completion notification with report
     */
    public boolean sendInterviewCompletion(InterviewCompletionRequest request) {
        try {
            log.info("🏁 Sending interview completion notification for session: {}", request.getSessionId());
            
            String url = userManagementUrl + "/email/interview/completion";
            
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<InterviewCompletionRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (Exception e) {
            log.error("❌ Error sending completion notification: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== REQUEST DATA CLASSES ====================

    public static class CandidateInvitationRequest {
        private String sessionId;
        private String candidateEmail;
        private String candidateName;
        private String jobRole;
        private LocalDateTime scheduledTime;
        private int durationMinutes;
        private List<String> technologies;

        // Constructors
        public CandidateInvitationRequest() {}
        
        public CandidateInvitationRequest(String sessionId, String candidateEmail, String candidateName, 
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

    public static class RecruiterNotificationRequest {
        private String sessionId;
        private String recruiterEmail;
        private String recruiterName;
        private String candidateName;
        private String jobRole;
        private int candidateExperience;
        private LocalDateTime scheduledTime;

        // Constructors
        public RecruiterNotificationRequest() {}
        
        public RecruiterNotificationRequest(String sessionId, String recruiterEmail, String recruiterName,
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

    public static class InterviewCompletionRequest {
        private String sessionId;
        private String recruiterEmail;
        private String candidateName;
        private String jobRole;
        private int durationMinutes;
        private String overallScore;
        private int questionsAnswered;
        private String technicalPerformance;

        // Constructors
        public InterviewCompletionRequest() {}
        
        public InterviewCompletionRequest(String sessionId, String recruiterEmail, String candidateName,
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
