package com.company.interview.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for scheduling an interview session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewScheduleRequest {
    
    @NotNull(message = "Candidate ID is required")
    private Long candidateId;
    
    @NotNull(message = "Recruiter ID is required")
    private Long recruiterId;
    
    @NotNull(message = "Scheduled start time is required")
    @Future(message = "Scheduled time must be in the future")
    private LocalDateTime scheduledStartTime;
    
    @NotBlank(message = "Job role is required")
    @Size(max = 100, message = "Job role must not exceed 100 characters")
    private String jobRole;
    
    @NotBlank(message = "Experience level is required")
    @Size(max = 50, message = "Experience level must not exceed 50 characters")
    private String experienceLevel;
    
    @NotEmpty(message = "Required technologies list cannot be empty")
    private List<String> requiredTechnologies;
    
    @Min(value = 5, message = "Minimum questions must be at least 5")
    @Max(value = 50, message = "Minimum questions must not exceed 50")
    private Integer minQuestions = 10;
    
    @Min(value = 10, message = "Maximum questions must be at least 10")
    @Max(value = 100, message = "Maximum questions must not exceed 100")
    private Integer maxQuestions = 30;
    
    @Pattern(regexp = "ADAPTIVE_AI|STRUCTURED|MIXED", 
             message = "Interview type must be ADAPTIVE_AI, STRUCTURED, or MIXED")
    private String interviewType = "ADAPTIVE_AI";
    
    @Pattern(regexp = "en|es|fr|de|it", 
             message = "Language preference must be one of: en, es, fr, de, it")
    private String languagePreference = "en";
    
    // Optional custom settings
    private List<Long> customQuestionPool;
    private Double targetDifficultyRange;
    private Boolean enableBiasDetection = true;
    private Boolean enableCodeChallenges = true;
    private Boolean enableVideoAnalytics = true;
}
