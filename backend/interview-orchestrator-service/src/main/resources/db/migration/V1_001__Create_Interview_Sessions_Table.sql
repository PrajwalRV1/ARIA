-- Interview Sessions Table
-- Core table for managing AI-driven adaptive interview sessions

-- Create ENUM type for session status
CREATE TYPE session_status AS ENUM ('SCHEDULED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'TECHNICAL_ISSUES');

CREATE TABLE interview_sessions (
    session_id UUID NOT NULL PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    recruiter_id BIGINT NOT NULL,
    status session_status NOT NULL DEFAULT 'SCHEDULED',
    
    -- Timing Information
    scheduled_start_time TIMESTAMP NULL,
    actual_start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    duration_minutes INTEGER NULL,
    
    -- IRT Parameters for adaptive questioning
    theta DECIMAL(10,4) NOT NULL DEFAULT 0.0, -- Candidate ability estimate
    standard_error DECIMAL(10,4) NOT NULL DEFAULT 1.0, -- Uncertainty in theta estimate
    min_questions INTEGER NOT NULL DEFAULT 10,
    max_questions INTEGER NOT NULL DEFAULT 30,
    current_question_count INTEGER NOT NULL DEFAULT 0,
    
    -- Meeting and WebRTC Configuration
    meeting_link VARCHAR(500) NULL,
    webrtc_room_id VARCHAR(255) NULL,
    
    -- Question Selection and Flow
    current_question_id BIGINT NULL,
    
    -- Transcript and Communication
    full_transcript TEXT NULL,
    code_submissions TEXT NULL, -- JSON format for code/chat submissions
    candidate_language_preference VARCHAR(10) NOT NULL DEFAULT 'en',
    
    -- AI and Analytics Data
    bias_score DECIMAL(10,4) NULL,
    engagement_score DECIMAL(10,4) NULL,
    technical_score DECIMAL(10,4) NULL,
    communication_score DECIMAL(10,4) NULL,
    
    -- Session Configuration
    job_role VARCHAR(100) NULL,
    experience_level VARCHAR(50) NULL,
    interview_type VARCHAR(50) NOT NULL DEFAULT 'ADAPTIVE_AI',
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_interview_sessions_candidate_id ON interview_sessions (candidate_id);
CREATE INDEX idx_interview_sessions_recruiter_id ON interview_sessions (recruiter_id);
CREATE INDEX idx_interview_sessions_status ON interview_sessions (status);
CREATE INDEX idx_interview_sessions_scheduled_start_time ON interview_sessions (scheduled_start_time);
CREATE INDEX idx_interview_sessions_created_at ON interview_sessions (created_at);

-- Session ICE Servers (for WebRTC)
CREATE TABLE session_ice_servers (
    session_id UUID NOT NULL,
    ice_server_url VARCHAR(255) NOT NULL,
    PRIMARY KEY (session_id, ice_server_url),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE
);

-- Session Question Pool
CREATE TABLE session_question_pool (
    session_id UUID NOT NULL,
    question_id BIGINT NOT NULL,
    PRIMARY KEY (session_id, question_id),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE
);

-- Create index for session_question_pool
CREATE INDEX idx_session_question_pool_question_id ON session_question_pool (question_id);

-- Session Asked Questions (for tracking)
CREATE TABLE session_asked_questions (
    session_id UUID NOT NULL,
    question_id BIGINT NOT NULL,
    asked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, question_id),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE
);

-- Create index for session_asked_questions
CREATE INDEX idx_session_asked_questions_asked_at ON session_asked_questions (asked_at);

-- Session AI Metrics (flexible key-value storage)
CREATE TABLE session_ai_metrics (
    session_id UUID NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(10,4) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, metric_name),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE
);

-- Session Technology Stack
CREATE TABLE session_tech_stack (
    session_id UUID NOT NULL,
    technology VARCHAR(100) NOT NULL,
    PRIMARY KEY (session_id, technology),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE
);
