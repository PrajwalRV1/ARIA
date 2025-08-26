-- ================================================================================
-- V1.005 - EMERGENCY ROLLBACK: Convert session_id columns from UUID to VARCHAR(36)
-- ================================================================================
-- WARNING: This is an EMERGENCY ROLLBACK migration
-- PURPOSE: Converts UUID columns back to VARCHAR(36) if V1_004 caused issues
-- DATA RISK: Potential data loss if UUID values exceed VARCHAR(36) format
-- USE ONLY: When instructed by database administrator or development team
--
-- IMPORTANT: This file should be renamed to proper migration version only when needed
-- Current filename prevents automatic execution by Flyway

-- ⚠️  EMERGENCY USE ONLY - RENAME TO V1_005__Emergency_Rollback_UUID_To_VARCHAR.sql TO ACTIVATE

-- Check current state before rollback
DO $$
DECLARE
    current_type TEXT;
    uuid_count INTEGER;
BEGIN
    -- Check current column type
    SELECT data_type INTO current_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'interview_sessions'
      AND column_name = 'session_id';
    
    RAISE NOTICE 'Current session_id type: %', current_type;
    
    -- Only proceed if currently UUID type
    IF current_type = 'uuid' THEN
        -- Count total records that would be affected
        SELECT COUNT(*) INTO uuid_count FROM interview_sessions;
        
        RAISE NOTICE 'EMERGENCY ROLLBACK: Converting % session records from UUID to VARCHAR(36)', uuid_count;
        RAISE NOTICE 'WARNING: This operation may cause data integrity issues';
        
        -- Step 1: Convert interview_sessions (primary table)
        ALTER TABLE interview_sessions 
        ALTER COLUMN session_id TYPE VARCHAR(36) USING session_id::TEXT;
        
        -- Step 2: Convert session-related tables
        ALTER TABLE session_ice_servers 
        ALTER COLUMN session_id TYPE VARCHAR(36) USING session_id::TEXT;
        
        ALTER TABLE session_question_pool 
        ALTER COLUMN session_id TYPE VARCHAR(36) USING session_id::TEXT;
        
        ALTER TABLE session_asked_questions 
        ALTER COLUMN session_id TYPE VARCHAR(36) USING session_id::TEXT;
        
        ALTER TABLE session_ai_metrics 
        ALTER COLUMN session_id TYPE VARCHAR(36) USING session_id::TEXT;
        
        ALTER TABLE session_tech_stack 
        ALTER COLUMN session_id TYPE VARCHAR(36) USING session_id::TEXT;
        
        -- Step 3: Convert interview_responses
        ALTER TABLE interview_responses 
        ALTER COLUMN session_id TYPE VARCHAR(36) USING session_id::TEXT;
        
        -- Step 4: Convert interview_events
        ALTER TABLE interview_events 
        ALTER COLUMN session_id TYPE VARCHAR(36) USING session_id::TEXT;
        
        RAISE NOTICE 'EMERGENCY ROLLBACK COMPLETED: All session_id columns converted to VARCHAR(36)';
        RAISE NOTICE 'CRITICAL: Verify application compatibility before proceeding';
        
    ELSE
        RAISE NOTICE 'ROLLBACK SKIPPED: session_id is not UUID type (current: %)', current_type;
    END IF;
END
$$;

-- Verify rollback success
DO $$
DECLARE
    table_rec RECORD;
    all_varchar BOOLEAN := TRUE;
BEGIN
    FOR table_rec IN
        SELECT t.table_name, c.data_type
        FROM information_schema.tables t
        JOIN information_schema.columns c ON t.table_name = c.table_name
        WHERE t.table_schema = 'public'
          AND c.column_name = 'session_id'
          AND t.table_type = 'BASE TABLE'
        ORDER BY t.table_name
    LOOP
        IF table_rec.data_type != 'character varying' THEN
            all_varchar := FALSE;
            RAISE WARNING 'Table % still has session_id type: %', table_rec.table_name, table_rec.data_type;
        END IF;
    END LOOP;
    
    IF all_varchar THEN
        RAISE NOTICE 'ROLLBACK VERIFICATION: All session_id columns are now VARCHAR type';
    ELSE
        RAISE EXCEPTION 'ROLLBACK FAILED: Some session_id columns are not VARCHAR type';
    END IF;
END
$$;
