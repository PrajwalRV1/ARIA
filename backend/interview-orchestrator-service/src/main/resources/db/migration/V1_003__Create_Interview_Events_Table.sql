-- Interview Events Table
-- For tracking all events during an interview session (user interactions, system events, AI decisions)
CREATE TABLE interview_events (
    event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL COMMENT 'SESSION_STARTED, QUESTION_PRESENTED, SPACEBAR_PRESSED, etc.',
    event_source VARCHAR(50) NULL COMMENT 'USER, SYSTEM, AI_ENGINE, SPEECH_SERVICE, etc.',
    event_description VARCHAR(500) NULL,
    
    -- Event Data
    event_data TEXT NULL COMMENT 'JSON format for flexible event data',
    question_id BIGINT NULL COMMENT 'Associated question if applicable',
    response_id BIGINT NULL COMMENT 'Associated response if applicable',
    
    -- Timing Information
    timestamp_millis BIGINT NULL COMMENT 'Milliseconds from interview start',
    duration_millis BIGINT NULL COMMENT 'Duration of the event if applicable',
    
    -- Severity and Priority
    severity ENUM('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL') NOT NULL DEFAULT 'INFO',
    requires_attention BOOLEAN NOT NULL DEFAULT FALSE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Keys
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (response_id) REFERENCES interview_responses(response_id) ON DELETE SET NULL,
    
    -- Indexes
    INDEX idx_session_id (session_id),
    INDEX idx_event_type (event_type),
    INDEX idx_event_source (event_source),
    INDEX idx_question_id (question_id),
    INDEX idx_response_id (response_id),
    INDEX idx_timestamp_millis (timestamp_millis),
    INDEX idx_severity (severity),
    INDEX idx_requires_attention (requires_attention),
    INDEX idx_created_at (created_at),
    
    -- Composite indexes for common queries
    INDEX idx_session_event_type (session_id, event_type),
    INDEX idx_session_timestamp (session_id, timestamp_millis),
    INDEX idx_session_severity (session_id, severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Event Metadata (flexible key-value storage for additional event data)
CREATE TABLE event_metadata (
    event_id BIGINT NOT NULL,
    metadata_key VARCHAR(100) NOT NULL,
    metadata_value TEXT NULL,
    
    PRIMARY KEY (event_id, metadata_key),
    FOREIGN KEY (event_id) REFERENCES interview_events(event_id) ON DELETE CASCADE,
    
    INDEX idx_metadata_key (metadata_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
