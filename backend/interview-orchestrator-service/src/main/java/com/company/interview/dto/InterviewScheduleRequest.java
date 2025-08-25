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
    
    @NotBlank(message = "Candidate name is required")
    private String candidateName;
    
    @NotBlank(message = "Candidate email is required")
    @Email(message = "Candidate email must be valid")
    private String candidateEmail;
    
    @NotNull(message = "Recruiter ID is required")
    private Long recruiterId;
    
    @NotBlank(message = "Recruiter name is required")
    private String recruiterName;
    
    @NotBlank(message = "Recruiter email is required")
    @Email(message = "Recruiter email must be valid")
    private String recruiterEmail;
    
    @NotNull(message = "Scheduled start time is required")
    // Custom validation will be handled in the controller to avoid timezone issues
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
    
    // Job Analysis Integration Fields
    @Size(max = 5000, message = "Job description must not exceed 5000 characters")
    private String jobDescription;
    
    @Size(max = 3000, message = "Key responsibilities must not exceed 3000 characters")
    private String keyResponsibilities;
    
    @Size(max = 1000, message = "Company context must not exceed 1000 characters")
    private String companyContext;
    
    private Boolean enableJobAnalysis = true;
    private Boolean useJobAwareQuestions = true;
    
    // Optional custom settings
    private List<Long> customQuestionPool;
    private Double targetDifficultyRange;
    private Boolean enableBiasDetection = true;
    private Boolean enableCodeChallenges = true;
    private Boolean enableVideoAnalytics = true;
    
    // Job Analysis Getters and Setters
    public String getJobDescription() {
        return jobDescription;
    }
    
    public void setJobDescription(String jobDescription) {
        this.jobDescription = jobDescription;
    }
    
    public String getKeyResponsibilities() {
        return keyResponsibilities;
    }
    
    public void setKeyResponsibilities(String keyResponsibilities) {
        this.keyResponsibilities = keyResponsibilities;
    }
    
    public String getCompanyContext() {
        return companyContext;
    }
    
    public void setCompanyContext(String companyContext) {
        this.companyContext = companyContext;
    }
    
    public Boolean getEnableJobAnalysis() {
        return enableJobAnalysis;
    }
    
    public void setEnableJobAnalysis(Boolean enableJobAnalysis) {
        this.enableJobAnalysis = enableJobAnalysis;
    }
    
    public Boolean getUseJobAwareQuestions() {
        return useJobAwareQuestions;
    }
    
    public void setUseJobAwareQuestions(Boolean useJobAwareQuestions) {
        this.useJobAwareQuestions = useJobAwareQuestions;
    }
    
    // Alias methods for controller compatibility
    public String getPosition() {
        return this.jobRole;
    }
    
    public List<String> getTechnologies() {
        return this.requiredTechnologies;
    }
}
