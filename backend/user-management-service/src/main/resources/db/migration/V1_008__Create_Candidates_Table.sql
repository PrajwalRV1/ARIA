-- Create candidates table migration
-- This table stores candidate information for recruitment process

CREATE TABLE candidates (
    id BIGSERIAL PRIMARY KEY,
    
    -- Business fields
    requisition_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    applied_role VARCHAR(255),
    application_date DATE,
    total_experience DOUBLE PRECISION,
    relevant_experience DOUBLE PRECISION,
    interview_round VARCHAR(100),
    status VARCHAR(50) DEFAULT 'PENDING',
    
    -- Job description and responsibilities
    job_description TEXT,
    key_responsibilities TEXT,
    
    -- File metadata (store URLs/paths, not bytes)
    resume_file_name VARCHAR(255),
    resume_url VARCHAR(500),
    resume_size BIGINT,
    
    profile_pic_file_name VARCHAR(255),
    profile_pic_url VARCHAR(500),
    profile_pic_size BIGINT,
    
    -- Audio file metadata for phone screening
    audio_filename VARCHAR(255),
    audio_url VARCHAR(500),
    audio_size BIGINT,
    
    -- Optional metadata
    source VARCHAR(100), -- e.g., LinkedIn, Referral
    notes TEXT,
    tags TEXT, -- comma separated for quick search
    recruiter_id VARCHAR(100), -- who added the candidate
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_candidates_status CHECK (status IN ('PENDING', 'SCHEDULED', 'COMPLETED', 'SELECTED', 'REJECTED', 'ON_HOLD')),
    CONSTRAINT uk_candidates_email_requisition UNIQUE (email, requisition_id)
);

-- Create candidate_skills table for skills normalization
CREATE TABLE candidate_skills (
    candidate_id BIGINT NOT NULL,
    skill VARCHAR(255) NOT NULL,
    
    PRIMARY KEY (candidate_id, skill),
    CONSTRAINT fk_candidate_skills_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX idx_candidates_email ON candidates(email);
CREATE INDEX idx_candidates_requisition_id ON candidates(requisition_id);
CREATE INDEX idx_candidates_status ON candidates(status);
CREATE INDEX idx_candidates_applied_role ON candidates(applied_role);
CREATE INDEX idx_candidates_created_at ON candidates(created_at);
CREATE INDEX idx_candidates_recruiter_id ON candidates(recruiter_id);
CREATE INDEX idx_candidates_interview_round ON candidates(interview_round);

-- Index for quick searches
CREATE INDEX idx_candidates_name ON candidates(name);
CREATE INDEX idx_candidates_phone ON candidates(phone);

-- Composite indexes for common queries
CREATE INDEX idx_candidates_status_recruiter ON candidates(status, recruiter_id);
CREATE INDEX idx_candidates_role_status ON candidates(applied_role, status);

-- Full-text search index for names and tags (PostgreSQL specific)
CREATE INDEX idx_candidates_search ON candidates USING gin(to_tsvector('english', name || ' ' || COALESCE(tags, '')));

-- Trigger for updated_at (if update_updated_at_column function exists)
-- CREATE TRIGGER update_candidates_updated_at BEFORE UPDATE ON candidates FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert some sample data for testing (optional)
INSERT INTO candidates (
    requisition_id, name, email, phone, applied_role, 
    total_experience, relevant_experience, interview_round, 
    status, recruiter_id, created_at, updated_at
) VALUES 
    ('FSDT1R1', 'John Doe', 'john.doe@example.com', '1234567890', 'Senior Software Engineer', 5.0, 4.0, 'Technical - T1', 'PENDING', 'recruiter@aria.com', NOW(), NOW()),
    ('FSDT1R1', 'Jane Smith', 'jane.smith@example.com', '9876543210', 'Senior Software Engineer', 6.0, 5.5, 'Technical - T1', 'SCHEDULED', 'recruiter@aria.com', NOW(), NOW()),
    ('FSDT2R1', 'Bob Johnson', 'bob.johnson@example.com', '5555551234', 'Full Stack Developer', 3.0, 2.5, 'HR - Round 1', 'PENDING', 'recruiter@aria.com', NOW(), NOW());

-- Insert sample skills
INSERT INTO candidate_skills (candidate_id, skill) VALUES 
    (1, 'Java'), (1, 'Spring Boot'), (1, 'Angular'), (1, 'PostgreSQL'),
    (2, 'Python'), (2, 'Django'), (2, 'React'), (2, 'MySQL'),
    (3, 'JavaScript'), (3, 'Node.js'), (3, 'Vue.js'), (3, 'MongoDB');
