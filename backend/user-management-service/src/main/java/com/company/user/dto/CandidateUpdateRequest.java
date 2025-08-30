package com.company.user.dto;

import com.company.user.model.CandidateStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for updating an existing candidate with comprehensive validation.
 * ID is set from path variable in controller.
 */
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@ToString
public class CandidateUpdateRequest {
    
    // ID is set from path variable in controller
    private Long id;
    
    @NotBlank(message = "Requisition ID is required")
    @Length(max = 255, message = "Requisition ID must not exceed 255 characters")
    private String requisitionId;
    
    @NotBlank(message = "Candidate name is required")
    @Length(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Length(max = 255, message = "Email must not exceed 255 characters")
    private String email;
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number must be 10-15 digits")
    private String phone;
    
    @Length(max = 255, message = "Applied role must not exceed 255 characters")
    private String appliedRole;
    
    @PastOrPresent(message = "Application date cannot be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applicationDate;
    
    @DecimalMin(value = "0.0", message = "Total experience cannot be negative")
    @DecimalMax(value = "50.0", message = "Total experience cannot exceed 50 years")
    private Double totalExperience;
    
    @DecimalMin(value = "0.0", message = "Relevant experience cannot be negative")
    @DecimalMax(value = "50.0", message = "Relevant experience cannot exceed 50 years")
    private Double relevantExperience;
    
    @Length(max = 100, message = "Interview round must not exceed 100 characters")
    private String interviewRound;
    
    @NotNull(message = "Candidate status is required")
    private CandidateStatus status;
    
    @Length(max = 5000, message = "Job description must not exceed 5000 characters")
    private String jobDescription;
    
    @Length(max = 5000, message = "Key responsibilities must not exceed 5000 characters")
    private String keyResponsibilities;
    
    @Size(max = 20, message = "Maximum 20 skills allowed")
    private List<@NotBlank @Length(max = 100, message = "Skill name too long") String> skills;
    
    @Length(max = 100, message = "Source must not exceed 100 characters")
    private String source;
    
    @Length(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
    
    @Length(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;
    
    @Length(max = 100, message = "Recruiter ID must not exceed 100 characters")
    private String recruiterId;
    
    /**
     * Validate that the current status can transition to the new status
     */
    public void validateStatusTransition(CandidateStatus currentStatus) {
        if (currentStatus == null || status == null) {
            return;
        }
        
        // Define valid status transitions (same as in Candidate entity)
        boolean validTransition = switch (currentStatus) {
            case PENDING -> status == CandidateStatus.APPLIED || 
                           status == CandidateStatus.REJECTED ||
                           status == CandidateStatus.WITHDRAWN;
                           
            case APPLIED -> status == CandidateStatus.INTERVIEW_SCHEDULED ||
                           status == CandidateStatus.REJECTED ||
                           status == CandidateStatus.ON_HOLD ||
                           status == CandidateStatus.WITHDRAWN;
                           
            case INTERVIEW_SCHEDULED -> status == CandidateStatus.IN_PROGRESS ||
                                      status == CandidateStatus.COMPLETED ||
                                      status == CandidateStatus.REJECTED ||
                                      status == CandidateStatus.ON_HOLD ||
                                      status == CandidateStatus.WITHDRAWN;
                                      
            case IN_PROGRESS -> status == CandidateStatus.COMPLETED ||
                              status == CandidateStatus.REJECTED ||
                              status == CandidateStatus.ON_HOLD;
                              
            case COMPLETED -> status == CandidateStatus.UNDER_REVIEW;
            
            case UNDER_REVIEW -> status == CandidateStatus.SELECTED ||
                               status == CandidateStatus.REJECTED ||
                               status == CandidateStatus.ON_HOLD;
                               
            case ON_HOLD -> status == CandidateStatus.APPLIED ||
                          status == CandidateStatus.INTERVIEW_SCHEDULED ||
                          status == CandidateStatus.REJECTED ||
                          status == CandidateStatus.WITHDRAWN;
                          
            case SELECTED, REJECTED, WITHDRAWN -> false; // Terminal states
        };
        
        if (!validTransition) {
            throw new IllegalArgumentException(
                String.format("Invalid status transition from %s to %s", currentStatus, status)
            );
        }
    }
}
