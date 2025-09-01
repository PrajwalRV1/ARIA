package com.company.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.Length;
import com.company.user.config.CandidateStatusConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Candidate entity representing interview candidates in the system.
 * Includes comprehensive validation, audit fields, and proper constraints.
 */
@Entity
@Table(name = "candidates", 
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_candidate_email_requisition", 
                           columnNames = {"email", "requisition_id"})
       },
       indexes = {
           @Index(name = "idx_candidate_status", columnList = "status"),
           @Index(name = "idx_candidate_email", columnList = "email"),
           @Index(name = "idx_candidate_requisition", columnList = "requisition_id"),
           @Index(name = "idx_candidate_recruiter", columnList = "recruiter_id"),
           @Index(name = "idx_candidate_created", columnList = "created_at")
       })
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@ToString(exclude = {"skills"}) // Avoid lazy loading in toString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // === CORE BUSINESS FIELDS ===
    
    @NotBlank(message = "Requisition ID is required")
    @Length(max = 255, message = "Requisition ID must not exceed 255 characters")
    @Column(name = "requisition_id", nullable = false, length = 255)
    private String requisitionId;

    @NotBlank(message = "Candidate name is required")
    @Length(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Length(max = 255, message = "Email must not exceed 255 characters")
    @Column(nullable = false, length = 255)
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number must be 10-15 digits")
    @Column(nullable = false, length = 20)
    private String phone;

    @Length(max = 255, message = "Applied role must not exceed 255 characters")
    @Column(name = "applied_role", length = 255)
    private String appliedRole;

    @PastOrPresent(message = "Application date cannot be in the future")
    @Column(name = "application_date")
    private LocalDate applicationDate;

    @DecimalMin(value = "0.0", message = "Total experience cannot be negative")
    @DecimalMax(value = "50.0", message = "Total experience cannot exceed 50 years")
    @Column(name = "total_experience")
    private Double totalExperience;

    @DecimalMin(value = "0.0", message = "Relevant experience cannot be negative")
    @DecimalMax(value = "50.0", message = "Relevant experience cannot exceed 50 years")
    @Column(name = "relevant_experience")
    private Double relevantExperience;

    @Length(max = 100, message = "Interview round must not exceed 100 characters")
    @Column(name = "interview_round", length = 100)
    private String interviewRound;

    @NotNull(message = "Candidate status is required")
    @Convert(converter = CandidateStatusConverter.class)
    @Column(nullable = false, columnDefinition = "candidate_status DEFAULT 'PENDING'")
    private CandidateStatus status;

    // === JOB DETAILS ===
    
    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    @Column(name = "key_responsibilities", columnDefinition = "TEXT")
    private String keyResponsibilities;

    // === SKILLS (Normalized) ===
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "candidate_skills", 
                     joinColumns = @JoinColumn(name = "candidate_id"),
                     foreignKey = @ForeignKey(name = "fk_candidate_skills_candidate"))
    @Column(name = "skill", length = 100)
    @Size(max = 20, message = "Maximum 20 skills allowed")
    private List<@NotBlank @Length(max = 100) String> skills;

    // === FILE METADATA ===
    
    @Length(max = 255, message = "Resume filename too long")
    @Column(name = "resume_file_name", length = 255)
    private String resumeFileName;

    @Length(max = 500, message = "Resume URL too long")
    @Column(name = "resume_url", length = 500)
    private String resumeUrl;

    @Positive(message = "Resume size must be positive")
    @Column(name = "resume_size")
    private Long resumeSize;

    @Length(max = 255, message = "Profile picture filename too long")
    @Column(name = "profile_pic_file_name", length = 255)
    private String profilePicFileName;

    @Length(max = 500, message = "Profile picture URL too long")
    @Column(name = "profile_pic_url", length = 500)
    private String profilePicUrl;

    @Positive(message = "Profile picture size must be positive")
    @Column(name = "profile_pic_size")
    private Long profilePicSize;

    // === AUDIO FILE METADATA ===
    
    @Length(max = 255, message = "Audio filename too long")
    @Column(name = "audio_filename", length = 255)
    private String audioFilename;

    @Length(max = 500, message = "Audio URL too long")
    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Positive(message = "Audio size must be positive")
    @Column(name = "audio_size")
    private Long audioSize;

    // === METADATA ===
    
    @Length(max = 100, message = "Source must not exceed 100 characters")
    @Column(length = 100)
    private String source; // e.g., LinkedIn, Referral

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Length(max = 500, message = "Tags must not exceed 500 characters")
    @Column(length = 500)
    private String tags; // comma separated for quick search

    @Length(max = 100, message = "Recruiter ID too long")
    @Column(name = "recruiter_id", length = 100)
    private String recruiterId; // who added the candidate

    // === TENANT ISOLATION (CRITICAL SECURITY) ===
    
    @NotBlank(message = "Tenant ID is required for data isolation")
    @Length(max = 255, message = "Tenant ID must not exceed 255 characters")
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId; // Tenant isolation for multi-tenancy
    
    @Length(max = 255, message = "Created by must not exceed 255 characters")
    @Column(name = "created_by", length = 255)
    private String createdBy; // User who created this record
    
    @Length(max = 255, message = "Updated by must not exceed 255 characters")
    @Column(name = "updated_by", length = 255)
    private String updatedBy; // User who last updated this record

    // === INTERVIEW ROUND STATUS ===
    
    @Length(max = 100, message = "Overall status must not exceed 100 characters")
    @Column(name = "overall_status", length = 100)
    private String overallStatus; // computed from interview rounds

    // === AUDIT FIELDS ===
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // === LIFECYCLE CALLBACKS ===
    
    @PrePersist
    public void prePersist() {
        if (applicationDate == null) {
            applicationDate = LocalDate.now();
        }
        if (status == null) {
            status = CandidateStatus.PENDING;
        }
        // CreationTimestamp and UpdateTimestamp handle the timestamps
    }

    @PreUpdate
    public void preUpdate() {
        // UpdateTimestamp handles the updatedAt field
    }

    // === BUSINESS METHODS ===
    
    /**
     * Check if the candidate has a resume uploaded
     */
    public boolean hasResume() {
        return resumeUrl != null && !resumeUrl.trim().isEmpty();
    }

    /**
     * Check if the candidate has a profile picture uploaded
     */
    public boolean hasProfilePicture() {
        return profilePicUrl != null && !profilePicUrl.trim().isEmpty();
    }

    /**
     * Check if the candidate has an audio file uploaded
     */
    public boolean hasAudioFile() {
        return audioUrl != null && !audioUrl.trim().isEmpty();
    }

    /**
     * Get display name (name + email for uniqueness)
     */
    public String getDisplayName() {
        return name + " (" + email + ")";
    }

    /**
     * Check if status can be transitioned to the target status
     */
    public boolean canTransitionTo(CandidateStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        
        // Define valid status transitions
        return switch (this.status) {
            case PENDING -> targetStatus == CandidateStatus.APPLIED || 
                           targetStatus == CandidateStatus.REJECTED ||
                           targetStatus == CandidateStatus.WITHDRAWN;
                           
            case APPLIED -> targetStatus == CandidateStatus.INTERVIEW_SCHEDULED ||
                           targetStatus == CandidateStatus.REJECTED ||
                           targetStatus == CandidateStatus.ON_HOLD ||
                           targetStatus == CandidateStatus.WITHDRAWN;
                           
            case INTERVIEW_SCHEDULED -> targetStatus == CandidateStatus.IN_PROGRESS ||
                                      targetStatus == CandidateStatus.COMPLETED ||
                                      targetStatus == CandidateStatus.REJECTED ||
                                      targetStatus == CandidateStatus.ON_HOLD ||
                                      targetStatus == CandidateStatus.WITHDRAWN;
                                      
            case IN_PROGRESS -> targetStatus == CandidateStatus.COMPLETED ||
                              targetStatus == CandidateStatus.REJECTED ||
                              targetStatus == CandidateStatus.ON_HOLD;
                              
            case COMPLETED -> targetStatus == CandidateStatus.UNDER_REVIEW;
            
            case UNDER_REVIEW -> targetStatus == CandidateStatus.SELECTED ||
                               targetStatus == CandidateStatus.REJECTED ||
                               targetStatus == CandidateStatus.ON_HOLD;
                               
            case ON_HOLD -> targetStatus == CandidateStatus.APPLIED ||
                          targetStatus == CandidateStatus.INTERVIEW_SCHEDULED ||
                          targetStatus == CandidateStatus.REJECTED ||
                          targetStatus == CandidateStatus.WITHDRAWN;
                          
            case SELECTED, REJECTED, WITHDRAWN -> false; // Terminal states
        };
    }
    
    // === EXPLICIT GETTERS FOR COMPILATION FIX ===
    // These explicit getters resolve Lombok compilation issues in containerized builds
    
    public String getAudioFilename() {
        return audioFilename;
    }
    
    public String getAudioUrl() {
        return audioUrl;
    }
    
    public Long getAudioSize() {
        return audioSize;
    }
    
    public String getSource() {
        return source;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public String getTags() {
        return tags;
    }
    
    public String getRecruiterId() {
        return recruiterId;
    }
}
