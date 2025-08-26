-- Interview Events Table
-- For tracking all events during an interview session (user interactions, system events, AI decisions)

-- Create ENUM type for event severity
CREATE TYPE event_severity AS ENUM ('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL');

CREATE TABLE interview_events (
    event_id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- SESSION_STARTED, QUESTION_PRESENTED, SPACEBAR_PRESSED, etc.
    event_source VARCHAR(50) NULL, -- USER, SYSTEM, AI_ENGINE, SPEECH_SERVICE, etc.
    event_description VARCHAR(500) NULL,
    
    -- Event Data
    event_data TEXT NULL, -- JSON format for flexible event data
    question_id BIGINT NULL, -- Associated question if applicable
    response_id BIGINT NULL, -- Associated response if applicable
    
    -- Timing Information
    timestamp_millis BIGINT NULL, -- Milliseconds from interview start
    duration_millis BIGINT NULL, -- Duration of the event if applicable
    
    -- Severity and Priority
    severity event_severity NOT NULL DEFAULT 'INFO',
    requires_attention BOOLEAN NOT NULL DEFAULT FALSE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Keys
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (response_id) REFERENCES interview_responses(response_id) ON DELETE SET NULL
);

-- Create indexes for interview_events
CREATE INDEX idx_interview_events_session_id ON interview_events (session_id);
CREATE INDEX idx_interview_events_event_type ON interview_events (event_type);
CREATE INDEX idx_interview_events_event_source ON interview_events (event_source);
CREATE INDEX idx_interview_events_question_id ON interview_events (question_id);
CREATE INDEX idx_interview_events_response_id ON interview_events (response_id);
CREATE INDEX idx_interview_events_timestamp_millis ON interview_events (timestamp_millis);
CREATE INDEX idx_interview_events_severity ON interview_events (severity);
CREATE INDEX idx_interview_events_requires_attention ON interview_events (requires_attention);
CREATE INDEX idx_interview_events_created_at ON interview_events (created_at);

-- Composite indexes for common queries
CREATE INDEX idx_interview_events_session_event_type ON interview_events (session_id, event_type);
CREATE INDEX idx_interview_events_session_timestamp ON interview_events (session_id, timestamp_millis);
CREATE INDEX idx_interview_events_session_severity ON interview_events (session_id, severity);

-- Event Metadata (flexible key-value storage for additional event data)
CREATE TABLE event_metadata (
    event_id BIGINT NOT NULL,
    metadata_key VARCHAR(100) NOT NULL,
    metadata_value TEXT NULL,
    
    PRIMARY KEY (event_id, metadata_key),
    FOREIGN KEY (event_id) REFERENCES interview_events(event_id) ON DELETE CASCADE
);

-- Create index for event_metadata
CREATE INDEX idx_event_metadata_metadata_key ON event_metadata (metadata_key);
