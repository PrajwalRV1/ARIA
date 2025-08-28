-- Question Effectiveness Log Table
-- For continuous learning and improvement of the adaptive questioning system
CREATE TABLE question_effectiveness_log (
    id BIGINT NOT NULL SERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    interview_session_id VARCHAR(255) NOT NULL,
    candidate_id BIGINT NOT NULL,
    
    -- IRT Parameters Before/After
    candidate_theta_before DECIMAL(10,4) NOT NULL COMMENT 'Theta estimate before this question',
    candidate_theta_after DECIMAL(10,4) NOT NULL COMMENT 'Theta estimate after this question',
    standard_error_before DECIMAL(10,4) NOT NULL COMMENT 'Standard error before',
    standard_error_after DECIMAL(10,4) NOT NULL COMMENT 'Standard error after',
    
    -- Response Data
    response_correct BOOLEAN NOT NULL COMMENT 'Was the response correct',
    response_time DOUBLE NOT NULL COMMENT 'Response time in seconds',
    candidate_answer TEXT NULL,
    expected_answer TEXT NULL,
    partial_credit_score DECIMAL(5,4) NULL COMMENT 'For coding and open-ended questions',
    
    -- IRT Parameters Used at Time of Question
    discrimination_parameter_used DOUBLE NOT NULL,
    difficulty_parameter_used DOUBLE NOT NULL,
    guessing_parameter_used DOUBLE NOT NULL,
    upper_asymptote_used DOUBLE NOT NULL,
    
    -- Information Theory Metrics
    information_provided DOUBLE NOT NULL COMMENT 'Fisher information contributed',
    expected_information DOUBLE NOT NULL COMMENT 'Expected Fisher information',
    information_efficiency DECIMAL(5,4) NOT NULL COMMENT 'Actual / Expected information',
    
    -- Prediction Accuracy
    predicted_probability DECIMAL(5,4) NOT NULL COMMENT 'IRT model prediction',
    prediction_error DOUBLE NOT NULL COMMENT '|actual - predicted|',
    log_likelihood DOUBLE NOT NULL COMMENT 'Log-likelihood contribution',
    
    -- Bias and Fairness Metrics
    bias_indicators TEXT NULL COMMENT 'JSON format: {"demographic": "value", "score": 0.1}',
    demographic_group VARCHAR(100) NULL COMMENT 'For bias analysis (anonymized)',
    expected_score_demographic DOUBLE NULL,
    actual_score_demographic DOUBLE NULL,
    
    -- Context and Environment
    question_position INT NOT NULL COMMENT 'Position in interview sequence',
    interview_stage VARCHAR(50) NULL COMMENT 'screening, technical, final, etc.',
    job_role VARCHAR(100) NULL,
    technologies_assessed TEXT NULL COMMENT 'JSON array',
    interviewer_id BIGINT NULL COMMENT 'For human-in-the-loop scenarios',
    
    -- Performance Indicators
    engagement_score DECIMAL(5,4) NULL,
    confidence_score DECIMAL(5,4) NULL,
    difficulty_perception DECIMAL(5,4) NULL COMMENT '1-5 scale',
    
    -- Adaptive Algorithm Metrics
    selection_algorithm VARCHAR(100) NULL COMMENT 'Which algorithm selected this question',
    selection_score DOUBLE NOT NULL COMMENT 'Score that led to question selection',
    competing_questions TEXT NULL COMMENT 'JSON array of other candidate questions',
    selection_reasons TEXT NULL COMMENT 'JSON array of selection criteria',
    
    -- Quality Metrics
    question_clarity_score DECIMAL(5,4) NULL,
    answer_quality_score DECIMAL(5,4) NULL,
    technical_accuracy_score DECIMAL(5,4) NULL,
    
    -- Metadata
    question_version VARCHAR(50) NULL,
    system_version VARCHAR(50) NULL,
    additional_metrics TEXT NULL COMMENT 'JSON format for extensible metrics',
    
    logged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Keys (assuming questions table exists in user management)
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    
    -- Indexes
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    
    -- Composite indexes for analysis queries
    -- INDEX,
    -- INDEX,
    -- INDEX
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
