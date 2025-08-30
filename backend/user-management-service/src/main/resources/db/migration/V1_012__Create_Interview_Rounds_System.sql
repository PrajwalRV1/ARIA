-- Create interview rounds tracking system
-- This replaces the simple status field with a comprehensive round-based approach

-- Create interview_round_status enum type
CREATE TYPE interview_round_status AS ENUM (
    'NOT_STARTED',          -- Round hasn't begun yet
    'INTERVIEW_SCHEDULED',  -- Interview is scheduled for this round
    'IN_PROGRESS',          -- Interview is currently happening
    'UNDER_REVIEW',         -- Interview completed, under review
    'COMPLETED',            -- Round completed successfully
    'ON_HOLD',              -- Round put on hold
    'REJECTED',             -- Candidate rejected in this round
    'WITHDRAWN'             -- Candidate withdrew during this round
);

-- Create interview_round_type enum for the 5 standard rounds
CREATE TYPE interview_round_type AS ENUM (
    'SCREENING',            -- Initial screening round
    'TECHNICAL_T1',         -- Technical interview round 1
    'TECHNICAL_T2',         -- Technical interview round 2  
    'HR_ROUND',             -- HR interview round
    'MANAGERIAL_ROUND'      -- Final managerial round
);

-- Create interview_rounds table to track each round for each candidate
CREATE TABLE interview_rounds (
    id BIGSERIAL PRIMARY KEY,
    
    -- Link to candidate
    candidate_id BIGINT NOT NULL,
    
    -- Round information
    round_type interview_round_type NOT NULL,
    round_name VARCHAR(100) NOT NULL, -- Human readable name
    round_order INTEGER NOT NULL,     -- Order of this round (1-5)
    
    -- Status and timing
    status interview_round_status NOT NULL DEFAULT 'NOT_STARTED',
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- Interview details
    interviewer_name VARCHAR(255),
    interviewer_email VARCHAR(255),
    meeting_link VARCHAR(500),
    notes TEXT,
    feedback TEXT,
    score INTEGER CHECK (score >= 0 AND score <= 100),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT fk_interview_rounds_candidate 
        FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    CONSTRAINT uk_interview_rounds_candidate_type 
        UNIQUE (candidate_id, round_type)
);

-- Create indexes for better performance
CREATE INDEX idx_interview_rounds_candidate_id ON interview_rounds(candidate_id);
CREATE INDEX idx_interview_rounds_status ON interview_rounds(status);
CREATE INDEX idx_interview_rounds_type ON interview_rounds(round_type);
CREATE INDEX idx_interview_rounds_scheduled ON interview_rounds(scheduled_at);
CREATE INDEX idx_interview_rounds_candidate_order ON interview_rounds(candidate_id, round_order);

-- Add a computed overall status column to candidates table
-- This will be derived from interview round statuses
ALTER TABLE candidates ADD COLUMN overall_status VARCHAR(50) DEFAULT 'APPLIED';

-- Create function to calculate overall candidate status based on rounds
CREATE OR REPLACE FUNCTION calculate_overall_status(candidate_id_param BIGINT) 
RETURNS VARCHAR(50) AS $$
DECLARE
    round_statuses TEXT[];
    has_rejected BOOLEAN := FALSE;
    has_withdrawn BOOLEAN := FALSE;
    has_on_hold BOOLEAN := FALSE;
    has_in_progress BOOLEAN := FALSE;
    has_scheduled BOOLEAN := FALSE;
    completed_rounds INTEGER := 0;
    total_rounds INTEGER := 0;
BEGIN
    -- Get all round statuses for the candidate
    SELECT ARRAY_AGG(status::TEXT), COUNT(*), 
           COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END)
    INTO round_statuses, total_rounds, completed_rounds
    FROM interview_rounds 
    WHERE candidate_id = candidate_id_param;
    
    -- If no rounds exist, candidate is just applied
    IF total_rounds = 0 THEN
        RETURN 'APPLIED';
    END IF;
    
    -- Check for terminal/blocking statuses
    SELECT 
        'REJECTED' = ANY(round_statuses),
        'WITHDRAWN' = ANY(round_statuses),
        'ON_HOLD' = ANY(round_statuses),
        'IN_PROGRESS' = ANY(round_statuses),
        'INTERVIEW_SCHEDULED' = ANY(round_statuses)
    INTO has_rejected, has_withdrawn, has_on_hold, has_in_progress, has_scheduled;
    
    -- Priority order for overall status determination
    IF has_rejected THEN
        RETURN 'REJECTED';
    ELSIF has_withdrawn THEN
        RETURN 'WITHDRAWN';
    ELSIF has_on_hold THEN
        RETURN 'ON_HOLD';
    ELSIF has_in_progress THEN
        RETURN 'IN_PROGRESS';
    ELSIF has_scheduled THEN
        RETURN 'INTERVIEW_SCHEDULED';
    ELSIF completed_rounds = total_rounds AND total_rounds = 5 THEN
        RETURN 'SELECTED';  -- All 5 rounds completed
    ELSIF completed_rounds > 0 THEN
        RETURN 'UNDER_REVIEW';  -- Some rounds completed, others pending
    ELSE
        RETURN 'APPLIED';  -- No progress yet
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-update overall_status when interview_rounds change
CREATE OR REPLACE FUNCTION update_candidate_overall_status() 
RETURNS TRIGGER AS $$
BEGIN
    -- Update the overall status for the affected candidate
    UPDATE candidates 
    SET overall_status = calculate_overall_status(
        CASE 
            WHEN TG_OP = 'DELETE' THEN OLD.candidate_id
            ELSE NEW.candidate_id
        END
    ),
    updated_at = CURRENT_TIMESTAMP
    WHERE id = CASE 
        WHEN TG_OP = 'DELETE' THEN OLD.candidate_id
        ELSE NEW.candidate_id
    END;
    
    RETURN CASE 
        WHEN TG_OP = 'DELETE' THEN OLD
        ELSE NEW
    END;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for interview_rounds table
