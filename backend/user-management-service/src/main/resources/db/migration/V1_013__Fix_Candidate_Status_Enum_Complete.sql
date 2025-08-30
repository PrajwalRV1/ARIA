-- Fix candidate_status enum to match Java CandidateStatus enum exactly
-- This migration ensures PostgreSQL enum and Java enum are in perfect sync
-- Java enum has: PENDING, APPLIED, INTERVIEW_SCHEDULED, IN_PROGRESS, COMPLETED, UNDER_REVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN

-- First, add all missing enum values that exist in Java but not in database
-- We need to be careful about the order and existing values

-- Add missing enum values (PostgreSQL doesn't allow dropping enum values, only adding)
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'PENDING';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'INTERVIEW_SCHEDULED';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'IN_PROGRESS';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'COMPLETED';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'UNDER_REVIEW';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'SELECTED';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'ON_HOLD';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'WITHDRAWN';

-- Update existing records to use standardized values
-- Map old values to new standardized values

-- 'SCREENING' -> 'APPLIED' (candidates who were in screening are now just applied)
UPDATE candidates SET status = 'APPLIED' 
WHERE status = 'SCREENING';

-- 'INTERVIEW' -> 'INTERVIEW_SCHEDULED' (old "INTERVIEW" becomes "INTERVIEW_SCHEDULED")
UPDATE candidates SET status = 'INTERVIEW_SCHEDULED' 
WHERE status = 'INTERVIEW';

-- 'HIRED' -> 'SELECTED' (old "HIRED" becomes "SELECTED")
UPDATE candidates SET status = 'SELECTED' 
WHERE status = 'HIRED';

-- Update the default value for new records
ALTER TABLE candidates ALTER COLUMN status SET DEFAULT 'PENDING';

-- Add updated_at trigger for any status changes
UPDATE candidates SET updated_at = CURRENT_TIMESTAMP 
WHERE status IN ('APPLIED', 'INTERVIEW_SCHEDULED', 'SELECTED');

-- Add constraint to ensure only valid enum values are used
-- This will help catch any issues during development
ALTER TABLE candidates ADD CONSTRAINT chk_candidate_status_valid 
CHECK (status::text IN (
    'PENDING', 'APPLIED', 'INTERVIEW_SCHEDULED', 'IN_PROGRESS', 
    'COMPLETED', 'UNDER_REVIEW', 'SELECTED', 'REJECTED', 
    'ON_HOLD', 'WITHDRAWN'
));

-- Update the comment to reflect final enum state
COMMENT ON TYPE candidate_status IS 'Candidate status enum - Final values matching Java enum: PENDING, APPLIED, INTERVIEW_SCHEDULED, IN_PROGRESS, COMPLETED, UNDER_REVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN';

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE 'Successfully synchronized candidate_status enum with Java CandidateStatus enum';
    RAISE NOTICE 'Available values: PENDING, APPLIED, INTERVIEW_SCHEDULED, IN_PROGRESS, COMPLETED, UNDER_REVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN';
END$$;
