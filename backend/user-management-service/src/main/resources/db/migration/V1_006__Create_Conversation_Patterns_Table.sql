-- Conversation Patterns Table
-- For comprehensive analysis of communication patterns during interviews
CREATE TABLE conversation_patterns (
    id BIGSERIAL PRIMARY KEY,
    interview_session_id VARCHAR(255) NOT NULL UNIQUE,
    candidate_id BIGINT NOT NULL,
    recruiter_id BIGINT NULL,
    
    -- Overall Conversation Metrics
    total_duration INT NOT NULL,
    total_questions INT NOT NULL,
    total_words_spoken INT NOT NULL,
    candidate_words INT NOT NULL,
    ai_words INT NOT NULL,
    
    -- Speaking Time Analysis
    candidate_speaking_time INT NOT NULL,
    ai_speaking_time INT NOT NULL,
    silence_time INT NOT NULL,
    overlap_time INT NOT NULL,
    
    -- Response Time Patterns
    average_response_time DOUBLE NOT NULL,
    median_response_time DOUBLE NOT NULL,
    max_response_time DOUBLE NOT NULL,
    min_response_time DOUBLE NOT NULL,
    response_time_variance DOUBLE NOT NULL,
    
    -- Communication Quality Metrics
    clarity_score DECIMAL(5,4) NOT NULL,
    coherence_score DECIMAL(5,4) NOT NULL,
    engagement_score DECIMAL(5,4) NOT NULL,
    confidence_score DECIMAL(5,4) NOT NULL,
    professionalism_score DECIMAL(5,4) NOT NULL,
    
    -- Language and Speech Patterns
    speaking_pace DOUBLE NOT NULL,
    pause_frequency DOUBLE NOT NULL,
    average_pause_duration DOUBLE NOT NULL,
    filler_word_count INT NOT NULL,
    vocabulary_richness DECIMAL(5,4) NOT NULL,
    technical_term_usage INT NOT NULL,
    
    -- Emotional and Sentiment Analysis
    overall_sentiment DECIMAL(5,4) NOT NULL,
    sentiment_progression TEXT NULL,
    stress_indicators INT NOT NULL,
    enthusiasm_level DECIMAL(5,4) NOT NULL,
    
    -- Question-Response Patterns
    direct_answers INT NOT NULL,
    clarification_requests INT NOT NULL,
    follow_up_questions INT NOT NULL,
    incomplete_responses INT NOT NULL,
    topic_changes INT NOT NULL,
    
    -- Interaction Quality
    interruption_count INT NOT NULL,
    ai_interruption_count INT NOT NULL,
    natural_flow_score DECIMAL(5,4) NOT NULL,
    rapport_score DECIMAL(5,4) NOT NULL,
    
    -- Technical Communication Assessment
    explanation_quality DECIMAL(5,4) NOT NULL,
    concept_articulation DECIMAL(5,4) NOT NULL,
    question_understanding DECIMAL(5,4) NOT NULL,
    
    -- Advanced Patterns (JSON storage for flexibility)
    conversation_topics TEXT NULL,
    key_phrases TEXT NULL,
    speech_patterns TEXT NULL,
    communication_style VARCHAR(50) NULL,
    interview_phase_performance TEXT NULL,
    
    -- Comparative Analysis
    peer_comparison_score DECIMAL(5,4) NOT NULL,
    improvement_areas TEXT NULL,
    strengths_identified TEXT NULL,
    
    -- Metadata
    analysis_version VARCHAR(50) NULL,
    confidence_level DECIMAL(5,4) NOT NULL,
    processing_time INT NOT NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    
    -- Foreign Keys
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    
    -- Indexes
    
    -- Composite indexes for analysis queries
    -- INDEX
