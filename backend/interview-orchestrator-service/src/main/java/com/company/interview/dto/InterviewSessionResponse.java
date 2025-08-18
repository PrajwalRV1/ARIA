package com.company.interview.dto;

import com.company.interview.model.InterviewSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for interview session operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionResponse {
    
    private UUID sessionId;
    private Long candidateId;
    private Long recruiterId;
    private String status;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    
    // Meeting Information
    private String meetingLink;
    private String webrtcRoomId;
    private List<String> iceServers;
    
    // Interview Progress
    private Double theta;
    private Double standardError;
    private Integer currentQuestionCount;
    private Integer minQuestions;
    private Integer maxQuestions;
    private Long currentQuestionId;
    
    // Configuration
    private String jobRole;
    private String experienceLevel;
    private List<String> requiredTechnologies;
    private String interviewType;
    private String languagePreference;
    
    // Analytics (summary level)
    private Double biasScore;
    private Double engagementScore;
    private Double technicalScore;
    private Double communicationScore;
    private Map<String, Double> aiMetrics;
    
    // Status Information
    private Boolean canStart;
    private Boolean canTerminateEarly;
    private Boolean isMaxQuestionsReached;
    private String nextAction;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Factory method to create from entity
    public static InterviewSessionResponse fromEntity(InterviewSession session) {
        return InterviewSessionResponse.builder()
                .sessionId(session.getSessionId())
                .candidateId(session.getCandidateId())
                .recruiterId(session.getRecruiterId())
                .status(session.getStatus().name())
                .scheduledStartTime(session.getScheduledStartTime())
                .actualStartTime(session.getActualStartTime())
                .endTime(session.getEndTime())
                .durationMinutes(session.getDurationMinutes())
                .meetingLink(session.getMeetingLink())
                .webrtcRoomId(session.getWebrtcRoomId())
                .iceServers(session.getIceServers())
                .theta(session.getTheta())
                .standardError(session.getStandardError())
                .currentQuestionCount(session.getCurrentQuestionCount())
                .minQuestions(session.getMinQuestions())
                .maxQuestions(session.getMaxQuestions())
                .currentQuestionId(session.getCurrentQuestionId())
                .jobRole(session.getJobRole())
                .experienceLevel(session.getExperienceLevel())
                .requiredTechnologies(session.getRequiredTechnologies())
                .interviewType(session.getInterviewType())
                .languagePreference(session.getLanguagePreference())
                .biasScore(session.getBiasScore())
                .engagementScore(session.getEngagementScore())
                .technicalScore(session.getTechnicalScore())
                .communicationScore(session.getCommunicationScore())
                .aiMetrics(session.getAiMetrics())
                .canStart(session.getStatus() == InterviewSession.InterviewStatus.SCHEDULED)
                .canTerminateEarly(session.canTerminateEarly())
                .isMaxQuestionsReached(session.isMaxQuestionsReached())
                .nextAction(determineNextAction(session))
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
    
    private static String determineNextAction(InterviewSession session) {
        switch (session.getStatus()) {
            case SCHEDULED:
                return "START_INTERVIEW";
            case IN_PROGRESS:
                if (session.canTerminateEarly()) {
                    return "CAN_TERMINATE_EARLY";
                } else if (session.isMaxQuestionsReached()) {
                    return "MUST_END";
                } else {
                    return "CONTINUE_QUESTIONS";
                }
            case PAUSED:
                return "RESUME_INTERVIEW";
            case COMPLETED:
                return "VIEW_RESULTS";
            case CANCELLED:
                return "RESCHEDULE";
            default:
                return "UNKNOWN";
        }
    }
}
