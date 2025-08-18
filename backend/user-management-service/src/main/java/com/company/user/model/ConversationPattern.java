package com.company.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity to analyze conversation patterns and flow during interviews
 * Used for improving interview experience and identifying communication patterns
 */
@Entity
@Table(name = "conversation_patterns")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_session_id", nullable = false, unique = true)
    @NotBlank(message = "Interview session ID is required")
    private String interviewSessionId;

    @Column(name = "candidate_id", nullable = false)
    @NotNull(message = "Candidate ID is required")
    private Long candidateId;

    @Column(name = "recruiter_id")
    private Long recruiterId; // For human-in-the-loop scenarios

    // Overall Conversation Metrics
    @Column(name = "total_duration", nullable = false)
    @Min(value = 0, message = "Total duration cannot be negative")
    private Integer totalDuration; // in seconds

    @Column(name = "total_questions", nullable = false)
    @Min(value = 0, message = "Total questions cannot be negative")
    private Integer totalQuestions;

    @Column(name = "total_words_spoken", nullable = false)
    @Min(value = 0, message = "Total words spoken cannot be negative")
    private Integer totalWordsSpoken;

    @Column(name = "candidate_words", nullable = false)
    @Min(value = 0, message = "Candidate words cannot be negative")
    private Integer candidateWords;

    @Column(name = "ai_words", nullable = false)
    @Min(value = 0, message = "AI words cannot be negative")
    private Integer aiWords;

    // Speaking Time Analysis
    @Column(name = "candidate_speaking_time", nullable = false)
    @Min(value = 0, message = "Candidate speaking time cannot be negative")
    private Integer candidateSpeakingTime; // in seconds

    @Column(name = "ai_speaking_time", nullable = false)
    @Min(value = 0, message = "AI speaking time cannot be negative")
    private Integer aiSpeakingTime; // in seconds

    @Column(name = "silence_time", nullable = false)
    @Min(value = 0, message = "Silence time cannot be negative")
    private Integer silenceTime; // in seconds

    @Column(name = "overlap_time", nullable = false)
    @Min(value = 0, message = "Overlap time cannot be negative")
    private Integer overlapTime; // in seconds

    // Response Time Patterns
    @Column(name = "average_response_time", nullable = false)
    @Min(value = 0, message = "Average response time cannot be negative")
    private Double averageResponseTime; // in seconds

    @Column(name = "median_response_time", nullable = false)
    @Min(value = 0, message = "Median response time cannot be negative")
    private Double medianResponseTime; // in seconds

    @Column(name = "max_response_time", nullable = false)
    @Min(value = 0, message = "Max response time cannot be negative")
    private Double maxResponseTime; // in seconds

    @Column(name = "min_response_time", nullable = false)
    @Min(value = 0, message = "Min response time cannot be negative")
    private Double minResponseTime; // in seconds

    @Column(name = "response_time_variance", nullable = false)
    @Min(value = 0, message = "Response time variance cannot be negative")
    private Double responseTimeVariance;

    // Communication Quality Metrics
    @Column(name = "clarity_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Clarity score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Clarity score must not exceed 1.0")
    private Double clarityScore; // How clear candidate's communication is

    @Column(name = "coherence_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Coherence score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Coherence score must not exceed 1.0")
    private Double coherenceScore; // How coherent the conversation flow is

    @Column(name = "engagement_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Engagement score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Engagement score must not exceed 1.0")
    private Double engagementScore; // Overall candidate engagement

    @Column(name = "confidence_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Confidence score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Confidence score must not exceed 1.0")
    private Double confidenceScore; // Candidate confidence level

    @Column(name = "professionalism_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Professionalism score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Professionalism score must not exceed 1.0")
    private Double professionalismScore; // Professional communication level

    // Language and Speech Patterns
    @Column(name = "speaking_pace", nullable = false)
    @Min(value = 0, message = "Speaking pace cannot be negative")
    private Double speakingPace; // words per minute

    @Column(name = "pause_frequency", nullable = false)
    @Min(value = 0, message = "Pause frequency cannot be negative")
    private Double pauseFrequency; // pauses per minute

    @Column(name = "average_pause_duration", nullable = false)
    @Min(value = 0, message = "Average pause duration cannot be negative")
    private Double averagePauseDuration; // in seconds

    @Column(name = "filler_word_count", nullable = false)
    @Min(value = 0, message = "Filler word count cannot be negative")
    private Integer fillerWordCount; // um, uh, like, etc.

    @Column(name = "vocabulary_richness", nullable = false)
    @DecimalMin(value = "0.0", message = "Vocabulary richness must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Vocabulary richness must not exceed 1.0")
    private Double vocabularyRichness; // unique words / total words

    @Column(name = "technical_term_usage", nullable = false)
    @Min(value = 0, message = "Technical term usage cannot be negative")
    private Integer technicalTermUsage; // count of technical terms used

    // Emotional and Sentiment Analysis
    @Column(name = "overall_sentiment", nullable = false)
    @DecimalMin(value = "-1.0", message = "Overall sentiment must be at least -1.0")
    @DecimalMax(value = "1.0", message = "Overall sentiment must not exceed 1.0")
    private Double overallSentiment; // -1 (negative) to 1 (positive)

    @Column(name = "sentiment_progression", columnDefinition = "TEXT")
    private String sentimentProgression; // JSON array of sentiment scores over time

    @Column(name = "stress_indicators", nullable = false)
    @Min(value = 0, message = "Stress indicators cannot be negative")
    private Integer stressIndicators; // count of stress-related speech patterns

    @Column(name = "enthusiasm_level", nullable = false)
    @DecimalMin(value = "0.0", message = "Enthusiasm level must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Enthusiasm level must not exceed 1.0")
    private Double enthusiasmLevel;

    // Question-Response Patterns
    @Column(name = "direct_answers", nullable = false)
    @Min(value = 0, message = "Direct answers cannot be negative")
    private Integer directAnswers; // questions answered directly

    @Column(name = "clarification_requests", nullable = false)
    @Min(value = 0, message = "Clarification requests cannot be negative")
    private Integer clarificationRequests; // times candidate asked for clarification

    @Column(name = "follow_up_questions", nullable = false)
    @Min(value = 0, message = "Follow-up questions cannot be negative")
    private Integer followUpQuestions; // candidate-initiated questions

    @Column(name = "incomplete_responses", nullable = false)
    @Min(value = 0, message = "Incomplete responses cannot be negative")
    private Integer incompleteResponses; // responses that seemed cut off

    @Column(name = "topic_changes", nullable = false)
    @Min(value = 0, message = "Topic changes cannot be negative")
    private Integer topicChanges; // times conversation changed topics abruptly

    // Interaction Quality
    @Column(name = "interruption_count", nullable = false)
    @Min(value = 0, message = "Interruption count cannot be negative")
    private Integer interruptionCount; // times candidate interrupted AI

    @Column(name = "ai_interruption_count", nullable = false)
    @Min(value = 0, message = "AI interruption count cannot be negative")
    private Integer aiInterruptionCount; // times AI interrupted candidate

    @Column(name = "natural_flow_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Natural flow score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Natural flow score must not exceed 1.0")
    private Double naturalFlowScore; // how natural the conversation felt

    @Column(name = "rapport_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Rapport score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Rapport score must not exceed 1.0")
    private Double rapportScore; // quality of rapport between candidate and AI

    // Technical Communication Assessment
    @Column(name = "explanation_quality", nullable = false)
    @DecimalMin(value = "0.0", message = "Explanation quality must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Explanation quality must not exceed 1.0")
    private Double explanationQuality; // quality of technical explanations

    @Column(name = "concept_articulation", nullable = false)
    @DecimalMin(value = "0.0", message = "Concept articulation must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Concept articulation must not exceed 1.0")
    private Double conceptArticulation; // ability to articulate complex concepts

    @Column(name = "question_understanding", nullable = false)
    @DecimalMin(value = "0.0", message = "Question understanding must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Question understanding must not exceed 1.0")
    private Double questionUnderstanding; // how well candidate understood questions

    // Advanced Patterns
    @Column(name = "conversation_topics", columnDefinition = "TEXT")
    private String conversationTopics; // JSON array of identified topics

    @Column(name = "key_phrases", columnDefinition = "TEXT")
    private String keyPhrases; // JSON array of important phrases used

    @Column(name = "speech_patterns", columnDefinition = "TEXT")
    private String speechPatterns; // JSON object with detailed speech analysis

    @Column(name = "communication_style")
    private String communicationStyle; // formal, casual, technical, conversational

    @Column(name = "interview_phase_performance", columnDefinition = "TEXT")
    private String interviewPhasePerformance; // JSON object with phase-wise metrics

    // Comparative Analysis
    @Column(name = "peer_comparison_score", nullable = false)
    @DecimalMin(value = "0.0", message = "Peer comparison score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Peer comparison score must not exceed 1.0")
    private Double peerComparisonScore; // compared to other candidates in similar roles

    @Column(name = "improvement_areas", columnDefinition = "TEXT")
    private String improvementAreas; // JSON array of identified improvement areas

    @Column(name = "strengths_identified", columnDefinition = "TEXT")
    private String strengthsIdentified; // JSON array of communication strengths

    // Metadata
    @Column(name = "analysis_version")
    private String analysisVersion; // version of analysis algorithm used

    @Column(name = "confidence_level", nullable = false)
    @DecimalMin(value = "0.0", message = "Confidence level must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Confidence level must not exceed 1.0")
    private Double confidenceLevel; // confidence in the analysis results

    @Column(name = "processing_time", nullable = false)
    @Min(value = 0, message = "Processing time cannot be negative")
    private Integer processingTime; // time taken to analyze (in milliseconds)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Calculated metrics methods
    public Double getCandidateSpeakingRatio() {
        if (totalDuration == 0) return 0.0;
        return (double) candidateSpeakingTime / totalDuration;
    }

    public Double getWordsPerMinute() {
        if (candidateSpeakingTime == 0) return 0.0;
        return ((double) candidateWords / candidateSpeakingTime) * 60.0;
    }

    public Double getResponseEfficiency() {
        if (totalQuestions == 0) return 0.0;
        return (double) directAnswers / totalQuestions;
    }

    public Double getInteractionQuality() {
        // Composite score based on multiple factors
        double flowWeight = 0.3;
        double clarityWeight = 0.25;
        double engagementWeight = 0.25;
        double professionalismWeight = 0.2;
        
        return (naturalFlowScore * flowWeight) + 
               (clarityScore * clarityWeight) + 
               (engagementScore * engagementWeight) + 
               (professionalismScore * professionalismWeight);
    }

    public Double getCommunicationScore() {
        // Overall communication effectiveness score
        double technicalWeight = 0.3;
        double clarityWeight = 0.25;
        double coherenceWeight = 0.25;
        double confidenceWeight = 0.2;
        
        return (explanationQuality * technicalWeight) + 
               (clarityScore * clarityWeight) + 
               (coherenceScore * coherenceWeight) + 
               (confidenceScore * confidenceWeight);
    }

    public Boolean isHighPerformer() {
        return getCommunicationScore() > 0.75 && 
               getInteractionQuality() > 0.75 && 
               peerComparisonScore > 0.7;
    }

    public Boolean needsImprovementInCommunication() {
        return clarityScore < 0.5 || coherenceScore < 0.5 || explanationQuality < 0.5;
    }

    public Boolean showsStressSigns() {
        return stressIndicators > 5 || 
               pauseFrequency > 10.0 || 
               fillerWordCount > candidateWords * 0.1;
    }

    // Static factory method
    public static ConversationPattern createInitial(String sessionId, Long candidateId) {
        ConversationPattern pattern = new ConversationPattern();
        pattern.setInterviewSessionId(sessionId);
        pattern.setCandidateId(candidateId);
        
        // Initialize with default values
        pattern.setTotalDuration(0);
        pattern.setTotalQuestions(0);
        pattern.setTotalWordsSpoken(0);
        pattern.setCandidateWords(0);
        pattern.setAiWords(0);
        pattern.setCandidateSpeakingTime(0);
        pattern.setAiSpeakingTime(0);
        pattern.setSilenceTime(0);
        pattern.setOverlapTime(0);
        
        // Initialize metrics
        pattern.setAverageResponseTime(0.0);
        pattern.setMedianResponseTime(0.0);
        pattern.setMaxResponseTime(0.0);
        pattern.setMinResponseTime(0.0);
        pattern.setResponseTimeVariance(0.0);
        
        // Initialize quality scores
        pattern.setClarityScore(0.5);
        pattern.setCoherenceScore(0.5);
        pattern.setEngagementScore(0.5);
        pattern.setConfidenceScore(0.5);
        pattern.setProfessionalismScore(0.5);
        
        return pattern;
    }

    // Update methods
    public void updateFromTranscript(String transcript, LocalDateTime timestamp) {
        // This would be implemented to analyze transcript and update metrics
        // Using NLP libraries like OpenNLP, Stanford NLP, or cloud APIs
    }

    public void updateSpeechMetrics(double speakingTime, int wordCount, 
                                   double pauseCount, double avgPauseDuration) {
        this.candidateSpeakingTime += (int) speakingTime;
        this.candidateWords += wordCount;
        this.pauseFrequency = pauseCount;
        this.averagePauseDuration = avgPauseDuration;
        this.speakingPace = getWordsPerMinute();
    }

    public void updateQualityScores(double clarity, double coherence, double engagement, 
                                   double confidence, double professionalism) {
        this.clarityScore = Math.max(0.0, Math.min(1.0, clarity));
        this.coherenceScore = Math.max(0.0, Math.min(1.0, coherence));
        this.engagementScore = Math.max(0.0, Math.min(1.0, engagement));
        this.confidenceScore = Math.max(0.0, Math.min(1.0, confidence));
        this.professionalismScore = Math.max(0.0, Math.min(1.0, professionalism));
    }

    public void recordQuestionResponse(double responseTime, boolean direct, 
                                     boolean needsClarification) {
        this.totalQuestions++;
        
        // Update response time statistics
        updateResponseTimeStats(responseTime);
        
        if (direct) {
            this.directAnswers++;
        }
        
        if (needsClarification) {
            this.clarificationRequests++;
        }
    }

    private void updateResponseTimeStats(double responseTime) {
        if (this.totalQuestions == 1) {
            this.minResponseTime = responseTime;
            this.maxResponseTime = responseTime;
            this.averageResponseTime = responseTime;
            this.medianResponseTime = responseTime;
        } else {
            this.minResponseTime = Math.min(this.minResponseTime, responseTime);
            this.maxResponseTime = Math.max(this.maxResponseTime, responseTime);
            
            // Update running average
            this.averageResponseTime = ((this.averageResponseTime * (totalQuestions - 1)) + responseTime) / totalQuestions;
            
            // For median and variance, you'd need to maintain a list of response times
            // This is a simplified implementation
        }
    }

    public void finalizeMeeting() {
        // Calculate final metrics and scores
        this.vocabularyRichness = calculateVocabularyRichness();
        this.naturalFlowScore = calculateNaturalFlowScore();
        this.rapportScore = calculateRapportScore();
        // ... other final calculations
    }

    private Double calculateVocabularyRichness() {
        // This would analyze the transcript for unique vs total words
        // Placeholder implementation
        return Math.min(1.0, candidateWords / 100.0); // Simplified
    }

    private Double calculateNaturalFlowScore() {
        // Calculate based on interruptions, overlaps, and response timing
        double interruptionPenalty = Math.min(0.5, interruptionCount * 0.1);
        double timingScore = Math.max(0.0, 1.0 - (responseTimeVariance / 10.0));
        return Math.max(0.0, timingScore - interruptionPenalty);
    }

    private Double calculateRapportScore() {
        // Calculate based on engagement, sentiment, and interaction quality
        return (engagementScore + Math.max(0.0, overallSentiment) + enthusiasmLevel) / 3.0;
    }
}
