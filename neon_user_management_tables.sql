-- Complete User Management Tables for Neon Database
-- Missing tables from the user-management-service Flyway migrations

-- First, let's update the users table to match the expected schema
DROP TABLE IF EXISTS users CASCADE;

-- Recreate users table with proper schema (matching V1_001)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) CHECK (role IN ('ADMIN', 'RECRUITER', 'INTERVIEWER', 'CANDIDATE')) NOT NULL,
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
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMP NULL,
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    
    -- Foreign key constraints (self-referential for created_by/updated_by)
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Create indexes for users table
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_active ON users(is_active);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_email_verification_token ON users(email_verification_token);
CREATE INDEX idx_users_password_reset_token ON users(password_reset_token);
CREATE INDEX idx_users_email_active ON users(email, is_active);
CREATE INDEX idx_users_role_active ON users(role, is_active);

-- Create roles table
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for roles table
CREATE INDEX idx_roles_name ON roles(name);
CREATE INDEX idx_roles_active ON roles(is_active);

-- Create permissions table  
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

-- Create indexes for permissions table
CREATE INDEX idx_permissions_name ON permissions(name);
CREATE INDEX idx_permissions_resource ON permissions(resource);
CREATE INDEX idx_permissions_action ON permissions(action);
CREATE INDEX idx_permissions_active ON permissions(is_active);

-- Create role_permissions junction table
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

-- Create indexes for role_permissions table
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- Create user_permissions table for additional user-specific permissions
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

-- Create indexes for user_permissions table
CREATE INDEX idx_user_permissions_user_id ON user_permissions(user_id);
CREATE INDEX idx_user_permissions_permission_id ON user_permissions(permission_id);
CREATE INDEX idx_user_permissions_expires_at ON user_permissions(expires_at);

-- Create user_profiles table for extended user information
CREATE TABLE user_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    
    -- Professional Information
    company VARCHAR(200),
    industry VARCHAR(100),
    experience_years INT,
    current_salary DECIMAL(15,2),
    expected_salary DECIMAL(15,2),
    notice_period_days INT,
    
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
    
    -- Skills and Preferences
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
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create refresh_tokens table for JWT token management
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    
    -- Token lifecycle
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    
    -- Device and session information
    device_id VARCHAR(255),
    device_type VARCHAR(50), -- MOBILE, DESKTOP, TABLET, etc.
    device_name VARCHAR(200),
    ip_address VARCHAR(45), -- Support for IPv6
    user_agent TEXT,
    
    -- Security
    last_used_at TIMESTAMP,
    usage_count INT DEFAULT 0,
    
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create session_logs table for audit trail
CREATE TABLE session_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    
    -- Session information
    session_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL, -- LOGIN, LOGOUT, TOKEN_REFRESH, PASSWORD_RESET, etc.
    status VARCHAR(20) NOT NULL, -- SUCCESS, FAILED, BLOCKED
    
    -- Request details
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_id VARCHAR(255),
    
    -- Security details
    failure_reason VARCHAR(255),
    two_factor_used BOOLEAN DEFAULT FALSE,
    
    -- Timing
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_session_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create question_effectiveness_log table
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

-- Create conversation_patterns table
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

-- Create bias_detection_results table
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

-- Create all the indexes
CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens(user_id, is_revoked, expires_at);
CREATE INDEX idx_question_effectiveness_log_question_id ON question_effectiveness_log(question_id);
CREATE INDEX idx_question_effectiveness_log_candidate_id ON question_effectiveness_log(candidate_id);
CREATE INDEX idx_question_effectiveness_log_interview_session ON question_effectiveness_log(interview_session_id);
CREATE INDEX idx_question_effectiveness_log_logged_at ON question_effectiveness_log(logged_at);
CREATE INDEX idx_question_effectiveness_log_job_role ON question_effectiveness_log(job_role);
CREATE INDEX idx_question_effectiveness_log_interview_stage ON question_effectiveness_log(interview_stage);
CREATE INDEX idx_conversation_patterns_interview_session ON conversation_patterns(interview_session_id);
CREATE INDEX idx_conversation_patterns_candidate_id ON conversation_patterns(candidate_id);
CREATE INDEX idx_conversation_patterns_created_at ON conversation_patterns(created_at);
CREATE INDEX idx_bias_detection_results_interview_session ON bias_detection_results(interview_session_id);
CREATE INDEX idx_bias_detection_results_candidate_id ON bias_detection_results(candidate_id);
CREATE INDEX idx_bias_detection_results_created_at ON bias_detection_results(created_at);
CREATE INDEX idx_bias_detection_results_bias_detected ON bias_detection_results(bias_detected);

