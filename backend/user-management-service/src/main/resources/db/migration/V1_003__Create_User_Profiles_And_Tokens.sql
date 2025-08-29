-- V1_003__Create_User_Profiles_And_Tokens.sql
-- Create user profiles and JWT refresh tokens tables

-- Drop tables if they exist
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS user_profiles;

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
    skills JSON,
    languages JSON,
    certifications JSON,
    preferences JSON,
    
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
    
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX
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
    
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX
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
    
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX,
    -- INDEX
);

-- Insert default user profiles for existing users
INSERT INTO user_profiles (user_id, allow_email_notifications, data_processing_consent, data_processing_consent_date)
SELECT id, TRUE, TRUE, NOW()
FROM users
WHERE email IN ('admin@aria.com', 'recruiter@aria.com', 'interviewer@aria.com');

-- Create indexes for performance optimization
CREATE INDEX idx_users_email_active ON users(email, is_active);
CREATE INDEX idx_users_role_active ON users(role, is_active);
CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens(user_id, is_revoked, expires_at);
