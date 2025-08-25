-- Interview Responses Table
-- For capturing and analyzing candidate responses (speech, text, code)
CREATE TABLE interview_responses (
    response_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    question_id BIGINT NOT NULL,
    question_sequence INT NOT NULL,
    
    -- Multi-modal Response Data
    transcript_text TEXT NULL COMMENT 'Speech-to-text transcription',
    code_submission TEXT NULL COMMENT 'Code typed in Monaco editor',
    chat_text TEXT NULL COMMENT 'Text typed in chat box',
    combined_response TEXT NULL COMMENT 'Merged transcript + code + chat',
    
    -- Timing and Interaction Data
    response_start_time TIMESTAMP NULL,
    response_end_time TIMESTAMP NULL,
    think_time_seconds INT NULL COMMENT 'Time before candidate started responding',
    response_duration_seconds INT NULL,
    spacebar_pressed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Did candidate signal completion?',
    auto_terminated BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Was response auto-ended due to silence?',
    
    -- IRT and Scoring Data
    question_difficulty DECIMAL(10,4) NULL COMMENT 'Question difficulty parameter (b)',
    discrimination DECIMAL(10,4) NULL COMMENT 'Question discrimination parameter (a)',
    correct_answer BOOLEAN NULL COMMENT 'For objective questions',
    partial_credit DECIMAL(5,4) NULL COMMENT '0.0 to 1.0 for subjective scoring',
    ai_score DECIMAL(5,4) NULL COMMENT 'Overall AI-generated score',
    human_score DECIMAL(5,4) NULL COMMENT 'Human reviewer override score',
    
    -- AI Analysis Results
    sentiment_score DECIMAL(5,4) NULL COMMENT 'Sentiment analysis of response',
    confidence_score DECIMAL(5,4) NULL COMMENT 'AI confidence in candidate answer',
    clarity_score DECIMAL(5,4) NULL COMMENT 'Speech clarity and articulation',
    relevance_score DECIMAL(5,4) NULL COMMENT 'Answer relevance to question',
    
    -- Technical Analysis (for coding responses)
    code_quality_score DECIMAL(5,4) NULL,
    algorithm_correctness DECIMAL(5,4) NULL,
    code_efficiency_score DECIMAL(5,4) NULL,
    syntax_errors_count INT NULL,
    
    -- Bias Detection
    bias_flag BOOLEAN NOT NULL DEFAULT FALSE,
    bias_details TEXT NULL COMMENT 'JSON with bias analysis details',
    
    -- Follow-up and Adaptation
    needs_followup BOOLEAN NOT NULL DEFAULT FALSE,
    followup_reason TEXT NULL,
    adapted_next_difficulty DECIMAL(5,4) NULL COMMENT 'Suggested difficulty for next question',
    
    -- Metadata
    audio_file_path VARCHAR(500) NULL COMMENT 'Path to recorded audio segment',
    video_timestamp_start BIGINT NULL COMMENT 'Milliseconds from interview start',
    video_timestamp_end BIGINT NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Keys
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_session_id (session_id),
    INDEX idx_question_id (question_id),
    INDEX idx_question_sequence (question_sequence),
    INDEX idx_response_start_time (response_start_time),
    INDEX idx_ai_score (ai_score),
    INDEX idx_bias_flag (bias_flag),
    INDEX idx_created_at (created_at),
    
    -- Composite indexes for common queries
    INDEX idx_session_sequence (session_id, question_sequence),
    INDEX idx_session_question (session_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Response AI Analysis (flexible storage for multiple analysis types)
CREATE TABLE response_ai_analysis (
    response_id BIGINT NOT NULL,
    analysis_type VARCHAR(100) NOT NULL COMMENT 'emotion, technical_accuracy, communication_quality, etc.',
    analysis_score DECIMAL(10,4) NOT NULL,
    confidence_level DECIMAL(5,4) NULL COMMENT 'AI confidence in this analysis',
    analysis_details TEXT NULL COMMENT 'JSON with detailed analysis results',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (response_id, analysis_type),
    FOREIGN KEY (response_id) REFERENCES interview_responses(response_id) ON DELETE CASCADE,
    
    INDEX idx_analysis_type (analysis_type),
    INDEX idx_analysis_score (analysis_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
