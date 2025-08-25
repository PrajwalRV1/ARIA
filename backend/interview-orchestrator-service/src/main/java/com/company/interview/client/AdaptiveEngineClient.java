package com.company.interview.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client service for communicating with the Python-based Adaptive Question Engine
 * Implements IRT-based question selection and bias detection integration
 */
@Service
public class AdaptiveEngineClient {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveEngineClient.class);

    @Value("${app.services.adaptive-engine.url:http://localhost:8001}")
    private String adaptiveEngineUrl;

    @Value("${app.services.adaptive-engine.timeout:15s}")
    private Duration timeout;

    private final RestTemplate restTemplate;

    public AdaptiveEngineClient(@Qualifier("adaptiveEngineRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        // Configure timeout
        this.restTemplate.getMessageConverters().forEach(converter -> {
            if (converter instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter) {
                ((org.springframework.http.converter.json.MappingJackson2HttpMessageConverter) converter)
                        .getObjectMapper().configure(
                        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            }
        });
    }

    /**
     * Get the next optimal question for a candidate based on their current ability estimate
     */
    @Retryable(
            value = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Optional<QuestionResponse> getNextQuestion(QuestionRequest request) {
        try {
            logger.debug("Requesting next question for session: {}, theta: {}", 
                    request.getSessionId(), request.getCurrentTheta());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "ARIA-Interview-Orchestrator/1.0");

            HttpEntity<QuestionRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<QuestionResponse> response = restTemplate.postForEntity(
                    adaptiveEngineUrl + "/next-question", 
                    entity, 
                    QuestionResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                QuestionResponse questionResponse = response.getBody();
                logger.info("Selected question {} for session {} with confidence {}", 
                        questionResponse.getQuestionId(), request.getSessionId(), 
                        questionResponse.getConfidenceScore());
                return Optional.of(questionResponse);
            }

            logger.warn("No suitable question found for session: {}", request.getSessionId());
            return Optional.empty();

        } catch (HttpClientErrorException.NotFound e) {
            logger.warn("No questions available for session {}: {}", request.getSessionId(), e.getMessage());
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            logger.error("Client error requesting question for session {}: {} - {}", 
                    request.getSessionId(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new AdaptiveEngineException("Failed to get next question", e);
        } catch (HttpServerErrorException e) {
            logger.error("Server error requesting question for session {}: {}", 
                    request.getSessionId(), e.getStatusCode());
            throw new AdaptiveEngineException("Adaptive engine server error", e);
        } catch (ResourceAccessException e) {
            logger.error("Network error connecting to adaptive engine for session {}: {}", 
                    request.getSessionId(), e.getMessage());
            throw new AdaptiveEngineException("Failed to connect to adaptive engine", e);
        } catch (Exception e) {
            logger.error("Unexpected error requesting question for session {}: {}", 
                    request.getSessionId(), e.getMessage());
            throw new AdaptiveEngineException("Unexpected error in adaptive engine", e);
        }
    }

    /**
     * Update candidate's theta (ability estimate) based on their response
     */
    @Retryable(
            value = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Optional<ThetaUpdateResponse> updateTheta(ThetaUpdateRequest request) {
        try {
            logger.debug("Updating theta for session: {}, question: {}", 
                    request.getSessionId(), request.getQuestionId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ThetaUpdateRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ThetaUpdateResponse> response = restTemplate.postForEntity(
                    adaptiveEngineUrl + "/update-theta", 
                    entity, 
                    ThetaUpdateResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ThetaUpdateResponse updateResponse = response.getBody();
                logger.info("Updated theta for session {} from {} to {} (change: {})", 
                        request.getSessionId(), request.getCurrentTheta(), 
                        updateResponse.getNewTheta(), updateResponse.getThetaChange());
                return Optional.of(updateResponse);
            }

            return Optional.empty();

        } catch (HttpClientErrorException e) {
            logger.error("Client error updating theta for session {}: {} - {}", 
                    request.getSessionId(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new AdaptiveEngineException("Failed to update theta", e);
        } catch (HttpServerErrorException e) {
            logger.error("Server error updating theta for session {}: {}", 
                    request.getSessionId(), e.getStatusCode());
            throw new AdaptiveEngineException("Adaptive engine server error", e);
        } catch (ResourceAccessException e) {
            logger.error("Network error updating theta for session {}: {}", 
                    request.getSessionId(), e.getMessage());
            throw new AdaptiveEngineException("Failed to connect to adaptive engine", e);
        }
    }

    /**
     * Submit learning data after interview completion
     */
    public void submitLearningData(LearningUpdateRequest request) {
        try {
            logger.debug("Submitting learning data for session: {}", request.getSessionId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<LearningUpdateRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    adaptiveEngineUrl + "/learning/update", 
                    entity, 
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Successfully submitted learning data for session: {}", request.getSessionId());
            }

        } catch (Exception e) {
            logger.error("Error submitting learning data for session {}: {}", 
                    request.getSessionId(), e.getMessage());
            // Don't throw exception - learning updates are not critical
        }
    }

    /**
     * Get analytics for a session
     */
    public Optional<Map<String, Object>> getSessionAnalytics(String sessionId) {
        try {
            logger.debug("Requesting session analytics for: {}", sessionId);

            ResponseEntity<Map> response = restTemplate.getForEntity(
                    adaptiveEngineUrl + "/analytics/session/" + sessionId, 
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return Optional.of((Map<String, Object>) response.getBody());
            }

            return Optional.empty();

        } catch (HttpClientErrorException.NotFound e) {
            logger.info("No analytics found for session: {}", sessionId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error getting session analytics for {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get the next job-aware question using enhanced job context
     */
    @Retryable(
            value = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Optional<QuestionResponse> getNextJobAwareQuestion(JobAwareQuestionRequest request) {
        try {
            logger.debug("Requesting job-aware question for session: {}, theta: {}", 
                    request.getSessionId(), request.getCurrentTheta());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "ARIA-Interview-Orchestrator/1.0");

            HttpEntity<JobAwareQuestionRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<QuestionResponse> response = restTemplate.postForEntity(
                    adaptiveEngineUrl + "/next-question-job-aware", 
                    entity, 
                    QuestionResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                QuestionResponse questionResponse = response.getBody();
                logger.info("Selected job-aware question {} for session {} with confidence {}", 
                        questionResponse.getQuestionId(), request.getSessionId(), 
                        questionResponse.getConfidenceScore());
                return Optional.of(questionResponse);
            }

            logger.warn("No suitable job-aware question found for session: {}", request.getSessionId());
            return Optional.empty();

        } catch (HttpClientErrorException.NotFound e) {
            logger.warn("No job-aware questions available for session {}: {}", request.getSessionId(), e.getMessage());
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            logger.error("Client error requesting job-aware question for session {}: {} - {}", 
                    request.getSessionId(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new AdaptiveEngineException("Failed to get next job-aware question", e);
        } catch (HttpServerErrorException e) {
            logger.error("Server error requesting job-aware question for session {}: {}", 
                    request.getSessionId(), e.getStatusCode());
            throw new AdaptiveEngineException("Adaptive engine server error", e);
        } catch (ResourceAccessException e) {
            logger.error("Network error connecting to adaptive engine for session {}: {}", 
                    request.getSessionId(), e.getMessage());
            throw new AdaptiveEngineException("Failed to connect to adaptive engine", e);
        } catch (Exception e) {
            logger.error("Unexpected error requesting job-aware question for session {}: {}", 
                    request.getSessionId(), e.getMessage());
            throw new AdaptiveEngineException("Unexpected error in adaptive engine", e);
        }
    }
    
    /**
     * Get job-based interview preview
     */
    public Optional<Map<String, Object>> getInterviewPreview(JobAwareQuestionRequest request) {
        try {
            logger.debug("Requesting interview preview for session: {}", request.getSessionId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<JobAwareQuestionRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    adaptiveEngineUrl + "/preview-interview", 
                    entity, 
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> preview = (Map<String, Object>) response.getBody();
                return Optional.of(preview);
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.warn("Failed to get interview preview for session {}: {}", 
                    request.getSessionId(), e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Analyze job for interview setup
     */
    public Optional<Map<String, Object>> analyzeJobForInterview(JobAnalysisRequest request) {
        try {
            logger.debug("Analyzing job for interview setup: {}", request.getJobRole());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<JobAnalysisRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    adaptiveEngineUrl + "/analyze-job", 
                    entity, 
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> analysis = (Map<String, Object>) response.getBody();
                return Optional.of(analysis);
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.warn("Failed to analyze job for interview: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Check if the adaptive engine service is available
     */
    public boolean isServiceAvailable() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    adaptiveEngineUrl + "/health", 
                    Map.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            logger.debug("Adaptive engine service is not available: {}", e.getMessage());
            return false;
        }
    }

    // DTO Classes
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobAnalysisRequest {
        @JsonProperty("job_description")
        private String jobDescription;
        
        @JsonProperty("key_responsibilities")
        private String keyResponsibilities;
        
        @JsonProperty("job_role")
        private String jobRole;
        
        @JsonProperty("company_context")
        private String companyContext;
        
        // Constructors
        public JobAnalysisRequest() {}
        
        // Getters and setters
        public String getJobDescription() { return jobDescription; }
        public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
        
        public String getKeyResponsibilities() { return keyResponsibilities; }
        public void setKeyResponsibilities(String keyResponsibilities) { this.keyResponsibilities = keyResponsibilities; }
        
        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }
        
        public String getCompanyContext() { return companyContext; }
        public void setCompanyContext(String companyContext) { this.companyContext = companyContext; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobAwareQuestionRequest extends QuestionRequest {
        @JsonProperty("job_description")
        private String jobDescription;
        
        @JsonProperty("key_responsibilities")
        private String keyResponsibilities;
        
        @JsonProperty("company_context")
        private String companyContext;
        
        // Constructors
        public JobAwareQuestionRequest() {}
        
        public JobAwareQuestionRequest(String sessionId, Integer candidateId, String jobRole, String experienceLevel) {
            super(sessionId, candidateId, jobRole, experienceLevel);
        }
        
        // Getters and setters
        public String getJobDescription() { return jobDescription; }
        public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
        
        public String getKeyResponsibilities() { return keyResponsibilities; }
        public void setKeyResponsibilities(String keyResponsibilities) { this.keyResponsibilities = keyResponsibilities; }
        
        public String getCompanyContext() { return companyContext; }
        public void setCompanyContext(String companyContext) { this.companyContext = companyContext; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionRequest {
        @JsonProperty("session_id")
        private String sessionId;
        
        @JsonProperty("candidate_id")
        private Integer candidateId;
        
        @JsonProperty("current_theta")
        private Double currentTheta = 0.0;
        
        @JsonProperty("standard_error")
        private Double standardError = 1.0;
        
        @JsonProperty("answered_questions")
        private List<Integer> answeredQuestions;
        
        @JsonProperty("job_role")
        private String jobRole;
        
        @JsonProperty("experience_level")
        private String experienceLevel;
        
        private List<String> technologies;
        
        @JsonProperty("min_difficulty")
        private Double minDifficulty = -3.0;
        
        @JsonProperty("max_difficulty")
        private Double maxDifficulty = 3.0;
        
        @JsonProperty("question_type")
        private String questionType;

        // Constructors
        public QuestionRequest() {}

        public QuestionRequest(String sessionId, Integer candidateId, String jobRole, String experienceLevel) {
            this.sessionId = sessionId;
            this.candidateId = candidateId;
            this.jobRole = jobRole;
            this.experienceLevel = experienceLevel;
        }

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public Integer getCandidateId() { return candidateId; }
        public void setCandidateId(Integer candidateId) { this.candidateId = candidateId; }

        public Double getCurrentTheta() { return currentTheta; }
        public void setCurrentTheta(Double currentTheta) { this.currentTheta = currentTheta; }

        public Double getStandardError() { return standardError; }
        public void setStandardError(Double standardError) { this.standardError = standardError; }

        public List<Integer> getAnsweredQuestions() { return answeredQuestions; }
        public void setAnsweredQuestions(List<Integer> answeredQuestions) { this.answeredQuestions = answeredQuestions; }

        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }

        public String getExperienceLevel() { return experienceLevel; }
        public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

        public List<String> getTechnologies() { return technologies; }
        public void setTechnologies(List<String> technologies) { this.technologies = technologies; }

        public Double getMinDifficulty() { return minDifficulty; }
        public void setMinDifficulty(Double minDifficulty) { this.minDifficulty = minDifficulty; }

        public Double getMaxDifficulty() { return maxDifficulty; }
        public void setMaxDifficulty(Double maxDifficulty) { this.maxDifficulty = maxDifficulty; }

        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionResponse {
        @JsonProperty("question_id")
        private Integer questionId;
        
        @JsonProperty("question_text")
        private String questionText;
        
        @JsonProperty("question_type")
        private String questionType;
        
        private Double difficulty;
        private Double discrimination;
        private String category;
        private List<String> technologies;
        
        @JsonProperty("expected_duration_minutes")
        private Integer expectedDurationMinutes;
        
        @JsonProperty("coding_required")
        private Boolean codingRequired;
        
        @JsonProperty("multi_part")
        private Boolean multiPart;
        
        @JsonProperty("followup_questions")
        private List<Integer> followupQuestions;
        
        @JsonProperty("confidence_score")
        private Double confidenceScore;
        
        @JsonProperty("selection_reason")
        private String selectionReason;

        // Getters and setters
        public Integer getQuestionId() { return questionId; }
        public void setQuestionId(Integer questionId) { this.questionId = questionId; }

        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }

        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }

        public Double getDifficulty() { return difficulty; }
        public void setDifficulty(Double difficulty) { this.difficulty = difficulty; }

        public Double getDiscrimination() { return discrimination; }
        public void setDiscrimination(Double discrimination) { this.discrimination = discrimination; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public List<String> getTechnologies() { return technologies; }
        public void setTechnologies(List<String> technologies) { this.technologies = technologies; }

        public Integer getExpectedDurationMinutes() { return expectedDurationMinutes; }
        public void setExpectedDurationMinutes(Integer expectedDurationMinutes) { 
            this.expectedDurationMinutes = expectedDurationMinutes; 
        }

        public Boolean getCodingRequired() { return codingRequired; }
        public void setCodingRequired(Boolean codingRequired) { this.codingRequired = codingRequired; }

        public Boolean getMultiPart() { return multiPart; }
        public void setMultiPart(Boolean multiPart) { this.multiPart = multiPart; }

        public List<Integer> getFollowupQuestions() { return followupQuestions; }
        public void setFollowupQuestions(List<Integer> followupQuestions) { this.followupQuestions = followupQuestions; }

        public Double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

        public String getSelectionReason() { return selectionReason; }
        public void setSelectionReason(String selectionReason) { this.selectionReason = selectionReason; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThetaUpdateRequest {
        @JsonProperty("session_id")
        private String sessionId;
        
        @JsonProperty("candidate_id")
        private Integer candidateId;
        
        @JsonProperty("question_id")
        private Integer questionId;
        
        @JsonProperty("response_data")
        private Map<String, Object> responseData;
        
        @JsonProperty("current_theta")
        private Double currentTheta;
        
        @JsonProperty("current_se")
        private Double currentSe;
        
        @JsonProperty("partial_credit")
        private Double partialCredit;
        
        @JsonProperty("response_time_seconds")
        private Integer responseTimeSeconds;

        // Constructors
        public ThetaUpdateRequest() {}

        public ThetaUpdateRequest(String sessionId, Integer candidateId, Integer questionId, 
                                 Map<String, Object> responseData, Double currentTheta, Double currentSe) {
            this.sessionId = sessionId;
            this.candidateId = candidateId;
            this.questionId = questionId;
            this.responseData = responseData;
            this.currentTheta = currentTheta;
            this.currentSe = currentSe;
        }

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public Integer getCandidateId() { return candidateId; }
        public void setCandidateId(Integer candidateId) { this.candidateId = candidateId; }

        public Integer getQuestionId() { return questionId; }
        public void setQuestionId(Integer questionId) { this.questionId = questionId; }

        public Map<String, Object> getResponseData() { return responseData; }
        public void setResponseData(Map<String, Object> responseData) { this.responseData = responseData; }

        public Double getCurrentTheta() { return currentTheta; }
        public void setCurrentTheta(Double currentTheta) { this.currentTheta = currentTheta; }

        public Double getCurrentSe() { return currentSe; }
        public void setCurrentSe(Double currentSe) { this.currentSe = currentSe; }

        public Double getPartialCredit() { return partialCredit; }
        public void setPartialCredit(Double partialCredit) { this.partialCredit = partialCredit; }

        public Integer getResponseTimeSeconds() { return responseTimeSeconds; }
        public void setResponseTimeSeconds(Integer responseTimeSeconds) { 
            this.responseTimeSeconds = responseTimeSeconds; 
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThetaUpdateResponse {
        @JsonProperty("new_theta")
        private Double newTheta;
        
        @JsonProperty("new_standard_error")
        private Double newStandardError;
        
        @JsonProperty("theta_change")
        private Double thetaChange;
        
        @JsonProperty("confidence_level")
        private Double confidenceLevel;
        
        @JsonProperty("termination_recommended")
        private Boolean terminationRecommended;
        
        @JsonProperty("next_difficulty_range")
        private Map<String, Double> nextDifficultyRange;
        
        @JsonProperty("bias_flag")
        private Boolean biasFlag = false;
        
        @JsonProperty("bias_details")
        private Map<String, Object> biasDetails;

        // Getters and setters
        public Double getNewTheta() { return newTheta; }
        public void setNewTheta(Double newTheta) { this.newTheta = newTheta; }

        public Double getNewStandardError() { return newStandardError; }
        public void setNewStandardError(Double newStandardError) { this.newStandardError = newStandardError; }

        public Double getThetaChange() { return thetaChange; }
        public void setThetaChange(Double thetaChange) { this.thetaChange = thetaChange; }

        public Double getConfidenceLevel() { return confidenceLevel; }
        public void setConfidenceLevel(Double confidenceLevel) { this.confidenceLevel = confidenceLevel; }

        public Boolean getTerminationRecommended() { return terminationRecommended; }
        public void setTerminationRecommended(Boolean terminationRecommended) { 
            this.terminationRecommended = terminationRecommended; 
        }

        public Map<String, Double> getNextDifficultyRange() { return nextDifficultyRange; }
        public void setNextDifficultyRange(Map<String, Double> nextDifficultyRange) { 
            this.nextDifficultyRange = nextDifficultyRange; 
        }

        public Boolean getBiasFlag() { return biasFlag; }
        public void setBiasFlag(Boolean biasFlag) { this.biasFlag = biasFlag; }

        public Map<String, Object> getBiasDetails() { return biasDetails; }
        public void setBiasDetails(Map<String, Object> biasDetails) { this.biasDetails = biasDetails; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LearningUpdateRequest {
        @JsonProperty("session_id")
        private String sessionId;
        
        @JsonProperty("interview_outcome")
        private Map<String, Object> interviewOutcome;
        
        @JsonProperty("question_effectiveness")
        private List<Map<String, Object>> questionEffectiveness;
        
        @JsonProperty("bias_incidents")
        private List<Map<String, Object>> biasIncidents;
        
        @JsonProperty("conversation_patterns")
        private List<Map<String, Object>> conversationPatterns;

        // Constructors
        public LearningUpdateRequest() {}

        public LearningUpdateRequest(String sessionId, Map<String, Object> interviewOutcome) {
            this.sessionId = sessionId;
            this.interviewOutcome = interviewOutcome;
        }

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public Map<String, Object> getInterviewOutcome() { return interviewOutcome; }
        public void setInterviewOutcome(Map<String, Object> interviewOutcome) { 
            this.interviewOutcome = interviewOutcome; 
        }

        public List<Map<String, Object>> getQuestionEffectiveness() { return questionEffectiveness; }
        public void setQuestionEffectiveness(List<Map<String, Object>> questionEffectiveness) { 
            this.questionEffectiveness = questionEffectiveness; 
        }

        public List<Map<String, Object>> getBiasIncidents() { return biasIncidents; }
        public void setBiasIncidents(List<Map<String, Object>> biasIncidents) { 
            this.biasIncidents = biasIncidents; 
        }

        public List<Map<String, Object>> getConversationPatterns() { return conversationPatterns; }
        public void setConversationPatterns(List<Map<String, Object>> conversationPatterns) { 
            this.conversationPatterns = conversationPatterns; 
        }
    }

    /**
     * Custom exception for adaptive engine communication errors
     */
    public static class AdaptiveEngineException extends RuntimeException {
        public AdaptiveEngineException(String message) {
            super(message);
        }

        public AdaptiveEngineException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
