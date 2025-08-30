-- Update existing candidate records to use new status values
-- This migration runs after enum values are committed and available
-- Maps old status values to new standardized values

-- Update existing records that might have old status values
-- Map 'SCREENING' to 'APPLIED' (if any exist)
UPDATE candidates SET status = 'APPLIED' WHERE status = 'SCREENING';

-- Map 'INTERVIEW' to 'INTERVIEW_SCHEDULED' (if any exist)
UPDATE candidates SET status = 'INTERVIEW_SCHEDULED' WHERE status = 'INTERVIEW';

-- Map 'SCHEDULED' to 'INTERVIEW_SCHEDULED' (if any exist)  
UPDATE candidates SET status = 'INTERVIEW_SCHEDULED' WHERE status = 'SCHEDULED';

-- Map 'HIRED' to 'SELECTED' (if any exist)
UPDATE candidates SET status = 'SELECTED' WHERE status = 'HIRED';

-- Update the comment to document the final enum state
COMMENT ON TYPE candidate_status IS 'Candidate status enum - Final values: PENDING, APPLIED, INTERVIEW_SCHEDULED, IN_PROGRESS, COMPLETED, UNDER_REVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN';

-- Log the migration completion
DO $$
BEGIN
    RAISE NOTICE 'Successfully updated existing candidate status values to match new enum specification';
END$$;
