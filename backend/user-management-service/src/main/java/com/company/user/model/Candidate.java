package com.company.user.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "candidates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // business fields
    @Column(nullable = false)
    private String requisitionId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String appliedRole;

    private LocalDate applicationDate;

    private Double totalExperience;
    private Double relevantExperience;

    private String interviewRound;

    @Enumerated(EnumType.STRING)
    private CandidateStatus status;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Column(columnDefinition = "TEXT")
    private String keyResponsibilities;

    // normalized skills storage
    @ElementCollection
    @CollectionTable(name = "candidate_skills", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "skill")
    private List<String> skills;

    // file metadata (store URLs / paths, not bytes)
    private String resumeFileName;
    private String resumeUrl;
    private Long resumeSize;

    private String profilePicFileName;
    private String profilePicUrl;
    private Long profilePicSize;

    // optional meta
    private String source; // e.g., LinkedIn, Referral
    private String notes;
    private String tags; // comma separated for quick search
    private String recruiterId; // who added the candidate

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (applicationDate == null) applicationDate = LocalDate.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
