package com.company.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Question entity with Item Response Theory (IRT) parameters for adaptive interviewing
 */
@Entity
@Table(name = "questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    @NotBlank(message = "Question text is required")
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Question type is required")
    private QuestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Difficulty level is required")
    private DifficultyLevel difficultyLevel;

    @ElementCollection
    @CollectionTable(name = "question_technologies", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "technology")
    private Set<String> technologies;

    @ElementCollection
    @CollectionTable(name = "question_job_roles", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "job_role")
    private Set<String> jobRoles;

    @ElementCollection
    @CollectionTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "tag")
    private Set<String> tags;

    // IRT Parameters
    @Column(name = "discrimination_parameter", nullable = false)
    @DecimalMin(value = "0.1", message = "Discrimination parameter must be at least 0.1")
    @DecimalMax(value = "5.0", message = "Discrimination parameter must not exceed 5.0")
    private Double discriminationParameter = 1.0; // 'a' parameter - item discrimination

    @Column(name = "difficulty_parameter", nullable = false)
    @DecimalMin(value = "-4.0", message = "Difficulty parameter must be at least -4.0")
    @DecimalMax(value = "4.0", message = "Difficulty parameter must not exceed 4.0")
    private Double difficultyParameter = 0.0; // 'b' parameter - item difficulty

    @Column(name = "guessing_parameter", nullable = false)
    @DecimalMin(value = "0.0", message = "Guessing parameter must be at least 0.0")
    @DecimalMax(value = "0.5", message = "Guessing parameter must not exceed 0.5")
    private Double guessingParameter = 0.0; // 'c' parameter - pseudo-guessing

    @Column(name = "upper_asymptote", nullable = false)
    @DecimalMin(value = "0.5", message = "Upper asymptote must be at least 0.5")
    @DecimalMax(value = "1.0", message = "Upper asymptote must not exceed 1.0")
    private Double upperAsymptote = 1.0; // 'd' parameter - upper asymptote

    // Question Effectiveness Metrics
    @Column(name = "times_asked", nullable = false)
    @Min(value = 0, message = "Times asked cannot be negative")
    private Integer timesAsked = 0;

    @Column(name = "times_answered_correctly", nullable = false)
    @Min(value = 0, message = "Times answered correctly cannot be negative")
    private Integer timesAnsweredCorrectly = 0;

    @Column(name = "average_response_time", nullable = false)
    @Min(value = 0, message = "Average response time cannot be negative")
    private Double averageResponseTime = 0.0; // in seconds

    @Column(name = "information_value", nullable = false)
    @Min(value = 0, message = "Information value cannot be negative")
    private Double informationValue = 0.0; // Fisher information

    @Column(name = "bias_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Bias score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Bias score must not exceed 1.0")
    private Double biasScore = 0.0; // 0 = no bias, 1 = high bias

    @Column(name = "engagement_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Engagement score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Engagement score must not exceed 1.0")
    private Double engagementScore = 0.5; // candidate engagement level

    // Content and Options
    @ElementCollection
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_order")
    private List<String> options;

    @ElementCollection
    @CollectionTable(name = "question_correct_answers", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "correct_answer")
    private Set<String> correctAnswers;

    @Column(name = "code_template", columnDefinition = "TEXT")
    private String codeTemplate; // For coding questions

    @Column(name = "test_cases", columnDefinition = "TEXT")
    private String testCases; // JSON format for coding questions

    @Column(name = "expected_keywords", columnDefinition = "TEXT")
    private String expectedKeywords; // JSON array for open-ended questions

    // Adaptive Behavior
    @Column(name = "min_theta_level")
    private Double minThetaLevel = -3.0; // Minimum ability level for this question

    @Column(name = "max_theta_level")
    private Double maxThetaLevel = 3.0; // Maximum ability level for this question

    @Column(name = "target_information")
    private Double targetInformation = 1.0; // Target information value

    @Column(name = "exposure_control")
    private Double exposureControl = 0.8; // Exposure control parameter

    // Status and Metadata
    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Boolean validated = false; // Psychometric validation status

    @Column(name = "validation_sample_size")
    private Integer validationSampleSize = 0;

    @Column(name = "last_calibration_date")
    private LocalDateTime lastCalibrationDate;

    @Column(name = "next_calibration_due")
    private LocalDateTime nextCalibrationDue;

    @Column(nullable = false)
    private Long createdBy; // Recruiter ID who created the question

    @Column(name = "reviewed_by")
    private Long reviewedBy; // Recruiter ID who reviewed the question

    @Column(name = "review_date")
    private LocalDateTime reviewDate;

    @Column(name = "review_comments", columnDefinition = "TEXT")
    private String reviewComments;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Calculated fields
    public Double getSuccessRate() {
        if (timesAsked == 0) return 0.0;
        return (double) timesAnsweredCorrectly / timesAsked;
    }

    public Double getInformationAtTheta(Double theta) {
        // Calculate Fisher information at given theta level
        double p = getProbabilityCorrect(theta);
        double q = 1 - p;
        return discriminationParameter * discriminationParameter * p * q;
    }

    public Double getProbabilityCorrect(Double theta) {
        // 3PL IRT model: P(θ) = c + (d-c) * e^(a(θ-b)) / (1 + e^(a(θ-b)))
        double exponent = discriminationParameter * (theta - difficultyParameter);
        double exponential = Math.exp(exponent);
        return guessingParameter + (upperAsymptote - guessingParameter) * (exponential / (1 + exponential));
    }

    public Boolean isCalibrationNeeded() {
        if (nextCalibrationDue == null) return true;
        return LocalDateTime.now().isAfter(nextCalibrationDue);
    }

    public Boolean hasMinimumSampleSize() {
        return validationSampleSize >= 100; // Minimum sample size for stable IRT parameters
    }

    // Update IRT parameters based on response data
    public void updateIRTParameters(Double newDiscrimination, Double newDifficulty, 
                                   Double newGuessing, Double newUpperAsymptote) {
        this.discriminationParameter = newDiscrimination;
        this.difficultyParameter = newDifficulty;
        this.guessingParameter = newGuessing;
        this.upperAsymptote = newUpperAsymptote;
        this.lastCalibrationDate = LocalDateTime.now();
        this.nextCalibrationDue = LocalDateTime.now().plusMonths(3); // Recalibrate every 3 months
    }

    // Update effectiveness metrics
    public void recordResponse(Boolean correct, Double responseTime) {
        this.timesAsked++;
        if (correct) {
            this.timesAnsweredCorrectly++;
        }
        
        // Update average response time
        this.averageResponseTime = ((this.averageResponseTime * (timesAsked - 1)) + responseTime) / timesAsked;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateBiasScore(Double newBiasScore) {
        this.biasScore = Math.max(0.0, Math.min(1.0, newBiasScore));
    }

    public void updateEngagementScore(Double newEngagementScore) {
        this.engagementScore = Math.max(0.0, Math.min(1.0, newEngagementScore));
    }
}
