package com.company.interview.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client service for communicating with the Job Description Analyzer service
 * Provides job analysis capabilities for context-aware interview orchestration
 */
@Service
public class JobAnalysisClient {

    private static final Logger logger = LoggerFactory.getLogger(JobAnalysisClient.class);

    @Value("${app.services.job-analyzer.url:http://localhost:8009}")
    private String jobAnalyzerUrl;

    @Value("${app.services.job-analyzer.timeout:10s}")
    private Duration timeout;

    private final RestTemplate restTemplate;

    public JobAnalysisClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Analyze job description to extract key insights for interview customization
     */
    @Retryable(
            value = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Cacheable(value = "jobAnalysis", key = "#request.jobDescription.hashCode() + '_' + #request.keyResponsibilities.hashCode()")
    public Optional<JobAnalysisResponse> analyzeJob(JobAnalysisRequest request) {
        try {
            logger.info("Analyzing job description for role: {}", request.getJobRole());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "ARIA-Interview-Orchestrator/1.0");

            HttpEntity<JobAnalysisRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<JobAnalysisResponse> response = restTemplate.postForEntity(
                    jobAnalyzerUrl + "/analyze",
                    entity,
                    JobAnalysisResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JobAnalysisResponse analysisResponse = response.getBody();
                logger.info("Job analysis completed for role: {} with {} key competencies identified",
                        request.getJobRole(), 
                        analysisResponse.getKeyCompetencies() != null ? analysisResponse.getKeyCompetencies().size() : 0);
                return Optional.of(analysisResponse);
            }

            logger.warn("Job analysis failed for role: {}", request.getJobRole());
            return Optional.empty();

        } catch (HttpClientErrorException e) {
            logger.error("Client error analyzing job for role {}: {} - {}", 
                    request.getJobRole(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new JobAnalysisException("Failed to analyze job description", e);
        } catch (HttpServerErrorException e) {
            logger.error("Server error analyzing job for role {}: {}", 
                    request.getJobRole(), e.getStatusCode());
            throw new JobAnalysisException("Job analyzer server error", e);
        } catch (ResourceAccessException e) {
            logger.error("Network error connecting to job analyzer for role {}: {}", 
                    request.getJobRole(), e.getMessage());
            throw new JobAnalysisException("Failed to connect to job analyzer", e);
        } catch (Exception e) {
            logger.error("Unexpected error analyzing job for role {}: {}", 
                    request.getJobRole(), e.getMessage());
            throw new JobAnalysisException("Unexpected error in job analysis", e);
        }
    }

    /**
     * Get interview preview based on job analysis
     */
    @Retryable(
            value = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 500, multiplier = 1.5)
    )
    public Optional<InterviewPreviewResponse> getInterviewPreview(JobAnalysisRequest request) {
        try {
            logger.debug("Getting interview preview for role: {}", request.getJobRole());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<JobAnalysisRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<InterviewPreviewResponse> response = restTemplate.postForEntity(
                    jobAnalyzerUrl + "/preview-interview",
                    entity,
                    InterviewPreviewResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.debug("Interview preview generated for role: {}", request.getJobRole());
                return Optional.of(response.getBody());
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.warn("Failed to get interview preview for role {}: {}", 
                    request.getJobRole(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get question category weights based on job analysis
     */
    @Cacheable(value = "questionWeights", key = "#jobDescription.hashCode() + '_' + #keyResponsibilities.hashCode()")
    public Optional<Map<String, Double>> getQuestionCategoryWeights(String jobDescription, String keyResponsibilities) {
        try {
            JobAnalysisRequest request = new JobAnalysisRequest();
            request.setJobDescription(jobDescription);
            request.setKeyResponsibilities(keyResponsibilities);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<JobAnalysisRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    jobAnalyzerUrl + "/question-weights",
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Double> weights = (Map<String, Double>) response.getBody();
                return Optional.of(weights);
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.warn("Failed to get question category weights: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Check if the job analyzer service is available
     */
    public boolean isServiceAvailable() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    jobAnalyzerUrl + "/health", 
                    Map.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            logger.debug("Job analyzer service is not available: {}", e.getMessage());
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

        public JobAnalysisRequest(String jobDescription, String keyResponsibilities, String jobRole) {
            this.jobDescription = jobDescription;
            this.keyResponsibilities = keyResponsibilities;
            this.jobRole = jobRole;
        }

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
    public static class JobAnalysisResponse {
        @JsonProperty("key_competencies")
        private List<String> keyCompetencies;

        @JsonProperty("technical_skills")
        private List<String> technicalSkills;

        @JsonProperty("priority_technical_skills")
        private List<String> priorityTechnicalSkills;

        @JsonProperty("soft_skills")
        private List<String> softSkills;

        @JsonProperty("experience_requirements")
        private Map<String, Object> experienceRequirements;

        @JsonProperty("difficulty_adjustment")
        private Map<String, Object> difficultyAdjustment;

        @JsonProperty("question_category_weights")
        private Map<String, Double> questionCategoryWeights;

        @JsonProperty("suggested_interview_duration")
        private Integer suggestedInterviewDuration;

        @JsonProperty("confidence_score")
        private Double confidenceScore;

        @JsonProperty("analysis_metadata")
        private Map<String, Object> analysisMetadata;

        // Getters and setters
        public List<String> getKeyCompetencies() { return keyCompetencies; }
        public void setKeyCompetencies(List<String> keyCompetencies) { this.keyCompetencies = keyCompetencies; }

        public List<String> getTechnicalSkills() { return technicalSkills; }
        public void setTechnicalSkills(List<String> technicalSkills) { this.technicalSkills = technicalSkills; }

        public List<String> getPriorityTechnicalSkills() { return priorityTechnicalSkills; }
        public void setPriorityTechnicalSkills(List<String> priorityTechnicalSkills) { 
            this.priorityTechnicalSkills = priorityTechnicalSkills; 
        }

        public List<String> getSoftSkills() { return softSkills; }
        public void setSoftSkills(List<String> softSkills) { this.softSkills = softSkills; }

        public Map<String, Object> getExperienceRequirements() { return experienceRequirements; }
        public void setExperienceRequirements(Map<String, Object> experienceRequirements) { 
            this.experienceRequirements = experienceRequirements; 
        }

        public Map<String, Object> getDifficultyAdjustment() { return difficultyAdjustment; }
        public void setDifficultyAdjustment(Map<String, Object> difficultyAdjustment) { 
            this.difficultyAdjustment = difficultyAdjustment; 
        }

        public Map<String, Double> getQuestionCategoryWeights() { return questionCategoryWeights; }
        public void setQuestionCategoryWeights(Map<String, Double> questionCategoryWeights) { 
            this.questionCategoryWeights = questionCategoryWeights; 
        }

        public Integer getSuggestedInterviewDuration() { return suggestedInterviewDuration; }
        public void setSuggestedInterviewDuration(Integer suggestedInterviewDuration) { 
            this.suggestedInterviewDuration = suggestedInterviewDuration; 
        }

        public Double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

        public Map<String, Object> getAnalysisMetadata() { return analysisMetadata; }
        public void setAnalysisMetadata(Map<String, Object> analysisMetadata) { 
            this.analysisMetadata = analysisMetadata; 
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InterviewPreviewResponse {
        @JsonProperty("estimated_questions")
        private List<Map<String, Object>> estimatedQuestions;

        @JsonProperty("question_distribution")
        private Map<String, Integer> questionDistribution;

        @JsonProperty("estimated_duration")
        private Integer estimatedDuration;

        @JsonProperty("difficulty_progression")
        private Map<String, Object> difficultyProgression;

        @JsonProperty("skill_coverage")
        private Map<String, Double> skillCoverage;

        @JsonProperty("recommended_initial_theta")
        private Double recommendedInitialTheta;

        // Getters and setters
        public List<Map<String, Object>> getEstimatedQuestions() { return estimatedQuestions; }
        public void setEstimatedQuestions(List<Map<String, Object>> estimatedQuestions) { 
            this.estimatedQuestions = estimatedQuestions; 
        }

        public Map<String, Integer> getQuestionDistribution() { return questionDistribution; }
        public void setQuestionDistribution(Map<String, Integer> questionDistribution) { 
            this.questionDistribution = questionDistribution; 
        }

        public Integer getEstimatedDuration() { return estimatedDuration; }
        public void setEstimatedDuration(Integer estimatedDuration) { this.estimatedDuration = estimatedDuration; }

        public Map<String, Object> getDifficultyProgression() { return difficultyProgression; }
        public void setDifficultyProgression(Map<String, Object> difficultyProgression) { 
            this.difficultyProgression = difficultyProgression; 
        }

        public Map<String, Double> getSkillCoverage() { return skillCoverage; }
        public void setSkillCoverage(Map<String, Double> skillCoverage) { this.skillCoverage = skillCoverage; }

        public Double getRecommendedInitialTheta() { return recommendedInitialTheta; }
        public void setRecommendedInitialTheta(Double recommendedInitialTheta) { 
            this.recommendedInitialTheta = recommendedInitialTheta; 
        }
    }

    /**
     * Custom exception for job analysis communication errors
     */
    public static class JobAnalysisException extends RuntimeException {
        public JobAnalysisException(String message) {
            super(message);
        }

        public JobAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
