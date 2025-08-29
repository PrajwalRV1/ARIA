-- Complete Schema Migration to Match Original User Management Service Database
-- This will recreate all tables to match the exact structure

-- First, drop all existing tables to recreate with proper structure
DROP TABLE IF EXISTS bias_detection_results CASCADE;
DROP TABLE IF EXISTS candidate_responses CASCADE;
DROP TABLE IF EXISTS candidates CASCADE;
DROP TABLE IF EXISTS candidate_skills CASCADE;
DROP TABLE IF EXISTS conversation_patterns CASCADE;
DROP TABLE IF EXISTS interview_sessions CASCADE;
DROP TABLE IF EXISTS learning_interview_outcomes CASCADE;
DROP TABLE IF EXISTS learning_question_effectiveness CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;
DROP TABLE IF EXISTS question_effectiveness_log CASCADE;
DROP TABLE IF EXISTS question_effectiveness_logs CASCADE;
DROP TABLE IF EXISTS question_irt_parameters CASCADE;
DROP TABLE IF EXISTS questions CASCADE;
DROP TABLE IF EXISTS question_correct_answers CASCADE;
DROP TABLE IF EXISTS question_job_roles CASCADE;
DROP TABLE IF EXISTS question_options CASCADE;
DROP TABLE IF EXISTS question_tags CASCADE;
DROP TABLE IF EXISTS question_technologies CASCADE;
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS session_logs CASCADE;
DROP TABLE IF EXISTS user_permissions CASCADE;
DROP TABLE IF EXISTS user_profiles CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS recruiters CASCADE;
DROP TABLE IF EXISTS otp_entries CASCADE;
DROP TABLE IF EXISTS password_reset_tokens CASCADE;
DROP TABLE IF EXISTS flyway_schema_history CASCADE;

-- Create ENUM types
CREATE TYPE candidate_status AS ENUM ('APPLIED', 'SCREENING', 'INTERVIEW', 'REJECTED', 'HIRED');
CREATE TYPE difficulty_level AS ENUM ('EASY', 'MEDIUM', 'HARD');
CREATE TYPE question_type_enum AS ENUM ('TECHNICAL', 'BEHAVIORAL', 'CODING', 'SYSTEM_DESIGN');
CREATE TYPE user_role AS ENUM ('ADMIN', 'RECRUITER', 'INTERVIEWER', 'CANDIDATE');

-- 1. users table (updated to match exact structure)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role user_role NOT NULL,
    profile_picture_url TEXT,
    phone_number VARCHAR(20),
    department VARCHAR(100),
    position VARCHAR(100),
    
    -- Account status and verification
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    is_email_verified BOOLEAN DEFAULT FALSE,
    email_verification_token VARCHAR(255),
    email_verification_sent_at TIMESTAMP,
    
    -- Password reset functionality  
    password_reset_token VARCHAR(255),
    password_reset_sent_at TIMESTAMP,
    
    -- Authentication and session management
    last_login_at TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP NULL,
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

-- Add self-referencing foreign keys after table creation
ALTER TABLE users ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

-- 2. candidates table (matching exact structure)
CREATE TABLE candidates (
    id BIGSERIAL PRIMARY KEY,
    requisition_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    applied_role VARCHAR(255) NOT NULL,
    application_date DATE,
    total_experience DOUBLE PRECISION,
    relevant_experience DOUBLE PRECISION,
    interview_round VARCHAR(255),
    status candidate_status,
    job_description TEXT,
    key_responsibilities TEXT,
    
    -- File metadata
    resume_file_name VARCHAR(255),
    resume_url VARCHAR(255),
    resume_size BIGINT,
    profile_pic_file_name VARCHAR(255),
    profile_pic_url VARCHAR(255),
    profile_pic_size BIGINT,
    audio_filename VARCHAR(255),
    audio_url VARCHAR(255),
    audio_size BIGINT,
    
    -- Metadata
    source VARCHAR(255),
    notes VARCHAR(255),
    tags VARCHAR(255),
    recruiter_id VARCHAR(255),
    
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- 3. candidate_skills table
CREATE TABLE candidate_skills (
    candidate_id BIGINT NOT NULL,
    skill VARCHAR(255),
    CONSTRAINT fk_candidate_skills_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE
);

-- 4. roles table
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. permissions table
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resource_action UNIQUE (resource, action)
);

