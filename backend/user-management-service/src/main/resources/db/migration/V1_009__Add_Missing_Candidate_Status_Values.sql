-- Add missing candidate_status enum values to match Java enum
-- This migration adds the missing enum values that exist in CandidateStatus.java
-- Final enum values: PENDING, APPLIED, INTERVIEW, SCHEDULED, IN_PROGRESS, COMPLETED, 
-- UNDER_REVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN

-- Add missing enum values to candidate_status type
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'PENDING';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'SCHEDULED';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'IN_PROGRESS';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'COMPLETED';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'UNDER_REVIEW';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'SELECTED';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'ON_HOLD';
ALTER TYPE candidate_status ADD VALUE IF NOT EXISTS 'WITHDRAWN';

-- Note: The original migration included 'APPLIED', 'SCREENING', 'INTERVIEW', 'REJECTED', 'HIRED'
-- We're keeping 'APPLIED', 'INTERVIEW', 'REJECTED' and adding the new ones
-- 'SCREENING' and 'HIRED' from original migration remain available but won't be used in Java enum
-- The Java enum now matches: PENDING, APPLIED, INTERVIEW, SCHEDULED, IN_PROGRESS, 
-- COMPLETED, UNDER_REVIEW, SELECTED, REJECTED, ON_HOLD, WITHDRAWN
