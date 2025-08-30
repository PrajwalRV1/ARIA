-- Add only the new INTERVIEW_SCHEDULED enum value
-- PostgreSQL requires enum additions to be committed before they can be used
-- This migration only adds the enum value, updates will be in the next migration

-- Add the new INTERVIEW_SCHEDULED value
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'INTERVIEW_SCHEDULED';

-- Add comment to document this step
COMMENT ON TYPE candidate_status IS 'Added INTERVIEW_SCHEDULED to candidate_status enum';
