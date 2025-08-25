-- Bias Detection Results Table
-- For tracking and monitoring bias detection results across interviews
CREATE TABLE bias_detection_results (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    interview_session_id VARCHAR(255) NOT NULL,
    candidate_id BIGINT NOT NULL,
    analysis_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Overall Bias Scores
    overall_bias_score DECIMAL(5,4) NOT NULL COMMENT '0.0 (no bias) to 1.0 (high bias)',
    bias_confidence DECIMAL(5,4) NOT NULL COMMENT 'Confidence in bias detection',
    bias_detected BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Demographic Bias Analysis
    demographic_bias_score DECIMAL(5,4) NULL,
    demographic_indicators TEXT NULL COMMENT 'JSON array of detected demographic indicators',
    age_bias_score DECIMAL(5,4) NULL,
    gender_bias_score DECIMAL(5,4) NULL,
    ethnicity_bias_score DECIMAL(5,4) NULL,
    accent_bias_score DECIMAL(5,4) NULL,
    
    -- Linguistic Bias Analysis
    linguistic_bias_score DECIMAL(5,4) NULL,
    language_complexity_bias DECIMAL(5,4) NULL,
    vocabulary_bias DECIMAL(5,4) NULL,
    grammar_bias DECIMAL(5,4) NULL,
    pronunciation_bias DECIMAL(5,4) NULL,
    
    -- Cultural Bias Analysis
    cultural_bias_score DECIMAL(5,4) NULL,
    cultural_references_bias DECIMAL(5,4) NULL,
    communication_style_bias DECIMAL(5,4) NULL,
    context_understanding_bias DECIMAL(5,4) NULL,
    
    -- Technical/Educational Bias
    educational_background_bias DECIMAL(5,4) NULL,
    technical_terminology_bias DECIMAL(5,4) NULL,
    prior_experience_bias DECIMAL(5,4) NULL,
    coding_style_bias DECIMAL(5,4) NULL,
    
    -- Cognitive Bias Analysis
    confirmation_bias_score DECIMAL(5,4) NULL COMMENT 'System confirmation bias',
    anchoring_bias_score DECIMAL(5,4) NULL COMMENT 'Anchoring on first impressions',
    halo_effect_score DECIMAL(5,4) NULL COMMENT 'Halo effect in scoring',
    recency_bias_score DECIMAL(5,4) NULL COMMENT 'Bias toward recent responses',
    
    -- Fairness Metrics
    statistical_parity DECIMAL(5,4) NULL COMMENT 'Statistical parity measure',
    equalized_odds DECIMAL(5,4) NULL COMMENT 'Equalized odds measure',
    calibration_score DECIMAL(5,4) NULL COMMENT 'Calibration across groups',
    individual_fairness DECIMAL(5,4) NULL COMMENT 'Individual fairness measure',
    
    -- Question-Specific Bias
    question_selection_bias DECIMAL(5,4) NULL COMMENT 'Bias in question selection',
    difficulty_calibration_bias DECIMAL(5,4) NULL COMMENT 'Bias in difficulty assessment',
    scoring_consistency_bias DECIMAL(5,4) NULL COMMENT 'Inconsistent scoring patterns',
    
    -- Intersectional Analysis
    intersectional_bias_detected BOOLEAN NOT NULL DEFAULT FALSE,
    intersectional_categories TEXT NULL COMMENT 'JSON array of intersecting bias categories',
    intersectional_severity DECIMAL(5,4) NULL,
    
    -- Detected Patterns
    bias_patterns TEXT NULL COMMENT 'JSON array of specific bias patterns detected',
    discriminatory_language TEXT NULL COMMENT 'JSON array of potentially discriminatory phrases',
    unfair_advantages TEXT NULL COMMENT 'JSON array of unfair advantages detected',
    systemic_barriers TEXT NULL COMMENT 'JSON array of systemic barriers identified',
    
    -- Mitigation Recommendations
    mitigation_suggestions TEXT NULL COMMENT 'JSON array of suggested mitigations',
    intervention_required BOOLEAN NOT NULL DEFAULT FALSE,
    intervention_type VARCHAR(100) NULL COMMENT 'Type of intervention needed',
    intervention_urgency ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NULL,
    
    -- Context and Metadata
    job_role VARCHAR(100) NULL,
    experience_level VARCHAR(50) NULL,
    interview_stage VARCHAR(50) NULL,
    question_types_analyzed TEXT NULL COMMENT 'JSON array of question types',
    technologies_assessed TEXT NULL COMMENT 'JSON array of technologies',
    
    -- Analysis Details
    detection_algorithm_version VARCHAR(50) NULL,
    processing_time_ms INT NOT NULL COMMENT 'Processing time in milliseconds',
    data_quality_score DECIMAL(5,4) NULL COMMENT 'Quality of input data for analysis',
    false_positive_likelihood DECIMAL(5,4) NULL COMMENT 'Estimated likelihood of false positive',
    
    -- Comparative Analysis
    peer_comparison_data TEXT NULL COMMENT 'JSON with peer comparison statistics',
    historical_baseline DECIMAL(5,4) NULL COMMENT 'Historical bias baseline for similar profiles',
    deviation_from_expected DECIMAL(5,4) NULL COMMENT 'Deviation from expected behavior',
    
    -- Remediation Tracking
    remediation_applied BOOLEAN NOT NULL DEFAULT FALSE,
    remediation_details TEXT NULL COMMENT 'JSON with details of applied remediation',
    remediation_effectiveness DECIMAL(5,4) NULL COMMENT 'Effectiveness of applied remediation',
    follow_up_required BOOLEAN NOT NULL DEFAULT FALSE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Foreign Keys
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_interview_session_id (interview_session_id),
    INDEX idx_candidate_id (candidate_id),
    INDEX idx_overall_bias_score (overall_bias_score),
    INDEX idx_bias_detected (bias_detected),
    INDEX idx_intervention_required (intervention_required),
    INDEX idx_intervention_urgency (intervention_urgency),
    INDEX idx_job_role (job_role),
    INDEX idx_analysis_timestamp (analysis_timestamp),
    INDEX idx_created_at (created_at),
    
    -- Composite indexes for bias monitoring
    INDEX idx_bias_monitoring (bias_detected, overall_bias_score, analysis_timestamp),
    INDEX idx_demographic_analysis (demographic_bias_score, age_bias_score, gender_bias_score),
    INDEX idx_fairness_metrics (statistical_parity, equalized_odds, individual_fairness),
    INDEX idx_intervention_tracking (intervention_required, intervention_urgency, remediation_applied)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
