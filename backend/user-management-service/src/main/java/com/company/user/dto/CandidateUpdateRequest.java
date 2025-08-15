package com.company.user.dto;

import com.company.user.model.CandidateStatus;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CandidateUpdateRequest {
    @NotNull private Long id;
    @NotBlank private String requisitionId;
    @NotBlank private String name;
    @Email @NotBlank private String email;
    @NotBlank private String phone;
    @NotBlank private String appliedRole;
    private LocalDate applicationDate;
    @PositiveOrZero private Double totalExperience;
    @PositiveOrZero private Double relevantExperience;
    private String interviewRound;
    @NotNull private CandidateStatus status;
    private String jobDescription;
    private String keyResponsibilities;
    private List<@NotBlank String> skills;
    private String source;
    private String notes;
    private String tags;
    private String recruiterId;
}
