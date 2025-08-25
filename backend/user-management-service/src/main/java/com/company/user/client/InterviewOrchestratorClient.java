package com.company.user.client;

import com.company.user.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client service for communicating with Interview Orchestrator Service
 */
@Service
public class InterviewOrchestratorClient {

    private static final Logger logger = LoggerFactory.getLogger(InterviewOrchestratorClient.class);
    private final RestTemplate restTemplate;

    public InterviewOrchestratorClient(@Qualifier("interviewOrchestratorRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Get user's interview sessions
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Optional<List<Map<String, Object>>> getUserInterviewSessions(Long userId) {
        try {
            logger.debug("Fetching interview sessions for user: {}", userId);
            
            String url = "/api/interview/sessions/user/{userId}";
            var response = restTemplate.getForEntity(url, List.class, userId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully retrieved {} interview sessions for user {}", 
                    ((List<?>) response.getBody()).size(), userId);
                return Optional.of((List<Map<String, Object>>) response.getBody());
            }
            
            return Optional.empty();
            
        } catch (HttpClientErrorException.NotFound e) {
            logger.info("No interview sessions found for user: {}", userId);
            return Optional.empty();
            
        } catch (HttpClientErrorException e) {
            logger.error("Client error while fetching interview sessions for user {}: {} - {}", 
                userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServiceCommunicationException("Failed to fetch interview sessions", e);
            
        } catch (HttpServerErrorException e) {
            logger.error("Server error while fetching interview sessions for user {}: {}", 
                userId, e.getStatusCode());
            throw new ServiceCommunicationException("Interview orchestrator service error", e);
            
        } catch (ResourceAccessException e) {
            logger.error("Network error while fetching interview sessions for user {}: {}", 
                userId, e.getMessage());
            throw new ServiceCommunicationException("Failed to connect to interview orchestrator service", e);
        }
    }

    /**
     * Create a new interview session
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Optional<Map<String, Object>> createInterviewSession(InterviewSessionRequest request) {
        try {
            logger.debug("Creating interview session for candidate: {}", request.getCandidateId());
            
            String url = "/api/interview/sessions";
            var response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Successfully created interview session with ID: {}", 
                    response.getBody().get("sessionId"));
                return Optional.of(response.getBody());
            }
            
            return Optional.empty();
            
        } catch (HttpClientErrorException e) {
            logger.error("Client error while creating interview session: {} - {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServiceCommunicationException("Failed to create interview session", e);
            
        } catch (HttpServerErrorException e) {
            logger.error("Server error while creating interview session: {}", e.getStatusCode());
            throw new ServiceCommunicationException("Interview orchestrator service error", e);
            
        } catch (ResourceAccessException e) {
            logger.error("Network error while creating interview session: {}", e.getMessage());
            throw new ServiceCommunicationException("Failed to connect to interview orchestrator service", e);
        }
    }

    /**
     * Update user interview statistics
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 2,
        backoff = @Backoff(delay = 500)
    )
    public void updateUserInterviewStats(Long userId, InterviewStatsUpdate statsUpdate) {
        try {
            logger.debug("Updating interview stats for user: {}", userId);
            
            String url = "/api/interview/users/{userId}/stats";
            restTemplate.put(url, statsUpdate, userId);
            
            logger.debug("Successfully updated interview stats for user: {}", userId);
            
        } catch (HttpClientErrorException e) {
            logger.error("Client error while updating interview stats for user {}: {} - {}", 
                userId, e.getStatusCode(), e.getResponseBodyAsString());
            // Don't throw exception for stats updates - they're not critical
            
        } catch (Exception e) {
            logger.error("Error updating interview stats for user {}: {}", userId, e.getMessage());
            // Don't throw exception for stats updates - they're not critical
        }
    }

    /**
     * Get interview session details
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Optional<Map<String, Object>> getInterviewSession(String sessionId) {
        try {
            logger.debug("Fetching interview session details: {}", sessionId);
            
            String url = "/api/interview/sessions/{sessionId}";
            var response = restTemplate.getForEntity(url, Map.class, sessionId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            
            return Optional.empty();
            
        } catch (HttpClientErrorException.NotFound e) {
            logger.info("Interview session not found: {}", sessionId);
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error fetching interview session {}: {}", sessionId, e.getMessage());
            throw new ServiceCommunicationException("Failed to fetch interview session", e);
        }
    }

    /**
     * Check if interview orchestrator service is available
     */
    public boolean isServiceAvailable() {
        try {
            String url = "/actuator/health";
            var response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            logger.debug("Interview orchestrator service is not available: {}", e.getMessage());
            return false;
        }
    }

    // Inner classes for DTOs
    public static class InterviewSessionRequest {
        private Long candidateId;
        private Long interviewerId;
        private String jobRole;
        private List<String> technologies;
        private String scheduledTime;
        private Integer maxQuestions;
        private Integer minQuestions;

        // Constructors
        public InterviewSessionRequest() {}

        public InterviewSessionRequest(Long candidateId, Long interviewerId, String jobRole) {
            this.candidateId = candidateId;
            this.interviewerId = interviewerId;
            this.jobRole = jobRole;
        }

        // Getters and setters
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }

        public Long getInterviewerId() { return interviewerId; }
        public void setInterviewerId(Long interviewerId) { this.interviewerId = interviewerId; }

        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }

        public List<String> getTechnologies() { return technologies; }
        public void setTechnologies(List<String> technologies) { this.technologies = technologies; }

        public String getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }

        public Integer getMaxQuestions() { return maxQuestions; }
        public void setMaxQuestions(Integer maxQuestions) { this.maxQuestions = maxQuestions; }

        public Integer getMinQuestions() { return minQuestions; }
        public void setMinQuestions(Integer minQuestions) { this.minQuestions = minQuestions; }
    }

    public static class InterviewStatsUpdate {
        private int totalInterviews;
        private int completedInterviews;
        private double averageScore;
        private String lastInterviewDate;

        // Constructors
        public InterviewStatsUpdate() {}

        public InterviewStatsUpdate(int totalInterviews, int completedInterviews, double averageScore) {
            this.totalInterviews = totalInterviews;
            this.completedInterviews = completedInterviews;
            this.averageScore = averageScore;
        }

        // Getters and setters
        public int getTotalInterviews() { return totalInterviews; }
        public void setTotalInterviews(int totalInterviews) { this.totalInterviews = totalInterviews; }

        public int getCompletedInterviews() { return completedInterviews; }
        public void setCompletedInterviews(int completedInterviews) { this.completedInterviews = completedInterviews; }

        public double getAverageScore() { return averageScore; }
        public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

        public String getLastInterviewDate() { return lastInterviewDate; }
        public void setLastInterviewDate(String lastInterviewDate) { this.lastInterviewDate = lastInterviewDate; }
    }

    /**
     * Custom exception for service communication errors
     */
    public static class ServiceCommunicationException extends RuntimeException {
        public ServiceCommunicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