CREATE TRIGGER trigger_update_candidate_status_on_rounds_change
    AFTER INSERT OR UPDATE OR DELETE ON interview_rounds
    FOR EACH ROW
    EXECUTE FUNCTION update_candidate_overall_status();

-- Initialize interview rounds for existing candidates
-- This creates all 5 rounds for each existing candidate
INSERT INTO interview_rounds (candidate_id, round_type, round_name, round_order, status)
SELECT 
    c.id,
    round_data.round_type,
    round_data.round_name,
    round_data.round_order,
    CASE 
        WHEN c.status = 'APPLIED' THEN 'NOT_STARTED'::interview_round_status
        WHEN c.status = 'INTERVIEW_SCHEDULED' THEN 'INTERVIEW_SCHEDULED'::interview_round_status
        WHEN c.status = 'IN_PROGRESS' THEN 'IN_PROGRESS'::interview_round_status
        WHEN c.status = 'COMPLETED' THEN 'COMPLETED'::interview_round_status
        WHEN c.status = 'UNDER_REVIEW' THEN 'UNDER_REVIEW'::interview_round_status
        WHEN c.status = 'SELECTED' THEN 'COMPLETED'::interview_round_status
        WHEN c.status = 'REJECTED' THEN 'REJECTED'::interview_round_status
        WHEN c.status = 'ON_HOLD' THEN 'ON_HOLD'::interview_round_status
        WHEN c.status = 'WITHDRAWN' THEN 'WITHDRAWN'::interview_round_status
        ELSE 'NOT_STARTED'::interview_round_status
    END
FROM candidates c
CROSS JOIN (
    VALUES 
        ('SCREENING'::interview_round_type, 'Screening', 1),
        ('TECHNICAL_T1'::interview_round_type, 'Technical - T1', 2),
        ('TECHNICAL_T2'::interview_round_type, 'Technical - T2', 3),
        ('HR_ROUND'::interview_round_type, 'HR - Round', 4),
        ('MANAGERIAL_ROUND'::interview_round_type, 'Managerial Round', 5)
) AS round_data(round_type, round_name, round_order)
WHERE NOT EXISTS (
    SELECT 1 FROM interview_rounds ir 
    WHERE ir.candidate_id = c.id AND ir.round_type = round_data.round_type
);

-- Update overall_status for all candidates based on their rounds
UPDATE candidates 
SET overall_status = calculate_overall_status(id)
WHERE id IN (SELECT DISTINCT candidate_id FROM interview_rounds);

-- Create view for easy candidate overview with round progress
CREATE OR REPLACE VIEW candidate_progress_view AS
SELECT 
    c.id,
    c.name,
    c.email,
    c.requisition_id,
    c.overall_status,
    COALESCE(round_progress.current_round, 'Not Started') as current_round,
    COALESCE(round_progress.completed_rounds, 0) as completed_rounds,
    COALESCE(round_progress.total_rounds, 5) as total_rounds,
    COALESCE(round_progress.next_scheduled, NULL) as next_interview_scheduled
FROM candidates c
LEFT JOIN (
    SELECT 
        candidate_id,
        STRING_AGG(
            CASE WHEN status IN ('IN_PROGRESS', 'INTERVIEW_SCHEDULED') 
            THEN round_name ELSE NULL END, 
            ', '
        ) as current_round,
        COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_rounds,
        COUNT(*) as total_rounds,
        MIN(CASE WHEN status = 'INTERVIEW_SCHEDULED' THEN scheduled_at END) as next_scheduled
    FROM interview_rounds
    GROUP BY candidate_id
) round_progress ON c.id = round_progress.candidate_id;

-- Add helpful comments
COMMENT ON TABLE interview_rounds IS 'Tracks individual interview rounds for each candidate with independent status management';
COMMENT ON TYPE interview_round_status IS 'Status values for individual interview rounds';
COMMENT ON TYPE interview_round_type IS 'Standard interview round types: Screening, Technical T1/T2, HR, Managerial';
COMMENT ON VIEW candidate_progress_view IS 'Consolidated view showing candidate progress across all interview rounds';
