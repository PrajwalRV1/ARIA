-- Final update to candidate_status enum to match the exact Java enum specification
-- This migration ensures the database enum exactly matches:
-- PENDING, APPLIED, INTERVIEW_SCHEDULED, IN_PROGRESS, COMPLETED, UNDER_REVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN

-- Add the new INTERVIEW_SCHEDULED value
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'INTERVIEW_SCHEDULED';

-- Update existing records that might have old status values
-- Map 'SCREENING' to 'APPLIED' (if any exist)
UPDATE candidates SET status = 'APPLIED' WHERE status = 'SCREENING';

-- Map 'INTERVIEW' to 'INTERVIEW_SCHEDULED' (if any exist)
UPDATE candidates SET status = 'INTERVIEW_SCHEDULED' WHERE status = 'INTERVIEW';

-- Map 'SCHEDULED' to 'INTERVIEW_SCHEDULED' (if any exist)  
UPDATE candidates SET status = 'INTERVIEW_SCHEDULED' WHERE status = 'SCHEDULED';

-- Map 'HIRED' to 'SELECTED' (if any exist)
UPDATE candidates SET status = 'SELECTED' WHERE status = 'HIRED';

-- Verify enum values are available
-- The complete enum now includes exactly what the Java enum specifies:
-- PENDING, APPLIED, INTERVIEW_SCHEDULED, IN_PROGRESS, COMPLETED, UNDER_REVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN
-- Plus legacy values: SCREENING, INTERVIEW, SCHEDULED, HIRED (which we've mapped above)

-- Add comment to document the final enum state
COMMENT ON TYPE candidate_status IS 'Candidate status enum - Final values: PENDING, APPLIED, INTERVIEW_SCHEDULED, IN_PROGRESS, COMPLETED, UNDER_REVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN';
