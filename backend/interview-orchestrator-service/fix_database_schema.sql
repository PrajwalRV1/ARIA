-- ================================================================================
-- ARIA Interview Orchestrator - Database Schema Fix Script
-- ================================================================================
-- This script fixes Flyway checksum mismatches and ensures proper UUID schema
-- Run this script as a database admin user with appropriate permissions
--
-- USAGE:
-- 1. Connect to your PostgreSQL database
-- 2. Run this entire script
-- 3. Restart your Spring Boot application
-- 4. Verify that Flyway validation passes
--
-- ================================================================================

\echo '================================================================================'
\echo 'ARIA Interview Orchestrator Database Schema Fix'
\echo '================================================================================'
\echo ''
\echo 'This script will:'
\echo '1. Check current database schema state'
\echo '2. Fix Flyway checksum mismatches if needed'
\echo '3. Ensure all session_id columns use UUID type'
\echo '4. Verify the final schema state'
\echo ''

-- ================================================================================
-- STEP 1: Pre-Fix Schema Verification
-- ================================================================================
\echo 'STEP 1: Checking current database schema state...'
\echo ''

-- Check Flyway schema history for potential checksum issues
\echo 'Current Flyway Schema History:'
SELECT 
    installed_rank,
    version,
    description,
    checksum,
    success,
    CASE 
        WHEN version IN ('1.001', '1.002', '1.003') AND checksum != -1848109661 
        THEN 'CHECKSUM_MISMATCH_LIKELY'
        ELSE 'OK'
    END AS status
FROM flyway_schema_history
ORDER BY installed_rank;

\echo ''
\echo 'Current session_id column types:'
SELECT 
    t.table_name,
    c.data_type,
    CASE 
        WHEN c.data_type = 'uuid' THEN 'CORRECT'
        WHEN c.data_type IN ('character varying', 'varchar', 'text') THEN 'NEEDS_CONVERSION'
        ELSE 'UNEXPECTED_TYPE'
    END AS type_status
FROM information_schema.tables t
JOIN information_schema.columns c ON t.table_name = c.table_name
WHERE t.table_schema = 'public'
  AND c.column_name = 'session_id'
  AND t.table_type = 'BASE TABLE'
ORDER BY t.table_name;

-- ================================================================================
-- STEP 2: Fix Flyway Checksum Mismatches
-- ================================================================================
\echo ''
\echo 'STEP 2: Fixing Flyway checksum mismatches...'

DO $$
DECLARE
    migration_record RECORD;
    needs_fix BOOLEAN := FALSE;
BEGIN
    -- Check if we need to fix checksums
    FOR migration_record IN 
        SELECT version, checksum
        FROM flyway_schema_history 
        WHERE version IN ('1.001', '1.002', '1.003')
          AND success = true
    LOOP
        -- Expected checksums for UUID-based migrations
        IF (migration_record.version = '1.001' AND migration_record.checksum != -1848109661) OR
           (migration_record.version = '1.002' AND migration_record.checksum != -677911012) OR
           (migration_record.version = '1.003' AND migration_record.checksum != 1658187676) THEN
            needs_fix := TRUE;
            EXIT;
        END IF;
    END LOOP;
    
    IF needs_fix THEN
        RAISE NOTICE 'Updating Flyway checksums to match current UUID migration files...';
        
        -- Update checksums to match the current UUID-based migration files
        UPDATE flyway_schema_history 
        SET checksum = -1848109661
        WHERE version = '1.001' AND success = true;
        
        UPDATE flyway_schema_history 
        SET checksum = -677911012
        WHERE version = '1.002' AND success = true;
        
        UPDATE flyway_schema_history 
        SET checksum = 1658187676
        WHERE version = '1.003' AND success = true;
        
        RAISE NOTICE 'Flyway checksums updated successfully';
    ELSE
        RAISE NOTICE 'Flyway checksums are already correct, no update needed';
    END IF;
END
$$;

-- ================================================================================
-- STEP 3: Ensure UUID Column Types
-- ================================================================================
\echo ''
\echo 'STEP 3: Ensuring all session_id columns use UUID type...'

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Convert session_id columns to UUID if they're not already
DO $$
DECLARE
    column_type TEXT;
    conversion_needed BOOLEAN := FALSE;
