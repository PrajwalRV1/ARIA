-- Interview Sessions Table
-- Core table for managing AI-driven adaptive interview sessions
CREATE TABLE interview_sessions (
    session_id VARCHAR(36) NOT NULL PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    recruiter_id BIGINT NOT NULL,
    status ENUM('SCHEDULED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'TECHNICAL_ISSUES') NOT NULL DEFAULT 'SCHEDULED',
    
    -- Timing Information
    scheduled_start_time TIMESTAMP NULL,
    actual_start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    duration_minutes INT NULL,
    
    -- IRT Parameters for adaptive questioning
    theta DECIMAL(10,4) NOT NULL DEFAULT 0.0 COMMENT 'Candidate ability estimate',
    standard_error DECIMAL(10,4) NOT NULL DEFAULT 1.0 COMMENT 'Uncertainty in theta estimate',
    min_questions INT NOT NULL DEFAULT 10,
    max_questions INT NOT NULL DEFAULT 30,
    current_question_count INT NOT NULL DEFAULT 0,
    
    -- Meeting and WebRTC Configuration
    meeting_link VARCHAR(500) NULL,
    webrtc_room_id VARCHAR(255) NULL,
    
    -- Question Selection and Flow
    current_question_id BIGINT NULL,
    
    -- Transcript and Communication
    full_transcript LONGTEXT NULL,
    code_submissions LONGTEXT NULL COMMENT 'JSON format for code/chat submissions',
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_candidate_id (candidate_id),
    INDEX idx_recruiter_id (recruiter_id),
    INDEX idx_status (status),
    INDEX idx_scheduled_start_time (scheduled_start_time),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Session ICE Servers (for WebRTC)
CREATE TABLE session_ice_servers (
    session_id VARCHAR(36) NOT NULL,
    ice_server_url VARCHAR(255) NOT NULL,
    PRIMARY KEY (session_id, ice_server_url),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Session Question Pool
CREATE TABLE session_question_pool (
    session_id VARCHAR(36) NOT NULL,
    question_id BIGINT NOT NULL,
    PRIMARY KEY (session_id, question_id),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
    INDEX idx_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Session Asked Questions (for tracking)
CREATE TABLE session_asked_questions (
    session_id VARCHAR(36) NOT NULL,
    question_id BIGINT NOT NULL,
    asked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, question_id),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
    INDEX idx_asked_at (asked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Session AI Metrics (flexible key-value storage)
CREATE TABLE session_ai_metrics (
    session_id VARCHAR(36) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(10,4) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, metric_name),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Session Technology Stack
CREATE TABLE session_tech_stack (
    session_id VARCHAR(36) NOT NULL,
    technology VARCHAR(100) NOT NULL,
    PRIMARY KEY (session_id, technology),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
