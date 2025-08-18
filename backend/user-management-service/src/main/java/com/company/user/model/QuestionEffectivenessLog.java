package com.company.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity to track question effectiveness and performance metrics over time
 * Used for continuous learning and IRT parameter calibration
 */
@Entity
@Table(name = "question_effectiveness_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionEffectivenessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @NotNull(message = "Question is required")
    private Question question;

    @Column(name = "interview_session_id", nullable = false)
    @NotNull(message = "Interview session ID is required")
    private String interviewSessionId;

    @Column(name = "candidate_id", nullable = false)
    @NotNull(message = "Candidate ID is required")
    private Long candidateId;

    @Column(name = "candidate_theta_before", nullable = false)
    @DecimalMin(value = "-4.0", message = "Theta before must be at least -4.0")
    @DecimalMax(value = "4.0", message = "Theta before must not exceed 4.0")
    private Double candidateThetaBefore;

    @Column(name = "candidate_theta_after", nullable = false)
    @DecimalMin(value = "-4.0", message = "Theta after must be at least -4.0")
    @DecimalMax(value = "4.0", message = "Theta after must not exceed 4.0")
    private Double candidateThetaAfter;

    @Column(name = "standard_error_before", nullable = false)
    @Min(value = 0, message = "Standard error before cannot be negative")
    private Double standardErrorBefore;

    @Column(name = "standard_error_after", nullable = false)
    @Min(value = 0, message = "Standard error after cannot be negative")
    private Double standardErrorAfter;

    // Response Data
    @Column(name = "response_correct", nullable = false)
    @NotNull(message = "Response correctness is required")
    private Boolean responseCorrect;

    @Column(name = "response_time", nullable = false)
    @Min(value = 0, message = "Response time cannot be negative")
    private Double responseTime; // in seconds

    @Column(name = "candidate_answer", columnDefinition = "TEXT")
    private String candidateAnswer;

    @Column(name = "expected_answer", columnDefinition = "TEXT")
    private String expectedAnswer;

    @Column(name = "partial_credit_score")
    @DecimalMin(value = "0.0", message = "Partial credit score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Partial credit score must not exceed 1.0")
    private Double partialCreditScore; // For coding and open-ended questions

    // IRT Parameters at Time of Question
    @Column(name = "discrimination_parameter_used", nullable = false)
    private Double discriminationParameterUsed;

    @Column(name = "difficulty_parameter_used", nullable = false)
    private Double difficultyParameterUsed;

    @Column(name = "guessing_parameter_used", nullable = false)
    private Double guessingParameterUsed;

    @Column(name = "upper_asymptote_used", nullable = false)
    private Double upperAsymptoteUsed;

    // Information Theory Metrics
    @Column(name = "information_provided", nullable = false)
    @Min(value = 0, message = "Information provided cannot be negative")
    private Double informationProvided; // Fisher information contributed

    @Column(name = "expected_information", nullable = false)
    @Min(value = 0, message = "Expected information cannot be negative")
    private Double expectedInformation; // Expected Fisher information

    @Column(name = "information_efficiency", nullable = false)
    @DecimalMin(value = "0.0", message = "Information efficiency must be at least 0.0")
    @DecimalMax(value = "2.0", message = "Information efficiency should not exceed 2.0")
    private Double informationEfficiency; // Actual / Expected information

    // Prediction Accuracy
    @Column(name = "predicted_probability", nullable = false)
    @DecimalMin(value = "0.0", message = "Predicted probability must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Predicted probability must not exceed 1.0")
    private Double predictedProbability; // IRT model prediction

    @Column(name = "prediction_error", nullable = false)
    private Double predictionError; // |actual - predicted|

    @Column(name = "log_likelihood", nullable = false)
    private Double logLikelihood; // Log-likelihood contribution

    // Bias and Fairness Metrics
    @Column(name = "bias_indicators", columnDefinition = "TEXT")
    private String biasIndicators; // JSON format: {"demographic": "value", "score": 0.1}

    @Column(name = "demographic_group")
    private String demographicGroup; // For bias analysis (anonymized)

    @Column(name = "expected_score_demographic")
    private Double expectedScoreDemographic; // Expected score for demographic group

    @Column(name = "actual_score_demographic")
    private Double actualScoreDemographic; // Actual score for demographic group

    // Context and Environment
    @Column(name = "question_position", nullable = false)
    @Min(value = 1, message = "Question position must be positive")
    private Integer questionPosition; // Position in interview sequence

    @Column(name = "interview_stage")
    private String interviewStage; // screening, technical, final, etc.

    @Column(name = "job_role")
    private String jobRole;

    @Column(name = "technologies_assessed", columnDefinition = "TEXT")
    private String technologiesAssessed; // JSON array

    @Column(name = "interviewer_id")
    private Long interviewerId; // For human-in-the-loop scenarios

    // Performance Indicators
    @Column(name = "engagement_score")
    @DecimalMin(value = "0.0", message = "Engagement score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Engagement score must not exceed 1.0")
    private Double engagementScore; // Candidate engagement during question

    @Column(name = "confidence_score")
    @DecimalMin(value = "0.0", message = "Confidence score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Confidence score must not exceed 1.0")
    private Double confidenceScore; // Candidate confidence in response

    @Column(name = "difficulty_perception")
    @DecimalMin(value = "1.0", message = "Difficulty perception must be at least 1.0")
    @DecimalMax(value = "5.0", message = "Difficulty perception must not exceed 5.0")
    private Double difficultyPerception; // Candidate's perceived difficulty (1-5 scale)

    // Adaptive Algorithm Metrics
    @Column(name = "selection_algorithm")
    private String selectionAlgorithm; // Which algorithm selected this question

    @Column(name = "selection_score", nullable = false)
    private Double selectionScore; // Score that led to question selection

    @Column(name = "competing_questions", columnDefinition = "TEXT")
    private String competingQuestions; // JSON array of other candidate questions

    @Column(name = "selection_reasons", columnDefinition = "TEXT")
    private String selectionReasons; // JSON array of selection criteria

    // Quality Metrics
    @Column(name = "question_clarity_score")
    @DecimalMin(value = "0.0", message = "Question clarity score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Question clarity score must not exceed 1.0")
    private Double questionClarityScore; // How clear was the question to candidate

    @Column(name = "answer_quality_score")
    @DecimalMin(value = "0.0", message = "Answer quality score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Answer quality score must not exceed 1.0")
    private Double answerQualityScore; // Quality of candidate's answer

    @Column(name = "technical_accuracy_score")
    @DecimalMin(value = "0.0", message = "Technical accuracy score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Technical accuracy score must not exceed 1.0")
    private Double technicalAccuracyScore; // Technical accuracy of answer

    // Metadata
    @Column(name = "question_version")
    private String questionVersion; // Version of question used

    @Column(name = "system_version")
    private String systemVersion; // Version of ARIA system

    @Column(name = "additional_metrics", columnDefinition = "TEXT")
    private String additionalMetrics; // JSON format for extensible metrics

    @CreationTimestamp
    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt;

    // Calculated metrics methods
    public Double getInformationGain() {
        return Math.abs(standardErrorBefore - standardErrorAfter);
    }

    public Double getThetaChange() {
        return Math.abs(candidateThetaAfter - candidateThetaBefore);
    }

    public Double getPredictionAccuracy() {
        return 1.0 - predictionError;
    }

    public Boolean isUnexpectedResponse() {
        // Flag responses that deviate significantly from prediction
        return predictionError > 0.5;
    }

    public Boolean isBiasIndicatorPresent() {
        return biasIndicators != null && !biasIndicators.isEmpty();
    }

    public Boolean isHighInformationQuestion() {
        return informationProvided > 1.0;
    }

    public Boolean isLowEngagementResponse() {
        return engagementScore != null && engagementScore < 0.3;
    }

    // Static factory methods
    public static QuestionEffectivenessLog createFromResponse(
            Question question, String sessionId, Long candidateId,
            Double thetaBefore, Double thetaAfter,
            Double seBefore, Double seAfter,
            Boolean correct, Double responseTime) {
        
        QuestionEffectivenessLog log = new QuestionEffectivenessLog();
        log.setQuestion(question);
        log.setInterviewSessionId(sessionId);
        log.setCandidateId(candidateId);
        log.setCandidateThetaBefore(thetaBefore);
        log.setCandidateThetaAfter(thetaAfter);
        log.setStandardErrorBefore(seBefore);
        log.setStandardErrorAfter(seAfter);
        log.setResponseCorrect(correct);
        log.setResponseTime(responseTime);
        
        // Copy current IRT parameters
        log.setDiscriminationParameterUsed(question.getDiscriminationParameter());
        log.setDifficultyParameterUsed(question.getDifficultyParameter());
        log.setGuessingParameterUsed(question.getGuessingParameter());
        log.setUpperAsymptoteUsed(question.getUpperAsymptote());
        
        // Calculate information metrics
        Double expectedInfo = question.getInformationAtTheta(thetaBefore);
        Double actualInfo = calculateActualInformation(thetaBefore, thetaAfter, seBefore, seAfter);
        log.setExpectedInformation(expectedInfo);
        log.setInformationProvided(actualInfo);
        log.setInformationEfficiency(expectedInfo > 0 ? actualInfo / expectedInfo : 0.0);
        
        // Calculate prediction metrics
        Double predictedProb = question.getProbabilityCorrect(thetaBefore);
        log.setPredictedProbability(predictedProb);
        log.setPredictionError(Math.abs((correct ? 1.0 : 0.0) - predictedProb));
        
        // Calculate log-likelihood
        Double logLike = correct ? Math.log(predictedProb) : Math.log(1.0 - predictedProb);
        log.setLogLikelihood(logLike);
        
        return log;
    }

    private static Double calculateActualInformation(Double thetaBefore, Double thetaAfter, 
                                                   Double seBefore, Double seAfter) {
        // Information is inversely related to variance (square of standard error)
        Double varianceBefore = seBefore * seBefore;
        Double varianceAfter = seAfter * seAfter;
        return varianceBefore - varianceAfter; // Information gained
    }

    // Update methods for additional metrics
    public void updateBiasMetrics(String demographicGroup, Double expectedScore, 
                                 Double actualScore, Map<String, Object> biasIndicators) {
        this.demographicGroup = demographicGroup;
        this.expectedScoreDemographic = expectedScore;
        this.actualScoreDemographic = actualScore;
        // Convert biasIndicators map to JSON string
        // Implementation would use Jackson ObjectMapper
    }

    public void updateEngagementMetrics(Double engagement, Double confidence, 
                                      Double perceivedDifficulty) {
        this.engagementScore = Math.max(0.0, Math.min(1.0, engagement));
        this.confidenceScore = Math.max(0.0, Math.min(1.0, confidence));
        this.difficultyPerception = Math.max(1.0, Math.min(5.0, perceivedDifficulty));
    }

    public void updateQualityMetrics(Double clarity, Double answerQuality, 
                                   Double technicalAccuracy) {
        this.questionClarityScore = Math.max(0.0, Math.min(1.0, clarity));
        this.answerQualityScore = Math.max(0.0, Math.min(1.0, answerQuality));
        this.technicalAccuracyScore = Math.max(0.0, Math.min(1.0, technicalAccuracy));
    }

    public void updateAdaptiveMetrics(String algorithm, Double selectionScore, 
                                    String[] competingQuestions, String[] reasons) {
        this.selectionAlgorithm = algorithm;
        this.selectionScore = selectionScore;
        // Convert arrays to JSON strings
        // Implementation would use Jackson ObjectMapper
    }
}
