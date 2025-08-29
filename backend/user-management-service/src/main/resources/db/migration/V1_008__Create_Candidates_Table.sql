-- Create candidates table migration
-- This table stores candidate information for recruitment process

-- Create candidate_status ENUM type if it doesn't exist
-- This ENUM may already exist from previous schema setup
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'candidate_status') THEN
        CREATE TYPE candidate_status AS ENUM ('APPLIED', 'SCREENING', 'INTERVIEW', 'REJECTED', 'HIRED');
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS candidates (
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
    status candidate_status DEFAULT 'APPLIED',
    
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
    -- Note: status uses candidate_status ENUM type which includes ('APPLIED', 'SCREENING', 'INTERVIEW', 'REJECTED', 'HIRED')
    CONSTRAINT uk_candidates_email_requisition UNIQUE (email, requisition_id)
);

-- Create candidate_skills table for skills normalization
CREATE TABLE IF NOT EXISTS candidate_skills (
    candidate_id BIGINT NOT NULL,
    skill VARCHAR(255) NOT NULL,
    
    PRIMARY KEY (candidate_id, skill),
    CONSTRAINT fk_candidate_skills_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_candidates_email ON candidates(email);
CREATE INDEX IF NOT EXISTS idx_candidates_requisition_id ON candidates(requisition_id);
CREATE INDEX IF NOT EXISTS idx_candidates_status ON candidates(status);
CREATE INDEX IF NOT EXISTS idx_candidates_applied_role ON candidates(applied_role);
CREATE INDEX IF NOT EXISTS idx_candidates_created_at ON candidates(created_at);
CREATE INDEX IF NOT EXISTS idx_candidates_recruiter_id ON candidates(recruiter_id);
CREATE INDEX IF NOT EXISTS idx_candidates_interview_round ON candidates(interview_round);

-- Index for quick searches
CREATE INDEX IF NOT EXISTS idx_candidates_name ON candidates(name);
CREATE INDEX IF NOT EXISTS idx_candidates_phone ON candidates(phone);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_candidates_status_recruiter ON candidates(status, recruiter_id);
CREATE INDEX IF NOT EXISTS idx_candidates_role_status ON candidates(applied_role, status);

-- Full-text search index for names and tags (PostgreSQL specific)
CREATE INDEX IF NOT EXISTS idx_candidates_search ON candidates USING gin(to_tsvector('english', name || ' ' || COALESCE(tags, '')));

-- Add unique constraint if it doesn't exist (for existing tables)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints 
                   WHERE table_name = 'candidates' AND constraint_name = 'uk_candidates_email_requisition') THEN
        ALTER TABLE candidates ADD CONSTRAINT uk_candidates_email_requisition UNIQUE (email, requisition_id);
    END IF;
END$$;

-- Add primary key constraint for candidate_skills if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints 
                   WHERE table_name = 'candidate_skills' AND constraint_type = 'PRIMARY KEY') THEN
        ALTER TABLE candidate_skills ADD PRIMARY KEY (candidate_id, skill);
    END IF;
END$$;

-- Trigger for updated_at (if update_updated_at_column function exists)
-- CREATE TRIGGER update_candidates_updated_at BEFORE UPDATE ON candidates FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert some sample data for testing (optional)
INSERT INTO candidates (
    requisition_id, name, email, phone, applied_role, 
    total_experience, relevant_experience, interview_round, 
    status, recruiter_id, created_at, updated_at
) VALUES 
    ('FSDT1R1', 'John Doe', 'john.doe@example.com', '1234567890', 'Senior Software Engineer', 5.0, 4.0, 'Technical - T1', 'APPLIED', 'recruiter@aria.com', NOW(), NOW()),
    ('FSDT1R1', 'Jane Smith', 'jane.smith@example.com', '9876543210', 'Senior Software Engineer', 6.0, 5.5, 'Technical - T1', 'SCREENING', 'recruiter@aria.com', NOW(), NOW()),
    ('FSDT2R1', 'Bob Johnson', 'bob.johnson@example.com', '5555551234', 'Full Stack Developer', 3.0, 2.5, 'HR - Round 1', 'INTERVIEW', 'recruiter@aria.com', NOW(), NOW())
ON CONFLICT (email, requisition_id) DO NOTHING;

-- Insert sample skills (only for candidates that actually exist)
DO $$
DECLARE
    candidate_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO candidate_count FROM candidates;
    
    IF candidate_count >= 3 THEN
        -- Insert skills using actual candidate IDs from the database
        INSERT INTO candidate_skills (candidate_id, skill) 
        SELECT c.id, skill_name
        FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) as rn FROM candidates LIMIT 3) c
        CROSS JOIN (
            VALUES 
                ('Java'), ('Spring Boot'), ('Angular'), ('PostgreSQL'),
                ('Python'), ('Django'), ('React'), ('MySQL'),
                ('JavaScript'), ('Node.js'), ('Vue.js'), ('MongoDB')
        ) AS skills(skill_name)
        WHERE 
            (c.rn = 1 AND skill_name IN ('Java', 'Spring Boot', 'Angular', 'PostgreSQL')) OR
            (c.rn = 2 AND skill_name IN ('Python', 'Django', 'React', 'MySQL')) OR
            (c.rn = 3 AND skill_name IN ('JavaScript', 'Node.js', 'Vue.js', 'MongoDB'))
        ON CONFLICT (candidate_id, skill) DO NOTHING;
    END IF;
END$$;
