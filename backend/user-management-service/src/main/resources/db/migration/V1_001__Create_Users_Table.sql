-- V1_001__Create_Users_Table.sql
-- Initial creation of users table for user management system

-- Drop table if exists (for clean migration)
DROP TABLE IF EXISTS users;

-- Create users table
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role ENUM('ADMIN', 'RECRUITER', 'INTERVIEWER', 'CANDIDATE') NOT NULL,
    profile_picture_url TEXT,
    phone_number VARCHAR(20),
    department VARCHAR(100),
    position VARCHAR(100),
    
    -- Account status and verification
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    is_email_verified BOOLEAN DEFAULT FALSE,
    email_verification_token VARCHAR(255),
    email_verification_sent_at DATETIME,
    
    -- Password reset functionality  
    password_reset_token VARCHAR(255),
    password_reset_sent_at DATETIME,
    
    -- Authentication and session management
    last_login_at DATETIME,
    failed_login_attempts INT DEFAULT 0,
    locked_until DATETIME NULL,
    
    -- Audit fields
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    
    -- Add indexes for performance
    INDEX idx_email (email),
    INDEX idx_role (role),
    INDEX idx_active (is_active),
    INDEX idx_created_at (created_at),
    INDEX idx_email_verification_token (email_verification_token),
    INDEX idx_password_reset_token (password_reset_token),
    
    -- Foreign key constraints (self-referential for created_by/updated_by)
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

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
