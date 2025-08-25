-- V1_002__Create_User_Roles_Permissions.sql
-- Create roles and permissions tables for RBAC (Role-Based Access Control)

-- Drop tables if they exist
DROP TABLE IF EXISTS user_permissions;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS roles;

-- Create roles table
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_name (name),
    INDEX idx_active (is_active)
);

-- Create permissions table  
CREATE TABLE permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_name (name),
    INDEX idx_resource (resource),
    INDEX idx_action (action),
    INDEX idx_active (is_active),
    UNIQUE KEY uk_resource_action (resource, action)
);

-- Create role_permissions junction table
CREATE TABLE role_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    granted_by BIGINT,
    
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_granted_by FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL,
    
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
);

-- Create user_permissions table for additional user-specific permissions
CREATE TABLE user_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted BOOLEAN DEFAULT TRUE,
    granted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    granted_by BIGINT,
    expires_at DATETIME NULL,
    
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_granted_by FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL,
    
    UNIQUE KEY uk_user_permission (user_id, permission_id),
    INDEX idx_user_id (user_id),
    INDEX idx_permission_id (permission_id),
    INDEX idx_expires_at (expires_at)
);

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
