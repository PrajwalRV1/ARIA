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
 * InterviewResponse entity for capturing and analyzing candidate responses
 * Supports multi-modal input (speech, text, code) with AI-powered evaluation
 */
@Entity
@Table(name = "interview_responses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewResponse {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "response_id")
    private Long responseId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonBackReference
    private InterviewSession interviewSession;
    
    @Column(name = "question_id", nullable = false)
    private Long questionId;
    
    @Column(name = "question_sequence", nullable = false)
    private Integer questionSequence;
    
    // Multi-modal Response Data
    @Lob
    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText; // Speech-to-text transcription
    
    @Lob
    @Column(name = "code_submission", columnDefinition = "TEXT")
    private String codeSubmission; // Code typed in Monaco editor
    
    @Lob
    @Column(name = "chat_text", columnDefinition = "TEXT")
    private String chatText; // Text typed in chat box
    
    @Lob
    @Column(name = "combined_response", columnDefinition = "TEXT")
    private String combinedResponse; // Merged transcript + code + chat
    
    // Timing and Interaction Data
    @Column(name = "response_start_time")
    private LocalDateTime responseStartTime;
    
    @Column(name = "response_end_time")
    private LocalDateTime responseEndTime;
    
    @Column(name = "think_time_seconds")
    private Integer thinkTimeSeconds; // Time before candidate started responding
    
    @Column(name = "response_duration_seconds")
    private Integer responseDurationSeconds;
    
    @Column(name = "spacebar_pressed")
    private Boolean spacebarPressed = false; // Did candidate signal completion?
    
    @Column(name = "auto_terminated")
    private Boolean autoTerminated = false; // Was response auto-ended due to silence?
    
    // IRT and Scoring Data
    @Column(name = "question_difficulty")
    private Double questionDifficulty; // Question difficulty parameter (b)
    
    @Column(name = "discrimination")
    private Double discrimination; // Question discrimination parameter (a)
    
    @Column(name = "correct_answer")
    private Boolean correctAnswer; // For objective questions
    
    @Column(name = "partial_credit")
    private Double partialCredit; // 0.0 to 1.0 for subjective scoring
    
    @Column(name = "ai_score")
    private Double aiScore; // Overall AI-generated score
    
    @Column(name = "human_score")
    private Double humanScore; // Human reviewer override score
    
    // AI Analysis Results
    @ElementCollection
    @CollectionTable(name = "response_ai_analysis", joinColumns = @JoinColumn(name = "response_id"))
    @MapKeyColumn(name = "analysis_type")
    @Column(name = "analysis_score")
    private Map<String, Double> aiAnalysisScores;
    
    @Column(name = "sentiment_score")
    private Double sentimentScore; // Sentiment analysis of response
    
    @Column(name = "confidence_score")
    private Double confidenceScore; // AI confidence in candidate's answer
    
    @Column(name = "clarity_score")
    private Double clarityScore; // Speech clarity and articulation
    
    @Column(name = "relevance_score")
    private Double relevanceScore; // Answer relevance to question
    
    // Technical Analysis (for coding responses)
    @Column(name = "code_quality_score")
    private Double codeQualityScore;
    
    @Column(name = "algorithm_correctness")
    private Double algorithmCorrectness;
    
    @Column(name = "code_efficiency_score")
    private Double codeEfficiencyScore;
    
    @Column(name = "syntax_errors_count")
    private Integer syntaxErrorsCount;
    
    // Bias Detection
    @Column(name = "bias_flag")
    private Boolean biasFlag = false;
    
    @Lob
    @Column(name = "bias_details", columnDefinition = "TEXT")
    private String biasDetails; // JSON with bias analysis details
    
    // Follow-up and Adaptation
    @Column(name = "needs_followup")
    private Boolean needsFollowup = false;
    
    @Lob
    @Column(name = "followup_reason", columnDefinition = "TEXT")
    private String followupReason;
    
    @Column(name = "adapted_next_difficulty", columnDefinition = "DECIMAL(5,4)")
    private Double adaptedNextDifficulty; // Suggested difficulty for next question
    
    // Metadata
    @Column(name = "audio_file_path", length = 500)
    private String audioFilePath; // Path to recorded audio segment
    
    @Column(name = "video_timestamp_start")
    private Long videoTimestampStart; // Milliseconds from interview start
    
    @Column(name = "video_timestamp_end")
    private Long videoTimestampEnd;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    // Helper methods
    public Integer calculateResponseDuration() {
        if (responseStartTime != null && responseEndTime != null) {
            return (int) java.time.Duration.between(responseStartTime, responseEndTime).getSeconds();
        }
        return null;
    }
    
    public boolean hasCodeComponent() {
        return codeSubmission != null && !codeSubmission.trim().isEmpty();
    }
    
    public boolean hasTextComponent() {
        return (transcriptText != null && !transcriptText.trim().isEmpty()) ||
               (chatText != null && !chatText.trim().isEmpty());
    }
    
    public boolean isMultiModalResponse() {
        return hasCodeComponent() && hasTextComponent();
    }
    
    public Double getOverallScore() {
        if (humanScore != null) {
            return humanScore; // Human override takes precedence
        }
        return aiScore != null ? aiScore : partialCredit;
    }
}
