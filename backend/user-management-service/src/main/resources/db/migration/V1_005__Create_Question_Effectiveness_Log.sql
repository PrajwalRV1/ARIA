-- Question Effectiveness Log Table
-- For continuous learning and improvement of the adaptive questioning system

-- Drop table if it exists (for clean migration)
DROP TABLE IF EXISTS question_effectiveness_log;

CREATE TABLE question_effectiveness_log (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    interview_session_id VARCHAR(255) NOT NULL,
    candidate_id BIGINT NOT NULL,
    
    -- IRT Parameters Before/After
    candidate_theta_before DECIMAL(10,4) NOT NULL,
    candidate_theta_after DECIMAL(10,4) NOT NULL,
    standard_error_before DECIMAL(10,4) NOT NULL,
    standard_error_after DECIMAL(10,4) NOT NULL,
    
    -- Response Data
    response_correct BOOLEAN NOT NULL,
    response_time DOUBLE PRECISION NOT NULL,
    candidate_answer TEXT,
    expected_answer TEXT,
    partial_credit_score DECIMAL(5,4),
    
    -- IRT Parameters Used at Time of Question
    discrimination_parameter_used DOUBLE PRECISION NOT NULL,
    difficulty_parameter_used DOUBLE PRECISION NOT NULL,
    guessing_parameter_used DOUBLE PRECISION NOT NULL,
    upper_asymptote_used DOUBLE PRECISION NOT NULL,
    
    -- Information Theory Metrics
    information_provided DOUBLE PRECISION NOT NULL,
    expected_information DOUBLE PRECISION NOT NULL,
    information_efficiency DECIMAL(5,4) NOT NULL,
    
    -- Prediction Accuracy
    predicted_probability DECIMAL(5,4) NOT NULL,
    prediction_error DOUBLE PRECISION NOT NULL,
    log_likelihood DOUBLE PRECISION NOT NULL,
    
    -- Bias and Fairness Metrics
    bias_indicators TEXT,
    demographic_group VARCHAR(100),
    expected_score_demographic DOUBLE PRECISION,
    actual_score_demographic DOUBLE PRECISION,
    
    -- Context and Environment
    question_position INT NOT NULL,
    interview_stage VARCHAR(50),
    job_role VARCHAR(100),
    technologies_assessed TEXT,
    interviewer_id BIGINT,
    
    -- Performance Indicators
    engagement_score DECIMAL(5,4),
    confidence_score DECIMAL(5,4),
    difficulty_perception DECIMAL(5,4),
    
    -- Adaptive Algorithm Metrics
    selection_algorithm VARCHAR(100),
    selection_score DOUBLE PRECISION NOT NULL,
    competing_questions TEXT,
    selection_reasons TEXT,
    
    -- Quality Metrics
    question_clarity_score DECIMAL(5,4),
    answer_quality_score DECIMAL(5,4),
    technical_accuracy_score DECIMAL(5,4),
    
    -- Metadata
    question_version VARCHAR(50),
    system_version VARCHAR(50),
    additional_metrics TEXT,
    
    logged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_question_effectiveness_log_question_id ON question_effectiveness_log(question_id);
CREATE INDEX idx_question_effectiveness_log_candidate_id ON question_effectiveness_log(candidate_id);
CREATE INDEX idx_question_effectiveness_log_interview_session ON question_effectiveness_log(interview_session_id);
CREATE INDEX idx_question_effectiveness_log_logged_at ON question_effectiveness_log(logged_at);
CREATE INDEX idx_question_effectiveness_log_job_role ON question_effectiveness_log(job_role);
CREATE INDEX idx_question_effectiveness_log_interview_stage ON question_effectiveness_log(interview_stage);
