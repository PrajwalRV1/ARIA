-- Bias Detection Results Table
-- For tracking and monitoring bias detection results across interviews
CREATE TABLE bias_detection_results (
    id BIGSERIAL PRIMARY KEY,
    interview_session_id VARCHAR(255) NOT NULL,
    candidate_id BIGINT NOT NULL,
    analysis_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Overall Bias Scores
    overall_bias_score DECIMAL(5,4) NOT NULL,
    bias_confidence DECIMAL(5,4) NOT NULL,
    bias_detected BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Demographic Bias Analysis
    demographic_bias_score DECIMAL(5,4) NULL,
    demographic_indicators TEXT NULL,
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
    confirmation_bias_score DECIMAL(5,4) NULL,
    anchoring_bias_score DECIMAL(5,4) NULL,
    halo_effect_score DECIMAL(5,4) NULL,
    recency_bias_score DECIMAL(5,4) NULL,
    
    -- Fairness Metrics
    statistical_parity DECIMAL(5,4) NULL,
    equalized_odds DECIMAL(5,4) NULL,
    calibration_score DECIMAL(5,4) NULL,
    individual_fairness DECIMAL(5,4) NULL,
    
    -- Question-Specific Bias
    question_selection_bias DECIMAL(5,4) NULL,
    difficulty_calibration_bias DECIMAL(5,4) NULL,
    scoring_consistency_bias DECIMAL(5,4) NULL,
    
    -- Intersectional Analysis
    intersectional_bias_detected BOOLEAN NOT NULL DEFAULT FALSE,
    intersectional_categories TEXT NULL,
    intersectional_severity DECIMAL(5,4) NULL,
    
    -- Detected Patterns
    bias_patterns TEXT NULL,
    discriminatory_language TEXT NULL,
    unfair_advantages TEXT NULL,
    systemic_barriers TEXT NULL,
    
    -- Mitigation Recommendations
    mitigation_suggestions TEXT NULL,
    intervention_required BOOLEAN NOT NULL DEFAULT FALSE,
    intervention_type VARCHAR(100) NULL,
    intervention_urgency VARCHAR(20) NULL,
    
    -- Context and Metadata
    job_role VARCHAR(100) NULL,
    experience_level VARCHAR(50) NULL,
    interview_stage VARCHAR(50) NULL,
    question_types_analyzed TEXT NULL,
    technologies_assessed TEXT NULL,
    
    -- Analysis Details
    detection_algorithm_version VARCHAR(50) NULL,
    processing_time_ms INT NOT NULL,
    data_quality_score DECIMAL(5,4) NULL,
    false_positive_likelihood DECIMAL(5,4) NULL,
    
    -- Comparative Analysis
    peer_comparison_data TEXT NULL,
    historical_baseline DECIMAL(5,4) NULL,
    deviation_from_expected DECIMAL(5,4) NULL,
    
    -- Remediation Tracking
    remediation_applied BOOLEAN NOT NULL DEFAULT FALSE,
    remediation_details TEXT NULL,
    remediation_effectiveness DECIMAL(5,4) NULL,
    follow_up_required BOOLEAN NOT NULL DEFAULT FALSE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    
    -- Foreign Keys
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    
    -- Indexes
    
    -- Composite indexes for bias monitoring
    -- INDEX
