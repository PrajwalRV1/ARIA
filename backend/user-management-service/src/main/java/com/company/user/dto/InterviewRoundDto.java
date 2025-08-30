package com.company.user.dto;

import com.company.user.model.InterviewRoundStatus;
import com.company.user.model.InterviewRoundType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * DTO for Interview Round data transfer between client and server.
 * Used for both creating new rounds and updating existing ones.
 */
public class InterviewRoundDto {
    
    private Long id;
    
    @NotNull(message = "Candidate ID is required")
    private Long candidateId;
    
    @NotNull(message = "Round type is required")
    private InterviewRoundType roundType;
    
    private String roundName;
    
    private Integer roundOrder;
    
    private InterviewRoundStatus status = InterviewRoundStatus.NOT_STARTED;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    
    private Integer durationMinutes;
    
    private String interviewerName;
    
    private String interviewerEmail;
    
    private String meetingLink;
    
    private String notes;
    
    private String feedback;
    
    private Integer score;
    
    private Integer maxScore = 100;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // Constructors
    public InterviewRoundDto() {}

    public InterviewRoundDto(Long candidateId, InterviewRoundType roundType) {
        this.candidateId = candidateId;
        this.roundType = roundType;
        this.roundName = roundType.getDisplayName();
        this.roundOrder = roundType.getDefaultOrder();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public InterviewRoundType getRoundType() {
        return roundType;
    }

    public void setRoundType(InterviewRoundType roundType) {
        this.roundType = roundType;
    }

    public String getRoundName() {
        return roundName;
    }

    public void setRoundName(String roundName) {
        this.roundName = roundName;
    }

    public Integer getRoundOrder() {
        return roundOrder;
    }

    public void setRoundOrder(Integer roundOrder) {
        this.roundOrder = roundOrder;
    }

    public InterviewRoundStatus getStatus() {
        return status;
    }

    public void setStatus(InterviewRoundStatus status) {
        this.status = status;
    }

    public LocalDateTime getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDateTime scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getInterviewerName() {
        return interviewerName;
    }

    public void setInterviewerName(String interviewerName) {
        this.interviewerName = interviewerName;
    }

    public String getInterviewerEmail() {
        return interviewerEmail;
    }

    public void setInterviewerEmail(String interviewerEmail) {
        this.interviewerEmail = interviewerEmail;
    }

    public String getMeetingLink() {
        return meetingLink;
    }

    public void setMeetingLink(String meetingLink) {
        this.meetingLink = meetingLink;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods
    public boolean isScheduled() {
        return scheduledDate != null && status == InterviewRoundStatus.INTERVIEW_SCHEDULED;
    }

    public boolean hasScore() {
        return score != null && score >= 0;
    }

    public double getScorePercentage() {
        if (score == null || maxScore == null || maxScore == 0) {
            return 0.0;
        }
        return (double) score / maxScore * 100;
    }

    @Override
    public String toString() {
        return "InterviewRoundDto{" +
                "id=" + id +
                ", candidateId=" + candidateId +
                ", roundType=" + roundType +
                ", roundName='" + roundName + '\'' +
                ", status=" + status +
                ", scheduledDate=" + scheduledDate +
                ", interviewerName='" + interviewerName + '\'' +
                ", score=" + score +
                '}';
    }
}
