package com.company.interview.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * InterviewSession entity for managing AI-driven adaptive interviews
 * Supports real-time WebRTC, adaptive questioning, and comprehensive analytics
 */
@Entity
@Table(name = "interview_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id")
    private UUID sessionId;
    
    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;
    
    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InterviewStatus status;
    
    @Column(name = "scheduled_start_time")
    private LocalDateTime scheduledStartTime;
    
    @Column(name = "actual_start_time")
    private LocalDateTime actualStartTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    
    // IRT Parameters for adaptive questioning
    @Column(name = "theta")
    private Double theta = 0.0; // Candidate ability estimate
    
    @Column(name = "standard_error")
    private Double standardError = 1.0; // Uncertainty in theta estimate
    
    @Column(name = "min_questions")
    private Integer minQuestions = 10;
    
    @Column(name = "max_questions")
    private Integer maxQuestions = 30;
    
    @Column(name = "current_question_count")
    private Integer currentQuestionCount = 0;
    
    // Meeting and WebRTC Configuration
    @Column(name = "meeting_link", length = 500)
    private String meetingLink;
    
    @Column(name = "webrtc_room_id")
    private String webrtcRoomId;
    
    @ElementCollection
    @CollectionTable(name = "session_ice_servers", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "ice_server_url")
    private List<String> iceServers;
    
    // Question Selection and Flow
    @ElementCollection
    @CollectionTable(name = "session_question_pool", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "question_id")
    private List<Long> questionPool;
    
    @ElementCollection
    @CollectionTable(name = "session_asked_questions", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "question_id")
    private List<Long> askedQuestions;
    
    @Column(name = "current_question_id")
    private Long currentQuestionId;
    
    // Transcript and Communication
    @Lob
    @Column(name = "full_transcript", columnDefinition = "LONGTEXT")
    private String fullTranscript;
    
    @Lob
    @Column(name = "code_submissions", columnDefinition = "LONGTEXT")
    private String codeSubmissions; // JSON format for code/chat submissions
    
    @Column(name = "candidate_language_preference", length = 10)
    private String languagePreference = "en";
    
    // AI and Analytics Data
    @ElementCollection
    @CollectionTable(name = "session_ai_metrics", joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyColumn(name = "metric_name")
    @Column(name = "metric_value")
    private Map<String, Double> aiMetrics;
    
    @Column(name = "bias_score")
    private Double biasScore;
    
    @Column(name = "engagement_score")
    private Double engagementScore;
    
    @Column(name = "technical_score")
    private Double technicalScore;
    
    @Column(name = "communication_score")
    private Double communicationScore;
    
    // Session Configuration
    @Column(name = "job_role", length = 100)
    private String jobRole;
    
    @Column(name = "experience_level", length = 50)
    private String experienceLevel;
    
    @ElementCollection
    @CollectionTable(name = "session_tech_stack", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "technology")
    private List<String> requiredTechnologies;
    
    @Column(name = "interview_type", length = 50)
    private String interviewType = "ADAPTIVE_AI"; // ADAPTIVE_AI, STRUCTURED, MIXED
    
    // Timestamps
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships
    @OneToMany(mappedBy = "interviewSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<InterviewResponse> responses;
    
    @OneToMany(mappedBy = "interviewSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<InterviewEvent> events;
    
    // Helper methods
    public void incrementQuestionCount() {
        this.currentQuestionCount = (this.currentQuestionCount == null ? 0 : this.currentQuestionCount) + 1;
    }
    
    public boolean isMaxQuestionsReached() {
        return this.currentQuestionCount != null && this.currentQuestionCount >= this.maxQuestions;
    }
    
    public boolean isMinQuestionsReached() {
        return this.currentQuestionCount != null && this.currentQuestionCount >= this.minQuestions;
    }
    
    public boolean canTerminateEarly() {
        return isMinQuestionsReached() && 
               this.standardError != null && 
               this.standardError <= 0.3; // Confidence threshold
    }
    
    public enum InterviewStatus {
        SCHEDULED,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        CANCELLED,
        EXPIRED,
        TECHNICAL_ISSUES
    }
}
