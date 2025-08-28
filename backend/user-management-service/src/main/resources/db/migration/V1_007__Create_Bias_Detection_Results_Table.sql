-- Bias Detection Results Table (Simplified)
-- For tracking bias detection results across interviews
CREATE TABLE bias_detection_results (
    id BIGSERIAL PRIMARY KEY,
    interview_session_id VARCHAR(255) NOT NULL,
    candidate_id BIGINT NOT NULL,
    analysis_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Overall Bias Scores
    overall_bias_score DECIMAL(5,4) NOT NULL DEFAULT 0.0,
    bias_confidence DECIMAL(5,4) NOT NULL DEFAULT 0.0,
    bias_detected BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Bias Analysis Results (JSON format for flexibility)
    bias_analysis_data TEXT,
    
    -- Mitigation Recommendations
    mitigation_suggestions TEXT,
    intervention_required BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Context and Metadata
    job_role VARCHAR(100),
    interview_stage VARCHAR(50),
    
    -- Analysis Details
    detection_algorithm_version VARCHAR(50),
    processing_time_ms INT NOT NULL DEFAULT 0,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_bias_detection_results_interview_session ON bias_detection_results(interview_session_id);
CREATE INDEX idx_bias_detection_results_candidate_id ON bias_detection_results(candidate_id);
CREATE INDEX idx_bias_detection_results_created_at ON bias_detection_results(created_at);
CREATE INDEX idx_bias_detection_results_bias_detected ON bias_detection_results(bias_detected);
