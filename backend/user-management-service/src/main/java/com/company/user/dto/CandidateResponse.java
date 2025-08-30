package com.company.user.dto;

import com.company.user.model.Candidate;
import com.company.user.model.CandidateStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for candidate data with comprehensive field mapping.
 * Includes metadata and computed fields for frontend consumption.
 */
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandidateResponse {
    
    // === CORE FIELDS ===
    private Long id;
    private String requisitionId;
    private String name;
    private String email;
    private String phone;
    private String appliedRole;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applicationDate;
    
    private Double totalExperience;
    private Double relevantExperience;
    private String interviewRound;
    private CandidateStatus status;
    
    // === JOB DETAILS ===
    private String jobDescription;
    private String keyResponsibilities;
    private List<String> skills;
    
    // === FILE METADATA ===
    private String resumeUrl;
    private String resumeFileName;
    private Long resumeSize;
    private boolean hasResume;
    
    private String profilePicUrl;
    private String profilePicFileName;
    private Long profilePicSize;
    private boolean hasProfilePicture;
    
    private String audioUrl;
    private String audioFilename;
    private Long audioSize;
    private boolean hasAudioFile;
    
    // === METADATA ===
    private String source;
    private String notes;
    private String tags;
    private String recruiterId;
    
    // === AUDIT FIELDS ===
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime updatedAt;
    
    // === COMPUTED FIELDS ===
    private String displayName;
    private List<CandidateStatus> nextPossibleStatuses;
    private String statusDisplayName;
    private boolean isTerminalStatus;
    
    // === HELPER METHODS ===
    
    /**
     * Create CandidateResponse from Candidate entity
     */
    public static CandidateResponse from(Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        
        CandidateResponseBuilder builder = CandidateResponse.builder()
                .id(candidate.getId())
                .requisitionId(candidate.getRequisitionId())
                .name(candidate.getName())
                .email(candidate.getEmail())
                .phone(candidate.getPhone())
                .appliedRole(candidate.getAppliedRole())
                .applicationDate(candidate.getApplicationDate())
                .totalExperience(candidate.getTotalExperience())
                .relevantExperience(candidate.getRelevantExperience())
                .interviewRound(candidate.getInterviewRound())
                .status(candidate.getStatus())
                .jobDescription(candidate.getJobDescription())
                .keyResponsibilities(candidate.getKeyResponsibilities())
                .skills(candidate.getSkills())
                .resumeUrl(candidate.getResumeUrl())
                .resumeFileName(candidate.getResumeFileName())
                .resumeSize(candidate.getResumeSize())
                .hasResume(candidate.hasResume())
                .profilePicUrl(candidate.getProfilePicUrl())
                .profilePicFileName(candidate.getProfilePicFileName())
                .profilePicSize(candidate.getProfilePicSize())
                .hasProfilePicture(candidate.hasProfilePicture())
                .audioUrl(candidate.getAudioUrl())
                .audioFilename(candidate.getAudioFilename())
                .audioSize(candidate.getAudioSize())
                .hasAudioFile(candidate.hasAudioFile())
                .source(candidate.getSource())
                .notes(candidate.getNotes())
                .tags(candidate.getTags())
                .recruiterId(candidate.getRecruiterId())
                .createdAt(candidate.getCreatedAt())
                .updatedAt(candidate.getUpdatedAt())
                .displayName(candidate.getDisplayName())
                .statusDisplayName(getStatusDisplayName(candidate.getStatus()))
                .isTerminalStatus(isTerminalStatus(candidate.getStatus()))
                .nextPossibleStatuses(getNextPossibleStatuses(candidate.getStatus()));
        
        return builder.build();
    }
    
    /**
     * Get human-readable status display name
     */
    private static String getStatusDisplayName(CandidateStatus status) {
        if (status == null) {
            return "Unknown";
        }
        
        return switch (status) {
            case PENDING -> "Pending";
            case APPLIED -> "Applied";
            case INTERVIEW_SCHEDULED -> "Interview Scheduled";
            case IN_PROGRESS -> "In Progress";
            case COMPLETED -> "Completed";
            case UNDER_REVIEW -> "Under Review";
            case SELECTED -> "Selected";
            case REJECTED -> "Rejected";
            case ON_HOLD -> "On Hold";
            case WITHDRAWN -> "Withdrawn";
        };
    }
    
    /**
     * Check if status is terminal (no further transitions possible)
     */
    private static boolean isTerminalStatus(CandidateStatus status) {
        return status == CandidateStatus.SELECTED || 
               status == CandidateStatus.REJECTED || 
               status == CandidateStatus.WITHDRAWN;
    }
    
    /**
     * Get list of next possible statuses from current status
     */
    private static List<CandidateStatus> getNextPossibleStatuses(CandidateStatus currentStatus) {
        if (currentStatus == null) {
            return List.of(CandidateStatus.PENDING);
        }
        
        return switch (currentStatus) {
            case PENDING -> List.of(
                CandidateStatus.APPLIED, 
                CandidateStatus.REJECTED, 
                CandidateStatus.WITHDRAWN
            );
            
            case APPLIED -> List.of(
                CandidateStatus.INTERVIEW_SCHEDULED,
                CandidateStatus.REJECTED,
                CandidateStatus.ON_HOLD,
                CandidateStatus.WITHDRAWN
            );
            
            case INTERVIEW_SCHEDULED -> List.of(
                CandidateStatus.IN_PROGRESS,
                CandidateStatus.COMPLETED,
                CandidateStatus.REJECTED,
                CandidateStatus.ON_HOLD,
                CandidateStatus.WITHDRAWN
            );
            
            case IN_PROGRESS -> List.of(
                CandidateStatus.COMPLETED,
                CandidateStatus.REJECTED,
                CandidateStatus.ON_HOLD
            );
            
            case COMPLETED -> List.of(
                CandidateStatus.UNDER_REVIEW
            );
            
            case UNDER_REVIEW -> List.of(
                CandidateStatus.SELECTED,
                CandidateStatus.REJECTED,
                CandidateStatus.ON_HOLD
            );
            
            case ON_HOLD -> List.of(
                CandidateStatus.APPLIED,
                CandidateStatus.INTERVIEW_SCHEDULED,
                CandidateStatus.REJECTED,
                CandidateStatus.WITHDRAWN
            );
            
            case SELECTED, REJECTED, WITHDRAWN -> List.of(); // Terminal states
        };
    }
}
