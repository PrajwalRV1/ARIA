-- Conversation Patterns Table (Simplified)
-- For analysis of communication patterns during interviews

-- Drop table if it exists (for clean migration)
DROP TABLE IF EXISTS conversation_patterns;

CREATE TABLE conversation_patterns (
    id BIGSERIAL PRIMARY KEY,
    interview_session_id VARCHAR(255) NOT NULL UNIQUE,
    candidate_id BIGINT NOT NULL,
    
    -- Basic Conversation Metrics
    total_duration INT NOT NULL DEFAULT 0,
    total_questions INT NOT NULL DEFAULT 0,
    average_response_time DOUBLE PRECISION DEFAULT 0.0,
    
    -- Communication Quality Metrics
    clarity_score DECIMAL(5,4) DEFAULT 0.0,
    engagement_score DECIMAL(5,4) DEFAULT 0.0,
    confidence_score DECIMAL(5,4) DEFAULT 0.0,
    
    -- Analysis Results (JSON format for flexibility)
    analysis_data TEXT,
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_conversation_patterns_interview_session ON conversation_patterns(interview_session_id);
CREATE INDEX idx_conversation_patterns_candidate_id ON conversation_patterns(candidate_id);
CREATE INDEX idx_conversation_patterns_created_at ON conversation_patterns(created_at);