-- Insert default roles
INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'System administrator with full access'),
    ('RECRUITER', 'HR recruiter who can schedule interviews and review candidates'),
    ('INTERVIEWER', 'Technical interviewer who conducts interviews'),
    ('CANDIDATE', 'Job candidate participating in interviews');

-- Insert permissions
INSERT INTO permissions (name, description, resource, action) VALUES
    -- User Management Permissions
    ('users.create', 'Create new users', 'users', 'create'),
    ('users.read', 'Read user information', 'users', 'read'),
    ('users.update', 'Update user information', 'users', 'update'),
    ('users.delete', 'Delete users', 'users', 'delete'),
    ('users.list', 'List all users', 'users', 'list'),
    
    -- Interview Management Permissions  
    ('interviews.create', 'Schedule new interviews', 'interviews', 'create'),
    ('interviews.read', 'View interview details', 'interviews', 'read'),
    ('interviews.update', 'Update interview information', 'interviews', 'update'),
    ('interviews.delete', 'Delete interviews', 'interviews', 'delete'),
    ('interviews.list', 'List interviews', 'interviews', 'list'),
    ('interviews.conduct', 'Conduct interviews as interviewer', 'interviews', 'conduct'),
    ('interviews.participate', 'Participate in interviews as candidate', 'interviews', 'participate'),
    
    -- Analytics and Reporting Permissions
    ('analytics.read', 'View analytics and reports', 'analytics', 'read'),
    ('analytics.export', 'Export analytics data', 'analytics', 'export'),
    
    -- System Administration Permissions
    ('system.configure', 'Configure system settings', 'system', 'configure'),
    ('system.monitor', 'Monitor system health', 'system', 'monitor'),
    ('system.backup', 'Perform system backups', 'system', 'backup'),
    
    -- Question Bank Permissions
    ('questions.create', 'Create interview questions', 'questions', 'create'),
    ('questions.read', 'View interview questions', 'questions', 'read'),
    ('questions.update', 'Update interview questions', 'questions', 'update'),
    ('questions.delete', 'Delete interview questions', 'questions', 'delete'),
    
    -- Assessment Permissions
    ('assessments.create', 'Create assessments', 'assessments', 'create'),
    ('assessments.read', 'View assessments', 'assessments', 'read'),
    ('assessments.update', 'Update assessments', 'assessments', 'update'),
    ('assessments.submit', 'Submit assessment responses', 'assessments', 'submit');

-- Assign permissions to roles

-- ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r 
CROSS JOIN permissions p 
WHERE r.name = 'ADMIN';

-- RECRUITER permissions
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

-- INTERVIEWER permissions  
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

-- CANDIDATE permissions
INSERT INTO role_permissions (role_id, permission_id) 
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'users.read', 'users.update',
    'interviews.read', 'interviews.participate',
    'assessments.read', 'assessments.submit'
)
WHERE r.name = 'CANDIDATE';

-- Insert default admin user (password: 'admin123' hashed with BCrypt)
INSERT INTO users (
    email, 
    password_hash, 
    first_name, 
    last_name, 
    role, 
    is_verified, 
    is_active, 
    is_email_verified,
    created_at,
    updated_at
) VALUES (
    'admin@aria.com',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', -- BCrypt hash for 'admin123'
    'System',
    'Administrator',
    'ADMIN',
    TRUE,
    TRUE,
    TRUE,
    NOW(),
    NOW()
);

-- Create additional default users for testing
INSERT INTO users (
    email, 
    password_hash, 
    first_name, 
    last_name, 
    role, 
    is_verified, 
    is_active, 
    is_email_verified,
    created_at,
    updated_at
) VALUES 
    (
        'recruiter@aria.com',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', -- BCrypt hash for 'admin123'
        'Jane',
        'Recruiter',
        'RECRUITER',
        TRUE,
        TRUE,
        TRUE,
        NOW(),
        NOW()
    ),
    (
        'interviewer@aria.com',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', -- BCrypt hash for 'admin123'
        'John',
        'Interviewer',
        'INTERVIEWER',
        TRUE,
        TRUE,
        TRUE,
        NOW(),
        NOW()
    );

-- Insert default user profiles for existing users
INSERT INTO user_profiles (user_id, allow_email_notifications, data_processing_consent, data_processing_consent_date)
SELECT id, TRUE, TRUE, NOW()
FROM users
WHERE email IN ('admin@aria.com', 'recruiter@aria.com', 'interviewer@aria.com');

-- Create triggers for updated_at
CREATE TRIGGER update_users_updated_at_trigger BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_roles_updated_at BEFORE UPDATE ON roles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_permissions_updated_at BEFORE UPDATE ON permissions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_conversation_patterns_updated_at BEFORE UPDATE ON conversation_patterns FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_bias_detection_results_updated_at BEFORE UPDATE ON bias_detection_results FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
