package com.company.user.dto;

import com.company.user.model.CandidateStatus;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CandidateResponse {
    private Long id;
    private String requisitionId;
    private String name;
    private String email;
    private String phone;
    private String appliedRole;
    private LocalDate applicationDate;
    private Double totalExperience;
    private Double relevantExperience;
    private String interviewRound;
    private CandidateStatus status;
    private String jobDescription;
    private String keyResponsibilities;
    private List<String> skills;
    private String resumeUrl;
    private String resumeFileName;
    private Long resumeSize;
    private String profilePicUrl;
    private String profilePicFileName;
    private Long profilePicSize;
    private String source;
    private String notes;
    private String tags;
    private String recruiterId;
}