BEGIN
    -- Check interview_sessions.session_id type
    SELECT data_type INTO column_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'interview_sessions'
      AND column_name = 'session_id';
    
    IF column_type IN ('character varying', 'varchar', 'text', 'character') THEN
        conversion_needed := TRUE;
        RAISE NOTICE 'Converting session_id columns from % to UUID...', column_type;
        
        -- Convert all session_id columns to UUID type
        -- Primary table first
        EXECUTE 'ALTER TABLE interview_sessions ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        
        -- Foreign key tables
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_ice_servers') THEN
            EXECUTE 'ALTER TABLE session_ice_servers ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_question_pool') THEN
            EXECUTE 'ALTER TABLE session_question_pool ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_asked_questions') THEN
            EXECUTE 'ALTER TABLE session_asked_questions ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_ai_metrics') THEN
            EXECUTE 'ALTER TABLE session_ai_metrics ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_tech_stack') THEN
            EXECUTE 'ALTER TABLE session_tech_stack ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        END IF;
        
        -- Response and event tables
        EXECUTE 'ALTER TABLE interview_responses ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        EXECUTE 'ALTER TABLE interview_events ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        
        RAISE NOTICE 'Successfully converted all session_id columns to UUID type';
    ELSE
        RAISE NOTICE 'session_id columns are already UUID type (%), no conversion needed', column_type;
    END IF;
END
$$;

-- ================================================================================
-- STEP 4: Post-Fix Verification
-- ================================================================================
\echo ''
\echo 'STEP 4: Verifying the fixed database schema...'
\echo ''

-- Verify Flyway schema history
\echo 'Updated Flyway Schema History:'
SELECT 
    installed_rank,
    version,
    description,
    checksum,
    success,
    CASE 
        WHEN version = '1.001' AND checksum = -1848109661 THEN 'CORRECT'
        WHEN version = '1.002' AND checksum = -677911012 THEN 'CORRECT'
        WHEN version = '1.003' AND checksum = 1658187676 THEN 'CORRECT'
        WHEN version NOT IN ('1.001', '1.002', '1.003') THEN 'OK'
        ELSE 'CHECKSUM_ISSUE'
    END AS checksum_status
FROM flyway_schema_history
ORDER BY installed_rank;

-- Verify column types
\echo ''
\echo 'Final session_id column types:'
SELECT 
    t.table_name,
    c.data_type,
    c.is_nullable,
    CASE 
        WHEN c.data_type = 'uuid' THEN 'CORRECT'
        ELSE 'ISSUE'
    END AS type_status
FROM information_schema.tables t
JOIN information_schema.columns c ON t.table_name = c.table_name
WHERE t.table_schema = 'public'
  AND c.column_name = 'session_id'
  AND t.table_type = 'BASE TABLE'
ORDER BY t.table_name;

-- Verify foreign key constraints
\echo ''
\echo 'Foreign key relationships:'
SELECT 
    tc.table_name AS child_table,
    kcu.column_name AS child_column,
    ccu.table_name AS parent_table,
    ccu.column_name AS parent_column
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage ccu 
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = 'public'
  AND kcu.column_name = 'session_id'
ORDER BY tc.table_name;

-- Final summary
\echo ''
\echo '================================================================================'
\echo 'SCHEMA FIX COMPLETE'
\echo '================================================================================'
\echo ''
\echo 'Summary:'
SELECT 
    COUNT(*) AS total_tables_with_session_id,
    COUNT(CASE WHEN c.data_type = 'uuid' THEN 1 END) AS uuid_columns,
    COUNT(CASE WHEN c.data_type != 'uuid' THEN 1 END) AS non_uuid_columns,
    CASE 
        WHEN COUNT(CASE WHEN c.data_type != 'uuid' THEN 1 END) = 0 
        THEN 'ALL_COLUMNS_ARE_UUID - READY_FOR_SPRING_BOOT'
        ELSE 'SOME_COLUMNS_NEED_ATTENTION'
    END AS overall_status
FROM information_schema.tables t
JOIN information_schema.columns c ON t.table_name = c.table_name
WHERE t.table_schema = 'public'
  AND c.column_name = 'session_id'
  AND t.table_type = 'BASE TABLE';

\echo ''
\echo 'Next steps:'
\echo '1. Restart your Spring Boot application'
\echo '2. Check application logs for Flyway validation success'
\echo '3. Test creating new interview sessions to verify UUID functionality'
\echo '';
