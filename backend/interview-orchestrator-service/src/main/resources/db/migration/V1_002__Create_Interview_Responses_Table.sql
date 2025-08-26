-- Interview Responses Table
-- For capturing and analyzing candidate responses (speech, text, code)
CREATE TABLE interview_responses (
    response_id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL,
    question_id BIGINT NOT NULL,
    question_sequence INTEGER NOT NULL,
    
    -- Multi-modal Response Data
    transcript_text TEXT NULL, -- Speech-to-text transcription
    code_submission TEXT NULL, -- Code typed in Monaco editor
    chat_text TEXT NULL, -- Text typed in chat box
    combined_response TEXT NULL, -- Merged transcript + code + chat
    
    -- Timing and Interaction Data
    response_start_time TIMESTAMP NULL,
    response_end_time TIMESTAMP NULL,
    think_time_seconds INTEGER NULL, -- Time before candidate started responding
    response_duration_seconds INTEGER NULL,
    spacebar_pressed BOOLEAN NOT NULL DEFAULT FALSE, -- Did candidate signal completion?
    auto_terminated BOOLEAN NOT NULL DEFAULT FALSE, -- Was response auto-ended due to silence?
    
    -- IRT and Scoring Data
    question_difficulty DECIMAL(10,4) NULL, -- Question difficulty parameter (b)
    discrimination DECIMAL(10,4) NULL, -- Question discrimination parameter (a)
    correct_answer BOOLEAN NULL, -- For objective questions
    partial_credit DECIMAL(5,4) NULL, -- 0.0 to 1.0 for subjective scoring
    ai_score DECIMAL(5,4) NULL, -- Overall AI-generated score
    human_score DECIMAL(5,4) NULL, -- Human reviewer override score
    
    -- AI Analysis Results
    sentiment_score DECIMAL(5,4) NULL, -- Sentiment analysis of response
    confidence_score DECIMAL(5,4) NULL, -- AI confidence in candidate answer
    clarity_score DECIMAL(5,4) NULL, -- Speech clarity and articulation
    relevance_score DECIMAL(5,4) NULL, -- Answer relevance to question
    
    -- Technical Analysis (for coding responses)
    code_quality_score DECIMAL(5,4) NULL,
    algorithm_correctness DECIMAL(5,4) NULL,
    code_efficiency_score DECIMAL(5,4) NULL,
    syntax_errors_count INTEGER NULL,
    
    -- Bias Detection
    bias_flag BOOLEAN NOT NULL DEFAULT FALSE,
    bias_details TEXT NULL, -- JSON with bias analysis details
    
    -- Follow-up and Adaptation
    needs_followup BOOLEAN NOT NULL DEFAULT FALSE,
    followup_reason TEXT NULL,
    adapted_next_difficulty DECIMAL(5,4) NULL, -- Suggested difficulty for next question
    
    -- Metadata
    audio_file_path VARCHAR(500) NULL, -- Path to recorded audio segment
    video_timestamp_start BIGINT NULL, -- Milliseconds from interview start
    video_timestamp_end BIGINT NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Keys
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE
);

-- Create indexes for interview_responses
CREATE INDEX idx_interview_responses_session_id ON interview_responses (session_id);
CREATE INDEX idx_interview_responses_question_id ON interview_responses (question_id);
CREATE INDEX idx_interview_responses_question_sequence ON interview_responses (question_sequence);
CREATE INDEX idx_interview_responses_response_start_time ON interview_responses (response_start_time);
CREATE INDEX idx_interview_responses_ai_score ON interview_responses (ai_score);
CREATE INDEX idx_interview_responses_bias_flag ON interview_responses (bias_flag);
CREATE INDEX idx_interview_responses_created_at ON interview_responses (created_at);

-- Composite indexes for common queries
CREATE INDEX idx_interview_responses_session_sequence ON interview_responses (session_id, question_sequence);
CREATE INDEX idx_interview_responses_session_question ON interview_responses (session_id, question_id);

-- Response AI Analysis (flexible storage for multiple analysis types)
CREATE TABLE response_ai_analysis (
    response_id BIGINT NOT NULL,
    analysis_type VARCHAR(100) NOT NULL, -- emotion, technical_accuracy, communication_quality, etc.
    analysis_score DECIMAL(10,4) NOT NULL,
    confidence_level DECIMAL(5,4) NULL, -- AI confidence in this analysis
    analysis_details TEXT NULL, -- JSON with detailed analysis results
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (response_id, analysis_type),
    FOREIGN KEY (response_id) REFERENCES interview_responses(response_id) ON DELETE CASCADE
);

-- Create indexes for response_ai_analysis
CREATE INDEX idx_response_ai_analysis_analysis_type ON response_ai_analysis (analysis_type);
CREATE INDEX idx_response_ai_analysis_analysis_score ON response_ai_analysis (analysis_score);
