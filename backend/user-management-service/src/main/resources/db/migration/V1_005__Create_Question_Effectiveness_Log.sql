-- Question Effectiveness Log Table
-- For continuous learning and improvement of the adaptive questioning system
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
    response_time DOUBLE NOT NULL,
    candidate_answer TEXT NULL,
    expected_answer TEXT NULL,
    partial_credit_score DECIMAL(5,4) NULL,
    
    -- IRT Parameters Used at Time of Question
    discrimination_parameter_used DOUBLE NOT NULL,
    difficulty_parameter_used DOUBLE NOT NULL,
    guessing_parameter_used DOUBLE NOT NULL,
    upper_asymptote_used DOUBLE NOT NULL,
    
    -- Information Theory Metrics
    information_provided DOUBLE NOT NULL,
    expected_information DOUBLE NOT NULL,
    information_efficiency DECIMAL(5,4) NOT NULL,
    
    -- Prediction Accuracy
    predicted_probability DECIMAL(5,4) NOT NULL,
    prediction_error DOUBLE NOT NULL,
    log_likelihood DOUBLE NOT NULL,
    
    -- Bias and Fairness Metrics
    bias_indicators TEXT NULL,
    demographic_group VARCHAR(100) NULL,
    expected_score_demographic DOUBLE NULL,
    actual_score_demographic DOUBLE NULL,
    
    -- Context and Environment
    question_position INT NOT NULL,
    interview_stage VARCHAR(50) NULL,
    job_role VARCHAR(100) NULL,
    technologies_assessed TEXT NULL,
    interviewer_id BIGINT NULL,
    
    -- Performance Indicators
    engagement_score DECIMAL(5,4) NULL,
    confidence_score DECIMAL(5,4) NULL,
    difficulty_perception DECIMAL(5,4) NULL,
    
    -- Adaptive Algorithm Metrics
    selection_algorithm VARCHAR(100) NULL,
    selection_score DOUBLE NOT NULL,
    competing_questions TEXT NULL,
    selection_reasons TEXT NULL,
    
    -- Quality Metrics
    question_clarity_score DECIMAL(5,4) NULL,
    answer_quality_score DECIMAL(5,4) NULL,
    technical_accuracy_score DECIMAL(5,4) NULL,
    
    -- Metadata
    question_version VARCHAR(50) NULL,
    system_version VARCHAR(50) NULL,
    additional_metrics TEXT NULL,
    
    logged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Keys (assuming questions table exists in user management)
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    
    -- Indexes
    
    -- Composite indexes for analysis queries
    -- INDEX
