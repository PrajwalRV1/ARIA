package com.company.interview.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * InterviewEvent entity for tracking all events during an interview session
 * Captures user interactions, system events, and AI decisions for analytics
 */
@Entity
@Table(name = "interview_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonBackReference
    private InterviewSession interviewSession;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;
    
    @Column(name = "event_source", length = 50)
    private String eventSource; // USER, SYSTEM, AI_ENGINE, SPEECH_SERVICE, etc.
    
    @Column(name = "event_description", length = 500)
    private String eventDescription;
    
    // Event Data
    @Lob
    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData; // JSON format for flexible event data
    
    @Column(name = "question_id")
    private Long questionId; // Associated question if applicable
    
    @Column(name = "response_id")
    private Long responseId; // Associated response if applicable
    
    // Timing Information
    @Column(name = "timestamp_millis")
    private Long timestampMillis; // Milliseconds from interview start
    
    @Column(name = "duration_millis")
    private Long durationMillis; // Duration of the event if applicable
    
    // Severity and Priority
    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private EventSeverity severity = EventSeverity.INFO;
    
    @Column(name = "requires_attention")
    private Boolean requiresAttention = false;
    
    // Additional Metadata
    @ElementCollection
    @CollectionTable(name = "event_metadata", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    public enum EventType {
        // Session Events
        SESSION_SCHEDULED,
        SESSION_STARTED,
        SESSION_PAUSED,
        SESSION_RESUMED,
        SESSION_ENDED,
        SESSION_CANCELLED,
        
        // User Interaction Events
        CANDIDATE_JOINED,
        CANDIDATE_LEFT,
        RECRUITER_JOINED,
        RECRUITER_LEFT,
        SPACEBAR_PRESSED,
        CODE_SUBMITTED,
        CHAT_MESSAGE_SENT,
        
        // Question Events
        QUESTION_PRESENTED,
        QUESTION_SKIPPED,
        RESPONSE_STARTED,
        RESPONSE_ENDED,
        RESPONSE_TIMEOUT,
        
        // Technical Events
        WEBRTC_CONNECTED,
        WEBRTC_DISCONNECTED,
        AUDIO_QUALITY_POOR,
        VIDEO_QUALITY_POOR,
        NETWORK_ISSUE,
        MICROPHONE_MUTED,
        MICROPHONE_UNMUTED,
        CAMERA_ON,
        CAMERA_OFF,
        
        // AI Engine Events
        THETA_UPDATED,
        NEXT_QUESTION_SELECTED,
        BIAS_DETECTED,
        CONFIDENCE_THRESHOLD_REACHED,
        EARLY_TERMINATION_TRIGGERED,
        
        // Speech Service Events
        TRANSCRIPT_UPDATED,
        SPEECH_RECOGNITION_STARTED,
        SPEECH_RECOGNITION_STOPPED,
        SILENCE_DETECTED,
        SPEECH_QUALITY_WARNING,
        
        // Analytics Events
        ENGAGEMENT_SCORE_CALCULATED,
        SENTIMENT_ANALYZED,
        CODE_QUALITY_ASSESSED,
        BIAS_SCORE_CALCULATED,
        
        // System Events
        ERROR_OCCURRED,
        WARNING_GENERATED,
        SYSTEM_RECOVERY,
        BACKUP_CREATED,
        
        // Custom Events
        CUSTOM_EVENT
    }
    
    public enum EventSeverity {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    // Helper methods
    public boolean isUserAction() {
        return eventSource != null && eventSource.equals("USER");
    }
    
    public boolean isSystemEvent() {
        return eventSource != null && eventSource.equals("SYSTEM");
    }
    
    public boolean isAiEvent() {
        return eventSource != null && eventSource.contains("AI");
    }
    
    public boolean isCritical() {
        return severity == EventSeverity.CRITICAL || severity == EventSeverity.ERROR;
    }
}
