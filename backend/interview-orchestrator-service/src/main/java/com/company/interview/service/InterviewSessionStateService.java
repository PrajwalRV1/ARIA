package com.company.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing interview session state in Redis
 */
@Service
public class InterviewSessionStateService {

    private static final Logger logger = LoggerFactory.getLogger(InterviewSessionStateService.class);
    
    // Redis key patterns
    private static final String INTERVIEW_SESSION_PREFIX = "aria:interview:session:";
    private static final String ACTIVE_INTERVIEWS_SET = "aria:active:interviews";
    private static final String USER_ACTIVE_INTERVIEW_PREFIX = "aria:user:active:interview:";
    private static final String SESSION_QUESTIONS_PREFIX = "aria:session:questions:";
    private static final String SESSION_RESPONSES_PREFIX = "aria:session:responses:";
    
    // Session timeouts
    private static final Duration INTERVIEW_SESSION_TIMEOUT = Duration.ofHours(3);
    private static final Duration QUESTION_CACHE_TIMEOUT = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public InterviewSessionStateService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Initialize a new interview session in Redis
     */
    public boolean initializeInterviewSession(String sessionId, InterviewSessionState sessionState) {
        try {
            String sessionKey = INTERVIEW_SESSION_PREFIX + sessionId;
            
            sessionState.setSessionId(sessionId);
            sessionState.setCreatedAt(Instant.now());
            sessionState.setLastUpdatedAt(Instant.now());
            sessionState.setStatus(SessionStatus.INITIALIZED);
            
            // Store session state
            redisTemplate.opsForValue().set(sessionKey, sessionState, INTERVIEW_SESSION_TIMEOUT);
            
            // Add to active interviews set
            redisTemplate.opsForZSet().add(ACTIVE_INTERVIEWS_SET, sessionId, System.currentTimeMillis());
            
            // Set user's active interview
            if (sessionState.getCandidateId() != null) {
                String userInterviewKey = USER_ACTIVE_INTERVIEW_PREFIX + sessionState.getCandidateId();
                redisTemplate.opsForValue().set(userInterviewKey, sessionId, INTERVIEW_SESSION_TIMEOUT);
            }
            
            logger.info("Initialized interview session state: {}", sessionId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to initialize interview session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Get interview session state
     */
    public Optional<InterviewSessionState> getInterviewSessionState(String sessionId) {
        try {
            String sessionKey = INTERVIEW_SESSION_PREFIX + sessionId;
            InterviewSessionState sessionState = (InterviewSessionState) redisTemplate.opsForValue().get(sessionKey);
            
            if (sessionState != null) {
                logger.debug("Retrieved interview session state: {}", sessionId);
                return Optional.of(sessionState);
            }
            
            logger.debug("Interview session state not found: {}", sessionId);
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Failed to retrieve interview session state {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Update interview session state
     */
    public boolean updateInterviewSessionState(String sessionId, InterviewSessionState sessionState) {
        try {
            String sessionKey = INTERVIEW_SESSION_PREFIX + sessionId;
            
            sessionState.setLastUpdatedAt(Instant.now());
            redisTemplate.opsForValue().set(sessionKey, sessionState, INTERVIEW_SESSION_TIMEOUT);
            
            logger.debug("Updated interview session state: {}", sessionId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to update interview session state {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Update session status
     */
    public boolean updateSessionStatus(String sessionId, SessionStatus status) {
        try {
            Optional<InterviewSessionState> stateOpt = getInterviewSessionState(sessionId);
            if (stateOpt.isEmpty()) {
                return false;
            }
            
            InterviewSessionState sessionState = stateOpt.get();
            sessionState.setStatus(status);
            sessionState.setLastUpdatedAt(Instant.now());
            
            if (status == SessionStatus.COMPLETED || status == SessionStatus.TERMINATED) {
                sessionState.setEndedAt(Instant.now());
                
                // Remove from active interviews
                redisTemplate.opsForZSet().remove(ACTIVE_INTERVIEWS_SET, sessionId);
                
                // Clear user's active interview
                if (sessionState.getCandidateId() != null) {
                    String userInterviewKey = USER_ACTIVE_INTERVIEW_PREFIX + sessionState.getCandidateId();
                    redisTemplate.delete(userInterviewKey);
                }
            } else if (status == SessionStatus.IN_PROGRESS && sessionState.getStartedAt() == null) {
                sessionState.setStartedAt(Instant.now());
            }
            
            return updateInterviewSessionState(sessionId, sessionState);
            
        } catch (Exception e) {
            logger.error("Failed to update session status for {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Store current question for session
     */
    public boolean storeCurrentQuestion(String sessionId, QuestionData questionData) {
        try {
            String questionKey = SESSION_QUESTIONS_PREFIX + sessionId + ":current";
            redisTemplate.opsForValue().set(questionKey, questionData, QUESTION_CACHE_TIMEOUT);
            
            // Also add to question history
            String historyKey = SESSION_QUESTIONS_PREFIX + sessionId + ":history";
            redisTemplate.opsForList().rightPush(historyKey, questionData);
            redisTemplate.expire(historyKey, INTERVIEW_SESSION_TIMEOUT);
            
            logger.debug("Stored current question for session: {}", sessionId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to store question for session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Get current question for session
     */
    public Optional<QuestionData> getCurrentQuestion(String sessionId) {
        try {
            String questionKey = SESSION_QUESTIONS_PREFIX + sessionId + ":current";
            QuestionData questionData = (QuestionData) redisTemplate.opsForValue().get(questionKey);
            
            if (questionData != null) {
                logger.debug("Retrieved current question for session: {}", sessionId);
                return Optional.of(questionData);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Failed to retrieve current question for session {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Store response for session
     */
    public boolean storeResponse(String sessionId, ResponseData responseData) {
        try {
            String responseKey = SESSION_RESPONSES_PREFIX + sessionId;
            redisTemplate.opsForList().rightPush(responseKey, responseData);
            redisTemplate.expire(responseKey, INTERVIEW_SESSION_TIMEOUT);
            
            logger.debug("Stored response for session: {}", sessionId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to store response for session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Get user's active interview session
     */
    public Optional<String> getUserActiveInterview(Long userId) {
        try {
            String userInterviewKey = USER_ACTIVE_INTERVIEW_PREFIX + userId;
            String sessionId = (String) redisTemplate.opsForValue().get(userInterviewKey);
            
            if (sessionId != null) {
                // Verify session is still active
                Optional<InterviewSessionState> stateOpt = getInterviewSessionState(sessionId);
                if (stateOpt.isPresent() && 
                    (stateOpt.get().getStatus() == SessionStatus.INITIALIZED || 
                     stateOpt.get().getStatus() == SessionStatus.IN_PROGRESS)) {
                    return Optional.of(sessionId);
                } else {
                    // Clean up stale reference
                    redisTemplate.delete(userInterviewKey);
                }
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Failed to get active interview for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Clean up session data
     */
    public void cleanupSession(String sessionId) {
        try {
            // Remove from active sessions
            redisTemplate.opsForZSet().remove(ACTIVE_INTERVIEWS_SET, sessionId);
            
            // Clean up related keys
            redisTemplate.delete(SESSION_QUESTIONS_PREFIX + sessionId + ":current");
            redisTemplate.delete(SESSION_QUESTIONS_PREFIX + sessionId + ":history");
            redisTemplate.delete(SESSION_RESPONSES_PREFIX + sessionId);
            
            logger.debug("Cleaned up session data for: {}", sessionId);
            
        } catch (Exception e) {
            logger.error("Failed to cleanup session {}: {}", sessionId, e.getMessage());
        }
    }

    // Data classes

    public static class InterviewSessionState {
        private String sessionId;
        private Long candidateId;
        private Long interviewerId;
        private SessionStatus status;
        private String jobRole;
        private List<String> requiredSkills;
        private Map<String, Object> configuration;
        private Instant createdAt;
        private Instant startedAt;
        private Instant endedAt;
        private Instant lastUpdatedAt;
        private int currentQuestionIndex;
        private double currentTheta; // IRT parameter
        private double currentStandardError; // IRT parameter

        // Constructors
        public InterviewSessionState() {}

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }

        public Long getInterviewerId() { return interviewerId; }
        public void setInterviewerId(Long interviewerId) { this.interviewerId = interviewerId; }

        public SessionStatus getStatus() { return status; }
        public void setStatus(SessionStatus status) { this.status = status; }

        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }

        public List<String> getRequiredSkills() { return requiredSkills; }
        public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }

        public Map<String, Object> getConfiguration() { return configuration; }
        public void setConfiguration(Map<String, Object> configuration) { this.configuration = configuration; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

        public Instant getStartedAt() { return startedAt; }
        public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

        public Instant getEndedAt() { return endedAt; }
        public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

        public Instant getLastUpdatedAt() { return lastUpdatedAt; }
        public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

        public int getCurrentQuestionIndex() { return currentQuestionIndex; }
        public void setCurrentQuestionIndex(int currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }

        public double getCurrentTheta() { return currentTheta; }
        public void setCurrentTheta(double currentTheta) { this.currentTheta = currentTheta; }

        public double getCurrentStandardError() { return currentStandardError; }
        public void setCurrentStandardError(double currentStandardError) { this.currentStandardError = currentStandardError; }
    }

    public static class QuestionData {
        private String questionId;
        private String questionText;
        private String questionType;
        private double difficulty;
        private String category;
        private List<String> options;
        private String codeTemplate;
        private Map<String, Object> metadata;
        private Instant presentedAt;

        // Constructors
        public QuestionData() {}

        // Getters and setters
        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }

        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }

        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }

        public double getDifficulty() { return difficulty; }
        public void setDifficulty(double difficulty) { this.difficulty = difficulty; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }

        public String getCodeTemplate() { return codeTemplate; }
        public void setCodeTemplate(String codeTemplate) { this.codeTemplate = codeTemplate; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

        public Instant getPresentedAt() { return presentedAt; }
        public void setPresentedAt(Instant presentedAt) { this.presentedAt = presentedAt; }
    }

    public static class ResponseData {
        private String questionId;
        private String response;
        private String responseType;
        private Instant submittedAt;
        private Duration responseTime;
        private Map<String, Object> metadata;

        // Constructors
        public ResponseData() {}

        // Getters and setters
        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }

        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }

        public String getResponseType() { return responseType; }
        public void setResponseType(String responseType) { this.responseType = responseType; }

        public Instant getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

        public Duration getResponseTime() { return responseTime; }
        public void setResponseTime(Duration responseTime) { this.responseTime = responseTime; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public enum SessionStatus {
        INITIALIZED,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        TERMINATED,
        ERROR
    }
}
