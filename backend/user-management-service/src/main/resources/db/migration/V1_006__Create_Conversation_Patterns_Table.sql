-- Conversation Patterns Table
-- For comprehensive analysis of communication patterns during interviews
CREATE TABLE conversation_patterns (
    id BIGINT NOT NULL SERIAL PRIMARY KEY,
    interview_session_id VARCHAR(255) NOT NULL UNIQUE,
    candidate_id BIGINT NOT NULL,
    recruiter_id BIGINT NULL,
    
    -- Overall Conversation Metrics
    total_duration INT NOT NULL COMMENT 'in seconds',
    total_questions INT NOT NULL,
    total_words_spoken INT NOT NULL,
    candidate_words INT NOT NULL,
    ai_words INT NOT NULL,
    
    -- Speaking Time Analysis
    candidate_speaking_time INT NOT NULL COMMENT 'in seconds',
    ai_speaking_time INT NOT NULL COMMENT 'in seconds',
    silence_time INT NOT NULL COMMENT 'in seconds',
    overlap_time INT NOT NULL COMMENT 'in seconds',
    
    -- Response Time Patterns
    average_response_time DOUBLE NOT NULL COMMENT 'in seconds',
    median_response_time DOUBLE NOT NULL COMMENT 'in seconds',
    max_response_time DOUBLE NOT NULL COMMENT 'in seconds',
    min_response_time DOUBLE NOT NULL COMMENT 'in seconds',
    response_time_variance DOUBLE NOT NULL,
    
    -- Communication Quality Metrics
    clarity_score DECIMAL(5,4) NOT NULL COMMENT 'How clear candidate communication is',
    coherence_score DECIMAL(5,4) NOT NULL COMMENT 'How coherent the conversation flow is',
    engagement_score DECIMAL(5,4) NOT NULL COMMENT 'Overall candidate engagement',
    confidence_score DECIMAL(5,4) NOT NULL COMMENT 'Candidate confidence level',
    professionalism_score DECIMAL(5,4) NOT NULL COMMENT 'Professional communication level',
    
    -- Language and Speech Patterns
    speaking_pace DOUBLE NOT NULL COMMENT 'words per minute',
    pause_frequency DOUBLE NOT NULL COMMENT 'pauses per minute',
    average_pause_duration DOUBLE NOT NULL COMMENT 'in seconds',
    filler_word_count INT NOT NULL COMMENT 'um, uh, like, etc.',
    vocabulary_richness DECIMAL(5,4) NOT NULL COMMENT 'unique words / total words',
    technical_term_usage INT NOT NULL COMMENT 'count of technical terms used',
    
    -- Emotional and Sentiment Analysis
    overall_sentiment DECIMAL(5,4) NOT NULL COMMENT '-1 (negative) to 1 (positive)',
    sentiment_progression TEXT NULL COMMENT 'JSON array of sentiment scores over time',
    stress_indicators INT NOT NULL COMMENT 'count of stress-related speech patterns',
    enthusiasm_level DECIMAL(5,4) NOT NULL,
    
    -- Question-Response Patterns
    direct_answers INT NOT NULL COMMENT 'questions answered directly',
    clarification_requests INT NOT NULL COMMENT 'times candidate asked for clarification',
    follow_up_questions INT NOT NULL COMMENT 'candidate-initiated questions',
    incomplete_responses INT NOT NULL COMMENT 'responses that seemed cut off',
    topic_changes INT NOT NULL COMMENT 'times conversation changed topics abruptly',
    
    -- Interaction Quality
    interruption_count INT NOT NULL COMMENT 'times candidate interrupted AI',
    ai_interruption_count INT NOT NULL COMMENT 'times AI interrupted candidate',
    natural_flow_score DECIMAL(5,4) NOT NULL COMMENT 'how natural the conversation felt',
    rapport_score DECIMAL(5,4) NOT NULL COMMENT 'quality of rapport between candidate and AI',
    
    -- Technical Communication Assessment
    explanation_quality DECIMAL(5,4) NOT NULL COMMENT 'quality of technical explanations',
    concept_articulation DECIMAL(5,4) NOT NULL COMMENT 'ability to articulate complex concepts',
    question_understanding DECIMAL(5,4) NOT NULL COMMENT 'how well candidate understood questions',
    
    -- Advanced Patterns (JSON storage for flexibility)
    conversation_topics TEXT NULL COMMENT 'JSON array of identified topics',
    key_phrases TEXT NULL COMMENT 'JSON array of important phrases used',
    speech_patterns TEXT NULL COMMENT 'JSON object with detailed speech analysis',
    communication_style VARCHAR(50) NULL COMMENT 'formal, casual, technical, conversational',
    interview_phase_performance TEXT NULL COMMENT 'JSON object with phase-wise metrics',
    
    -- Comparative Analysis
    peer_comparison_score DECIMAL(5,4) NOT NULL COMMENT 'compared to other candidates in similar roles',
    improvement_areas TEXT NULL COMMENT 'JSON array of identified improvement areas',
    strengths_identified TEXT NULL COMMENT 'JSON array of communication strengths',
    
    -- Metadata
    analysis_version VARCHAR(50) NULL COMMENT 'version of analysis algorithm used',
    confidence_level DECIMAL(5,4) NOT NULL COMMENT 'confidence in the analysis results',
    processing_time INT NOT NULL COMMENT 'time taken to analyze (in milliseconds)',
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    
    -- Foreign Keys
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    
    -- Indexes
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    
    -- Composite indexes for analysis queries
    -- INDEX,
    -- INDEX,
    -- INDEX
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
