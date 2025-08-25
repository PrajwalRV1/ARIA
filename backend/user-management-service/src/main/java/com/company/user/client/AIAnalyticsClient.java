package com.company.user.client;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client service for communicating with AI Analytics Service (FastAPI)
 */
@Service
public class AIAnalyticsClient {

    private static final Logger logger = LoggerFactory.getLogger(AIAnalyticsClient.class);
    private final RestTemplate restTemplate;

    public AIAnalyticsClient(@Qualifier("aiAnalyticsRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Get user analytics summary
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Optional<UserAnalytics> getUserAnalytics(Long userId) {
        try {
            logger.debug("Fetching analytics for user: {}", userId);
            
            String url = "/analytics/user/{userId}";
            var response = restTemplate.getForEntity(url, UserAnalytics.class, userId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully retrieved analytics for user: {}", userId);
                return Optional.of(response.getBody());
            }
            
            return Optional.empty();
            
        } catch (HttpClientErrorException.NotFound e) {
            logger.info("No analytics found for user: {}", userId);
            return Optional.empty();
            
        } catch (HttpClientErrorException e) {
            logger.error("Client error while fetching analytics for user {}: {} - {}", 
                userId, e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
            
        } catch (HttpServerErrorException e) {
            logger.error("Server error while fetching analytics for user {}: {}", 
                userId, e.getStatusCode());
            throw new ServiceCommunicationException("AI Analytics service error", e);
            
        } catch (ResourceAccessException e) {
            logger.error("Network error while fetching analytics for user {}: {}", 
                userId, e.getMessage());
            throw new ServiceCommunicationException("Failed to connect to AI Analytics service", e);
        }
    }

    /**
     * Analyze interview performance
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 2,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public Optional<InterviewAnalysis> analyzeInterviewPerformance(String sessionId, AnalysisRequest request) {
        try {
            logger.debug("Analyzing interview performance for session: {}", sessionId);
            
            String url = "/analytics/interviews/{sessionId}/analyze";
            var response = restTemplate.postForEntity(url, request, InterviewAnalysis.class, sessionId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Successfully analyzed interview performance for session: {}", sessionId);
                return Optional.of(response.getBody());
            }
            
            return Optional.empty();
            
        } catch (HttpClientErrorException e) {
            logger.error("Client error while analyzing interview performance: {} - {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error analyzing interview performance for session {}: {}", sessionId, e.getMessage());
            throw new ServiceCommunicationException("Failed to analyze interview performance", e);
        }
    }

    /**
     * Get bias detection results
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 2,
        backoff = @Backoff(delay = 1000)
    )
    public Optional<BiasDetectionResult> detectBias(String sessionId, BiasDetectionRequest request) {
        try {
            logger.debug("Running bias detection for session: {}", sessionId);
            
            String url = "/analytics/bias-detection/{sessionId}";
            var response = restTemplate.postForEntity(url, request, BiasDetectionResult.class, sessionId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully completed bias detection for session: {}", sessionId);
                return Optional.of(response.getBody());
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error in bias detection for session {}: {}", sessionId, e.getMessage());
            // Don't throw exception for bias detection - it's not critical for user flow
            return Optional.empty();
        }
    }

    /**
     * Get emotional analysis
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 2,
        backoff = @Backoff(delay = 1000)
    )
    public Optional<EmotionalAnalysis> analyzeEmotions(String sessionId, EmotionalAnalysisRequest request) {
        try {
            logger.debug("Analyzing emotions for session: {}", sessionId);
            
            String url = "/analytics/emotions/{sessionId}";
            var response = restTemplate.postForEntity(url, request, EmotionalAnalysis.class, sessionId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error in emotional analysis for session {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Check if AI Analytics service is available
     */
    public boolean isServiceAvailable() {
        try {
            String url = "/health";
            var response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            logger.debug("AI Analytics service is not available: {}", e.getMessage());
            return false;
        }
    }

    // DTOs for AI Analytics Service communication

    public static class UserAnalytics {
        private Long userId;
        private int totalInterviews;
        private double averageScore;
        private Map<String, Double> skillsAssessment;
        private List<String> strengths;
        private List<String> improvementAreas;
        private String overallRating;

        // Constructors
        public UserAnalytics() {}

        // Getters and setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public int getTotalInterviews() { return totalInterviews; }
        public void setTotalInterviews(int totalInterviews) { this.totalInterviews = totalInterviews; }

        public double getAverageScore() { return averageScore; }
        public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

        public Map<String, Double> getSkillsAssessment() { return skillsAssessment; }
        public void setSkillsAssessment(Map<String, Double> skillsAssessment) { this.skillsAssessment = skillsAssessment; }

        public List<String> getStrengths() { return strengths; }
        public void setStrengths(List<String> strengths) { this.strengths = strengths; }

        public List<String> getImprovementAreas() { return improvementAreas; }
        public void setImprovementAreas(List<String> improvementAreas) { this.improvementAreas = improvementAreas; }

        public String getOverallRating() { return overallRating; }
        public void setOverallRating(String overallRating) { this.overallRating = overallRating; }
    }

    public static class AnalysisRequest {
        private List<Map<String, Object>> responses;
        private Map<String, Object> videoAnalysis;
        private Map<String, Object> codeAnalysis;
        private String jobRole;
        private List<String> requiredSkills;

        // Constructors
        public AnalysisRequest() {}

        // Getters and setters
        public List<Map<String, Object>> getResponses() { return responses; }
        public void setResponses(List<Map<String, Object>> responses) { this.responses = responses; }

        public Map<String, Object> getVideoAnalysis() { return videoAnalysis; }
        public void setVideoAnalysis(Map<String, Object> videoAnalysis) { this.videoAnalysis = videoAnalysis; }

        public Map<String, Object> getCodeAnalysis() { return codeAnalysis; }
        public void setCodeAnalysis(Map<String, Object> codeAnalysis) { this.codeAnalysis = codeAnalysis; }

        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }

        public List<String> getRequiredSkills() { return requiredSkills; }
        public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }
    }

    public static class InterviewAnalysis {
        private String sessionId;
        private double overallScore;
        private Map<String, Double> categoryScores;
        private Map<String, Double> skillScores;
        private List<String> strengths;
        private List<String> weaknesses;
        private String recommendation;
        private double confidenceLevel;

        // Constructors
        public InterviewAnalysis() {}

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }

        public Map<String, Double> getCategoryScores() { return categoryScores; }
        public void setCategoryScores(Map<String, Double> categoryScores) { this.categoryScores = categoryScores; }

        public Map<String, Double> getSkillScores() { return skillScores; }
        public void setSkillScores(Map<String, Double> skillScores) { this.skillScores = skillScores; }

        public List<String> getStrengths() { return strengths; }
        public void setStrengths(List<String> strengths) { this.strengths = strengths; }

        public List<String> getWeaknesses() { return weaknesses; }
        public void setWeaknesses(List<String> weaknesses) { this.weaknesses = weaknesses; }

        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

        public double getConfidenceLevel() { return confidenceLevel; }
        public void setConfidenceLevel(double confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    }

    public static class BiasDetectionRequest {
        private List<Map<String, Object>> responses;
        private Map<String, Object> candidateProfile;
        private String jobRole;

        // Constructors
        public BiasDetectionRequest() {}

        // Getters and setters
        public List<Map<String, Object>> getResponses() { return responses; }
        public void setResponses(List<Map<String, Object>> responses) { this.responses = responses; }

        public Map<String, Object> getCandidateProfile() { return candidateProfile; }
        public void setCandidateProfile(Map<String, Object> candidateProfile) { this.candidateProfile = candidateProfile; }

        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }
    }