-- 6. role_permissions table
CREATE TABLE role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by BIGINT,
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_granted_by FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

-- 7. user_permissions table
CREATE TABLE user_permissions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted BOOLEAN DEFAULT TRUE,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by BIGINT,
    expires_at TIMESTAMP NULL,
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_granted_by FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_user_permission UNIQUE (user_id, permission_id)
);

-- 8. user_profiles table
CREATE TABLE user_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    
    -- Professional Information
    company VARCHAR(200),
    industry VARCHAR(100),
    experience_years INTEGER,
    current_salary DECIMAL(15,2),
    expected_salary DECIMAL(15,2),
    notice_period_days INTEGER,
    
    -- Contact Information  
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    linkedin_url VARCHAR(500),
    github_url VARCHAR(500),
    portfolio_url VARCHAR(500),
    
    -- Skills and Preferences (using JSONB for PostgreSQL)
    skills JSONB,
    languages JSONB,
    certifications JSONB,
    preferences JSONB,
    
    -- Resume and Documents
    resume_url VARCHAR(500),
    resume_filename VARCHAR(255),
    resume_uploaded_at TIMESTAMP,
    cover_letter_url VARCHAR(500),
    
    -- Privacy and Communication Preferences
    allow_email_notifications BOOLEAN DEFAULT TRUE,
    allow_sms_notifications BOOLEAN DEFAULT FALSE,
    allow_marketing_emails BOOLEAN DEFAULT FALSE,
    data_processing_consent BOOLEAN DEFAULT FALSE,
    data_processing_consent_date TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 9. refresh_tokens table
CREATE TABLE refresh_tokens (
    id VARCHAR(255) PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    revoked BOOLEAN NOT NULL,
    device_id VARCHAR(255),
    device_type VARCHAR(50),
    device_name VARCHAR(200),
    ip_address VARCHAR(45),
    user_agent TEXT,
    last_used_at TIMESTAMP,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMP
);

-- 10. session_logs table
CREATE TABLE session_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_id VARCHAR(255),
    failure_reason VARCHAR(255),
    two_factor_used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 11. questions table (comprehensive structure)
CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    question_text TEXT NOT NULL,
    type question_type_enum NOT NULL,
    description TEXT,
    difficulty_level difficulty_level NOT NULL,
    
    -- IRT Parameters
    difficulty_parameter DOUBLE PRECISION NOT NULL,
    discrimination_parameter DOUBLE PRECISION NOT NULL,
    guessing_parameter DOUBLE PRECISION NOT NULL,
    upper_asymptote DOUBLE PRECISION NOT NULL,
    
    -- Performance metrics
    times_asked INTEGER NOT NULL,
    times_answered_correctly INTEGER NOT NULL,
    average_response_time DOUBLE PRECISION NOT NULL,
    engagement_score DOUBLE PRECISION NOT NULL,
    bias_score DOUBLE PRECISION NOT NULL,
    information_value DOUBLE PRECISION NOT NULL,
    
    -- IRT calibration
    min_theta_level DOUBLE PRECISION,
    max_theta_level DOUBLE PRECISION,
    target_information DOUBLE PRECISION,
    last_calibration_date TIMESTAMP,
    next_calibration_due TIMESTAMP,
    validation_sample_size INTEGER,
    
    -- Content
    code_template TEXT,
    test_cases TEXT,
    expected_keywords TEXT,
    
    -- Status and review
    active BOOLEAN NOT NULL,
    validated BOOLEAN NOT NULL,
    review_date TIMESTAMP,
    reviewed_by BIGINT,
    review_comments TEXT,
    exposure_control DOUBLE PRECISION,
    
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 12. question_correct_answers table
CREATE TABLE question_correct_answers (
    question_id BIGINT NOT NULL,
    correct_answer VARCHAR(255),
    CONSTRAINT fk_question_correct_answers_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 13. question_options table
CREATE TABLE question_options (
    question_id BIGINT NOT NULL,
    option_order INTEGER NOT NULL,
    options VARCHAR(255),
    PRIMARY KEY (question_id, option_order),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 14. question_job_roles table
CREATE TABLE question_job_roles (
    question_id BIGINT NOT NULL,
    job_role VARCHAR(255),
    CONSTRAINT fk_question_job_roles_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 15. question_tags table
CREATE TABLE question_tags (
    question_id BIGINT NOT NULL,
    tag VARCHAR(255),
    CONSTRAINT fk_question_tags_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 16. question_technologies table
CREATE TABLE question_technologies (
    question_id BIGINT NOT NULL,
    technology VARCHAR(255),
    CONSTRAINT fk_question_technologies_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 17. question_effectiveness_log table
CREATE TABLE question_effectiveness_log (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    interview_session_id VARCHAR(255) NOT NULL,
    candidate_id BIGINT NOT NULL,
    
    -- IRT Parameters
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
    
    -- IRT Parameters Used
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
    
    -- Bias and Fairness
    bias_indicators TEXT,
    demographic_group VARCHAR(100),
    expected_score_demographic DOUBLE PRECISION,
    actual_score_demographic DOUBLE PRECISION,
    
    -- Context
    question_position INTEGER NOT NULL,
    interview_stage VARCHAR(50),
    job_role VARCHAR(100),
    technologies_assessed TEXT,
    interviewer_id BIGINT,
    
    -- Performance Indicators
    engagement_score DECIMAL(5,4),
    confidence_score DECIMAL(5,4),
    difficulty_perception DECIMAL(5,4),
    
    -- Algorithm Metrics
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
    
    logged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_question_effectiveness_log_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_effectiveness_log_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE
);

-- 18. question_effectiveness_logs table (different structure)
CREATE TABLE question_effectiveness_logs (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    interview_session_id VARCHAR(255) NOT NULL,
    candidate_id BIGINT NOT NULL,
    
    -- IRT data
    candidate_theta_before DOUBLE PRECISION NOT NULL,
    candidate_theta_after DOUBLE PRECISION NOT NULL,
    standard_error_before DOUBLE PRECISION NOT NULL,
    standard_error_after DOUBLE PRECISION NOT NULL,
    
    -- Response data
    response_correct BOOLEAN NOT NULL,
    response_time DOUBLE PRECISION NOT NULL,
    candidate_answer TEXT,
    expected_answer TEXT,
    partial_credit_score DOUBLE PRECISION,
    
    -- IRT parameters used
    discrimination_parameter_used DOUBLE PRECISION NOT NULL,
    difficulty_parameter_used DOUBLE PRECISION NOT NULL,
    guessing_parameter_used DOUBLE PRECISION NOT NULL,
    upper_asymptote_used DOUBLE PRECISION NOT NULL,
    
    -- Information metrics
    information_provided DOUBLE PRECISION NOT NULL,
    expected_information DOUBLE PRECISION NOT NULL,
    information_efficiency DOUBLE PRECISION NOT NULL,
    
    -- Prediction
    predicted_probability DOUBLE PRECISION NOT NULL,
    prediction_error DOUBLE PRECISION NOT NULL,
    log_likelihood DOUBLE PRECISION NOT NULL,
    
    -- Bias detection
    bias_indicators TEXT,
    demographic_group VARCHAR(255),
    expected_score_demographic DOUBLE PRECISION,
    actual_score_demographic DOUBLE PRECISION,
    
    -- Context
    question_position INTEGER NOT NULL,
    interview_stage VARCHAR(255),
    job_role VARCHAR(255),
    technologies_assessed TEXT,
    interviewer_id BIGINT,
    
    -- Performance
    engagement_score DOUBLE PRECISION,
    confidence_score DOUBLE PRECISION,
    difficulty_perception DOUBLE PRECISION,
    
    -- Selection data
    selection_algorithm VARCHAR(255),
    selection_score DOUBLE PRECISION NOT NULL,
    competing_questions TEXT,
    selection_reasons TEXT,
    
    -- Quality
    question_clarity_score DOUBLE PRECISION,
    answer_quality_score DOUBLE PRECISION,
    technical_accuracy_score DOUBLE PRECISION,
    
    -- Meta
    question_version VARCHAR(255),
    system_version VARCHAR(255),
    additional_metrics TEXT,
    
    logged_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_question_effectiveness_logs_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 19. conversation_patterns table (comprehensive)
CREATE TABLE conversation_patterns (
    id BIGSERIAL PRIMARY KEY,
    interview_session_id VARCHAR(255) NOT NULL UNIQUE,
    candidate_id BIGINT NOT NULL,
    recruiter_id BIGINT,
    
    -- Basic metrics
    total_duration INTEGER NOT NULL DEFAULT 0,
    total_questions INTEGER NOT NULL DEFAULT 0,
    average_response_time DOUBLE PRECISION DEFAULT 0.0,
    min_response_time DOUBLE PRECISION NOT NULL,
    max_response_time DOUBLE PRECISION NOT NULL,
    median_response_time DOUBLE PRECISION NOT NULL,
    response_time_variance DOUBLE PRECISION NOT NULL,
    
    -- Communication quality
    clarity_score DOUBLE PRECISION DEFAULT 0.0,
    engagement_score DOUBLE PRECISION DEFAULT 0.0,
    confidence_score DOUBLE PRECISION DEFAULT 0.0,
    coherence_score DOUBLE PRECISION NOT NULL,
    professionalism_score DOUBLE PRECISION NOT NULL,
    enthusiasm_level DOUBLE PRECISION NOT NULL,
    confidence_level DOUBLE PRECISION NOT NULL,
    
    -- Speaking patterns
    total_words_spoken INTEGER NOT NULL,
    candidate_words INTEGER NOT NULL,
    ai_words INTEGER NOT NULL,
    candidate_speaking_time INTEGER NOT NULL,
    ai_speaking_time INTEGER NOT NULL,
    silence_time INTEGER NOT NULL,
    overlap_time INTEGER NOT NULL,
    speaking_pace DOUBLE PRECISION NOT NULL,
    
    -- Conversation flow
    natural_flow_score DOUBLE PRECISION NOT NULL,
    interruption_count INTEGER NOT NULL,
    ai_interruption_count INTEGER NOT NULL,
    topic_changes INTEGER NOT NULL,
    follow_up_questions INTEGER NOT NULL,
    clarification_requests INTEGER NOT NULL,
    
    -- Response analysis
    direct_answers INTEGER NOT NULL,
    incomplete_responses INTEGER NOT NULL,
    filler_word_count INTEGER NOT NULL,
    pause_frequency DOUBLE PRECISION NOT NULL,
    average_pause_duration DOUBLE PRECISION NOT NULL,
    
    -- Content analysis
    technical_term_usage INTEGER NOT NULL,
    vocabulary_richness DOUBLE PRECISION NOT NULL,
    concept_articulation DOUBLE PRECISION NOT NULL,
    explanation_quality DOUBLE PRECISION NOT NULL,
    question_understanding DOUBLE PRECISION NOT NULL,
    
    -- Emotional/behavioral
    overall_sentiment DOUBLE PRECISION NOT NULL,
    stress_indicators INTEGER NOT NULL,
    rapport_score DOUBLE PRECISION NOT NULL,
    
    -- Comparative metrics
    peer_comparison_score DOUBLE PRECISION NOT NULL,
    
    -- Text data
    conversation_topics TEXT,
    key_phrases TEXT,
    speech_patterns TEXT,
    sentiment_progression TEXT,
    strengths_identified TEXT,
    improvement_areas TEXT,
    interview_phase_performance TEXT,
    
    -- Metadata
    communication_style VARCHAR(255),
    analysis_version VARCHAR(255),
    processing_time INTEGER NOT NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 20. recruiters table
CREATE TABLE recruiters (
    id VARCHAR(255) PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_otp_verified BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 21. otp_entries table
CREATE TABLE otp_entries (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp_code VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_sent_at TIMESTAMP NOT NULL,
    attempts INTEGER NOT NULL
);

-- 22. password_reset_tokens table
CREATE TABLE password_reset_tokens (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL
);

-- 23. flyway_schema_history table
CREATE TABLE flyway_schema_history (
    installed_rank INTEGER NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INTEGER,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INTEGER NOT NULL,
    success BOOLEAN NOT NULL,
    CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
);

-- Create all indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_active ON users(is_active);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_email_verification_token ON users(email_verification_token);
CREATE INDEX idx_users_password_reset_token ON users(password_reset_token);

CREATE INDEX idx_candidates_id ON candidates(id);
CREATE INDEX idx_candidate_skills_candidate_id ON candidate_skills(candidate_id);

CREATE INDEX idx_roles_name ON roles(name);
CREATE INDEX idx_roles_active ON roles(is_active);

CREATE INDEX idx_permissions_name ON permissions(name);
CREATE INDEX idx_permissions_resource ON permissions(resource);
CREATE INDEX idx_permissions_action ON permissions(action);
CREATE INDEX idx_permissions_active ON permissions(is_active);

CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

CREATE INDEX idx_user_permissions_user_id ON user_permissions(user_id);
CREATE INDEX idx_user_permissions_permission_id ON user_permissions(permission_id);
CREATE INDEX idx_user_permissions_expires_at ON user_permissions(expires_at);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

CREATE INDEX idx_session_logs_user_id ON session_logs(user_id);
CREATE INDEX idx_session_logs_session_id ON session_logs(session_id);
CREATE INDEX idx_session_logs_created_at ON session_logs(created_at);

CREATE INDEX idx_questions_id ON questions(id);
CREATE INDEX idx_questions_type ON questions(type);
CREATE INDEX idx_questions_active ON questions(active);

CREATE INDEX idx_question_effectiveness_log_question_id ON question_effectiveness_log(question_id);
CREATE INDEX idx_question_effectiveness_log_candidate_id ON question_effectiveness_log(candidate_id);
CREATE INDEX idx_question_effectiveness_log_session_id ON question_effectiveness_log(interview_session_id);

CREATE INDEX idx_question_effectiveness_logs_question_id ON question_effectiveness_logs(question_id);
CREATE INDEX idx_question_effectiveness_logs_session_id ON question_effectiveness_logs(interview_session_id);

CREATE INDEX idx_conversation_patterns_session_id ON conversation_patterns(interview_session_id);
CREATE INDEX idx_conversation_patterns_candidate_id ON conversation_patterns(candidate_id);

CREATE INDEX idx_recruiters_email ON recruiters(email);
CREATE INDEX idx_otp_entries_email ON otp_entries(email);
CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX flyway_schema_history_s_idx ON flyway_schema_history(success);

-- Insert default data
INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'System administrator with full access'),
    ('RECRUITER', 'HR recruiter who can schedule interviews and review candidates'),
    ('INTERVIEWER', 'Technical interviewer who conducts interviews'),
    ('CANDIDATE', 'Job candidate participating in interviews');

INSERT INTO permissions (name, description, resource, action) VALUES
    ('users.create', 'Create new users', 'users', 'create'),
    ('users.read', 'Read user information', 'users', 'read'),
    ('users.update', 'Update user information', 'users', 'update'),
    ('users.delete', 'Delete users', 'users', 'delete'),
    ('users.list', 'List all users', 'users', 'list'),
    ('interviews.create', 'Schedule new interviews', 'interviews', 'create'),
    ('interviews.read', 'View interview details', 'interviews', 'read'),
    ('interviews.update', 'Update interview information', 'interviews', 'update'),
    ('interviews.delete', 'Delete interviews', 'interviews', 'delete'),
    ('interviews.list', 'List interviews', 'interviews', 'list'),
    ('interviews.conduct', 'Conduct interviews as interviewer', 'interviews', 'conduct'),
    ('interviews.participate', 'Participate in interviews as candidate', 'interviews', 'participate'),
    ('analytics.read', 'View analytics and reports', 'analytics', 'read'),
    ('analytics.export', 'Export analytics data', 'analytics', 'export'),
    ('system.configure', 'Configure system settings', 'system', 'configure'),
    ('system.monitor', 'Monitor system health', 'system', 'monitor'),
    ('system.backup', 'Perform system backups', 'system', 'backup'),
    ('questions.create', 'Create interview questions', 'questions', 'create'),
    ('questions.read', 'View interview questions', 'questions', 'read'),
    ('questions.update', 'Update interview questions', 'questions', 'update'),
    ('questions.delete', 'Delete interview questions', 'questions', 'delete'),
    ('assessments.create', 'Create assessments', 'assessments', 'create'),
    ('assessments.read', 'View assessments', 'assessments', 'read'),
    ('assessments.update', 'Update assessments', 'assessments', 'update'),
    ('assessments.submit', 'Submit assessment responses', 'assessments', 'submit');

-- Assign permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r 
CROSS JOIN permissions p 
WHERE r.name = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r 
JOIN permissions p ON p.name IN (
    'users.create', 'users.read', 'users.update', 'users.list',
    'interviews.create', 'interviews.read', 'interviews.update', 'interviews.list',
    'analytics.read', 'analytics.export',
    'questions.read', 'assessments.read'
)
WHERE r.name = 'RECRUITER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'users.read',
    'interviews.read', 'interviews.conduct', 'interviews.update',
    'questions.read', 'questions.create', 'questions.update',
    'assessments.create', 'assessments.read', 'assessments.update'
)
WHERE r.name = 'INTERVIEWER';

INSERT INTO role_permissions (role_id, permission_id) 
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'users.read', 'users.update',
    'interviews.read', 'interviews.participate',
    'assessments.read', 'assessments.submit'
)
WHERE r.name = 'CANDIDATE';

-- Insert default users
INSERT INTO users (
    email, password_hash, first_name, last_name, role, 
    is_verified, is_active, is_email_verified,
    created_at, updated_at
) VALUES (
    'admin@aria.com',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    'System', 'Administrator', 'ADMIN',
    TRUE, TRUE, TRUE, NOW(), NOW()
), (
    'recruiter@aria.com',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    'Jane', 'Recruiter', 'RECRUITER',
    TRUE, TRUE, TRUE, NOW(), NOW()
), (
    'interviewer@aria.com',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    'John', 'Interviewer', 'INTERVIEWER',
    TRUE, TRUE, TRUE, NOW(), NOW()
);

-- Insert user profiles
INSERT INTO user_profiles (user_id, allow_email_notifications, data_processing_consent, data_processing_consent_date)
SELECT id, TRUE, TRUE, NOW()
FROM users
WHERE email IN ('admin@aria.com', 'recruiter@aria.com', 'interviewer@aria.com');

-- Insert sample questions
INSERT INTO questions (
    question_text, type, description, difficulty_level,
    difficulty_parameter, discrimination_parameter, guessing_parameter, upper_asymptote,
    times_asked, times_answered_correctly, average_response_time, engagement_score, 
    bias_score, information_value, active, validated, created_by, created_at, updated_at
) VALUES 
(
    'Explain the difference between HTTP and HTTPS protocols. What are the key security benefits of HTTPS?',
    'TECHNICAL', 'Networking and security fundamentals', 'EASY',
    -0.8, 1.2, 0.0, 1.0,
    100, 75, 180.5, 0.85, 0.1, 2.3,
    TRUE, TRUE, 1, NOW(), NOW()
),
(
    'What is database normalization and why is it important? Explain the first three normal forms.',
    'TECHNICAL', 'Database design principles', 'MEDIUM',
    0.2, 1.4, 0.0, 1.0,
    80, 48, 240.2, 0.78, 0.15, 2.8,
    TRUE, TRUE, 1, NOW(), NOW()
),
(
    'Design a URL shortener service like bit.ly. Consider scalability and performance.',
    'SYSTEM_DESIGN', 'System design and scalability', 'HARD',
    1.8, 1.2, 0.0, 1.0,
    50, 15, 600.0, 0.92, 0.05, 3.2,
    TRUE, TRUE, 1, NOW(), NOW()
);

-- Insert Flyway history
INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script, checksum,
    installed_by, installed_on, execution_time, success
) VALUES
(1, '1', 'Complete Schema Migration', 'SQL', 'complete_schema_migration.sql', 1234567890, 
 'neondb_owner', NOW(), 15000, TRUE);

-- Create update triggers
CREATE TRIGGER update_users_updated_at_trigger 
    BEFORE UPDATE ON users 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_roles_updated_at 
    BEFORE UPDATE ON roles 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_permissions_updated_at 
    BEFORE UPDATE ON permissions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_profiles_updated_at 
    BEFORE UPDATE ON user_profiles 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_questions_updated_at 
    BEFORE UPDATE ON questions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_conversation_patterns_updated_at 
    BEFORE UPDATE ON conversation_patterns 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
