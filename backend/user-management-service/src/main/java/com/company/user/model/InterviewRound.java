package com.company.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;

/**
 * Entity representing individual interview rounds for candidates.
 * Each candidate has multiple rounds (Screening, Technical T1/T2, HR, Managerial)
 * and each round has its own independent status and details.
 */
@Entity
@Table(name = "interview_rounds",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_interview_rounds_candidate_type",
                           columnNames = {"candidate_id", "round_type"})
       },
       indexes = {
           @Index(name = "idx_interview_rounds_candidate_id", columnList = "candidate_id"),
           @Index(name = "idx_interview_rounds_status", columnList = "status"),
           @Index(name = "idx_interview_rounds_type", columnList = "round_type"),
           @Index(name = "idx_interview_rounds_scheduled", columnList = "scheduled_at"),
           @Index(name = "idx_interview_rounds_candidate_order", columnList = "candidate_id, round_order")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"candidate"}) // Avoid circular reference
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InterviewRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // === RELATIONSHIP ===
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_interview_rounds_candidate"))
    @NotNull(message = "Candidate is required")
    private Candidate candidate;

    // === ROUND INFORMATION ===
    
    @Enumerated(EnumType.STRING)
    @Column(name = "round_type", nullable = false)
    @NotNull(message = "Round type is required")
    private InterviewRoundType roundType;

    @NotBlank(message = "Round name is required")
    @Length(max = 100, message = "Round name must not exceed 100 characters")
    @Column(name = "round_name", nullable = false, length = 100)
    private String roundName;

    @NotNull(message = "Round order is required")
    @Min(value = 1, message = "Round order must be at least 1")
    @Max(value = 10, message = "Round order must not exceed 10")
    @Column(name = "round_order", nullable = false)
    private Integer roundOrder;

    // === STATUS AND TIMING ===
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Round status is required")
    private InterviewRoundStatus status;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // === INTERVIEW DETAILS ===
    
    @Length(max = 255, message = "Interviewer name too long")
    @Column(name = "interviewer_name", length = 255)
    private String interviewerName;

    @Email(message = "Interviewer email must be valid")
    @Length(max = 255, message = "Interviewer email too long")
    @Column(name = "interviewer_email", length = 255)
    private String interviewerEmail;

    @Length(max = 500, message = "Meeting link too long")
    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Min(value = 0, message = "Score cannot be negative")
    @Max(value = 100, message = "Score cannot exceed 100")
    private Integer score;

    // === AUDIT FIELDS ===
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // === BUSINESS METHODS ===
    
    /**
     * Check if this round is completed (successfully or with rejection)
     */
    public boolean isCompleted() {
        return status == InterviewRoundStatus.COMPLETED || 
               status == InterviewRoundStatus.REJECTED ||
               status == InterviewRoundStatus.WITHDRAWN;
    }

    /**
     * Check if this round is in a terminal state (no further action needed)
     */
    public boolean isTerminal() {
        return status == InterviewRoundStatus.COMPLETED ||
               status == InterviewRoundStatus.REJECTED ||
               status == InterviewRoundStatus.WITHDRAWN;
    }

    /**
     * Check if this round is active (scheduled or in progress)
     */
    public boolean isActive() {
        return status == InterviewRoundStatus.INTERVIEW_SCHEDULED ||
               status == InterviewRoundStatus.IN_PROGRESS;
    }

    /**
     * Check if this round can be started (is currently not started)
     */
    public boolean canBeStarted() {
        return status == InterviewRoundStatus.NOT_STARTED;
    }

    /**
     * Check if status transition is valid for this round
     */
    public boolean canTransitionTo(InterviewRoundStatus newStatus) {
        if (newStatus == null || newStatus == this.status) {
            return false;
        }

        return switch (this.status) {
            case NOT_STARTED -> newStatus == InterviewRoundStatus.INTERVIEW_SCHEDULED ||
                              newStatus == InterviewRoundStatus.ON_HOLD ||
                              newStatus == InterviewRoundStatus.WITHDRAWN;

            case INTERVIEW_SCHEDULED -> newStatus == InterviewRoundStatus.IN_PROGRESS ||
                                      newStatus == InterviewRoundStatus.ON_HOLD ||
                                      newStatus == InterviewRoundStatus.REJECTED ||
                                      newStatus == InterviewRoundStatus.WITHDRAWN;

            case IN_PROGRESS -> newStatus == InterviewRoundStatus.UNDER_REVIEW ||
                              newStatus == InterviewRoundStatus.COMPLETED ||
                              newStatus == InterviewRoundStatus.ON_HOLD ||
                              newStatus == InterviewRoundStatus.REJECTED;

            case UNDER_REVIEW -> newStatus == InterviewRoundStatus.COMPLETED ||
                               newStatus == InterviewRoundStatus.REJECTED ||
                               newStatus == InterviewRoundStatus.ON_HOLD;

            case ON_HOLD -> newStatus == InterviewRoundStatus.INTERVIEW_SCHEDULED ||
                          newStatus == InterviewRoundStatus.REJECTED ||
                          newStatus == InterviewRoundStatus.WITHDRAWN;

            case COMPLETED, REJECTED, WITHDRAWN -> false; // Terminal states
        };
    }

    /**
     * Get human-readable status description
     */
    public String getStatusDescription() {
        return switch (status) {
            case NOT_STARTED -> "Not Started";
            case INTERVIEW_SCHEDULED -> "Interview Scheduled";
            case IN_PROGRESS -> "In Progress";
            case UNDER_REVIEW -> "Under Review";
            case COMPLETED -> "Completed";
            case ON_HOLD -> "On Hold";
            case REJECTED -> "Rejected";
            case WITHDRAWN -> "Withdrawn";
        };
    }

    /**
     * Get duration of the round if completed
     */
    public Long getDurationMinutes() {
        if (startedAt != null && completedAt != null) {
            return java.time.Duration.between(startedAt, completedAt).toMinutes();
        }
        return null;
    }

    // === LIFECYCLE CALLBACKS ===
    
    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = InterviewRoundStatus.NOT_STARTED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        // Automatically set timing fields based on status changes
        if (status == InterviewRoundStatus.IN_PROGRESS && startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (isCompleted() && completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
}
