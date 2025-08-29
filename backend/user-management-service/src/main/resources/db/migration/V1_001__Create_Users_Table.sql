-- V1_001__Create_Users_Table.sql
-- Initial creation of users table for user management system

-- Drop table if exists (for clean migration)
-- Use CASCADE to handle foreign key constraints from other tables
DROP TABLE IF EXISTS users CASCADE;

-- Create users table
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

-- Create indexes for performance (PostgreSQL syntax)
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_active ON users(is_active);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_email_verification_token ON users(email_verification_token);
CREATE INDEX idx_users_password_reset_token ON users(password_reset_token);

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