    public static class BiasDetectionResult {
        private String sessionId;
        private boolean biasDetected;
        private List<String> biasTypes;
        private double confidenceScore;
        private Map<String, Double> biasScores;
        private String analysis;

        // Constructors
        public BiasDetectionResult() {}

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public boolean isBiasDetected() { return biasDetected; }
        public void setBiasDetected(boolean biasDetected) { this.biasDetected = biasDetected; }

        public List<String> getBiasTypes() { return biasTypes; }
        public void setBiasTypes(List<String> biasTypes) { this.biasTypes = biasTypes; }

        public double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

        public Map<String, Double> getBiasScores() { return biasScores; }
        public void setBiasScores(Map<String, Double> biasScores) { this.biasScores = biasScores; }

        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }
    }

    public static class EmotionalAnalysisRequest {
        private String videoUrl;
        private String audioUrl;
        private List<String> timestamps;

        // Constructors
        public EmotionalAnalysisRequest() {}

        // Getters and setters
        public String getVideoUrl() { return videoUrl; }
        public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

        public String getAudioUrl() { return audioUrl; }
        public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

        public List<String> getTimestamps() { return timestamps; }
        public void setTimestamps(List<String> timestamps) { this.timestamps = timestamps; }
    }

    public static class EmotionalAnalysis {
        private String sessionId;
        private Map<String, Double> emotions;
        private double engagementLevel;
        private double stressLevel;
        private double confidenceLevel;
        private List<Map<String, Object>> timelineData;

        // Constructors
        public EmotionalAnalysis() {}

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public Map<String, Double> getEmotions() { return emotions; }
        public void setEmotions(Map<String, Double> emotions) { this.emotions = emotions; }

        public double getEngagementLevel() { return engagementLevel; }
        public void setEngagementLevel(double engagementLevel) { this.engagementLevel = engagementLevel; }

        public double getStressLevel() { return stressLevel; }
        public void setStressLevel(double stressLevel) { this.stressLevel = stressLevel; }

        public double getConfidenceLevel() { return confidenceLevel; }
        public void setConfidenceLevel(double confidenceLevel) { this.confidenceLevel = confidenceLevel; }

        public List<Map<String, Object>> getTimelineData() { return timelineData; }
        public void setTimelineData(List<Map<String, Object>> timelineData) { this.timelineData = timelineData; }
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
