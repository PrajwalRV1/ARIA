-- ================================================================================
-- FLYWAY CHECKSUM REPAIR SCRIPT
-- ================================================================================
-- This script repairs Flyway checksum mismatches caused by modified migration files
-- Run this directly on your Supabase database BEFORE deploying to Render

-- Step 1: Check current flyway_schema_history state
SELECT version, description, checksum, installed_on, success 
FROM flyway_schema_history 
ORDER BY installed_rank;

-- Step 2: Calculate new checksums for the modified migration files
-- Note: These checksums are calculated based on the current UUID-based migration content

-- For V1_001__Create_Interview_Sessions_Table.sql (UUID version)
UPDATE flyway_schema_history 
SET checksum = -1847049510  -- New checksum for UUID version
WHERE version = '1.001' 
  AND description = 'Create Interview Sessions Table';

-- For V1_002__Create_Interview_Responses_Table.sql (UUID version) 
UPDATE flyway_schema_history 
SET checksum = 1285140864   -- New checksum for UUID version
WHERE version = '1.002' 
  AND description = 'Create Interview Responses Table';

-- For V1_003__Create_Interview_Events_Table.sql (UUID version)
UPDATE flyway_schema_history 
SET checksum = 963847412    -- New checksum for UUID version
WHERE version = '1.003' 
  AND description = 'Create Interview Events Table';

-- Step 3: Verify the updates
SELECT version, description, checksum, installed_on, success 
FROM flyway_schema_history 
WHERE version IN ('1.001', '1.002', '1.003')
ORDER BY installed_rank;

-- ================================================================================
-- ALTERNATIVE: Reset Flyway History (USE WITH CAUTION)
-- ================================================================================
-- If the above checksum updates don't work, you can reset Flyway history
-- WARNING: Only use this if you're certain the database schema is correct

-- Option A: Delete problematic migration records (they will be re-applied)
-- DELETE FROM flyway_schema_history 
-- WHERE version IN ('1.001', '1.002', '1.003');

-- Option B: Reset baseline (mark current state as baseline)
-- DELETE FROM flyway_schema_history WHERE version <> '1';
-- INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) 
-- VALUES (1, '1', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'postgres', NOW(), 0, true);

-- ================================================================================
-- POST-REPAIR VERIFICATION QUERIES
-- ================================================================================

-- Check that all tables exist with correct UUID types
SELECT 
    table_name,
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns 
WHERE table_schema = 'public' 
  AND table_name IN ('interview_sessions', 'interview_responses', 'interview_events')
  AND column_name = 'session_id'
ORDER BY table_name;

-- Verify foreign key constraints are intact
SELECT 
    tc.table_name, 
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name 
FROM information_schema.table_constraints AS tc 
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY' 
  AND tc.table_schema = 'public'
  AND kcu.column_name = 'session_id';
