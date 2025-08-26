-- ================================================================================
-- V1.004 - Convert session_id columns from VARCHAR(36) to UUID type
-- ================================================================================
-- PURPOSE: Aligns database schema with JPA entity expectations (UUID type)
-- SAFETY: Preserves all data during conversion using PostgreSQL CAST operations
-- ROLLBACK: This migration cannot be automatically rolled back due to type conversion
--          Manual rollback requires converting UUID back to VARCHAR(36)

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Validate that all session_id values are valid UUIDs before conversion
DO $$
DECLARE
    invalid_count INTEGER;
    current_type TEXT;
BEGIN
    -- Check current column type
    SELECT data_type INTO current_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'interview_sessions'
      AND column_name = 'session_id';
    
    -- Only proceed if currently VARCHAR/TEXT type
    IF current_type IN ('character varying', 'varchar', 'text', 'character') THEN
        RAISE NOTICE 'Current session_id type: %. Starting UUID conversion...', current_type;
        
        -- Validate UUID format in interview_sessions
        SELECT COUNT(*) INTO invalid_count
        FROM interview_sessions
        WHERE session_id !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
        
        IF invalid_count > 0 THEN
            RAISE EXCEPTION 'Found % invalid UUID format(s) in interview_sessions.session_id. Migration aborted.', invalid_count;
        END IF;
        
        -- Validate UUID format in interview_responses
        SELECT COUNT(*) INTO invalid_count
        FROM interview_responses
        WHERE session_id !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
        
        IF invalid_count > 0 THEN
            RAISE EXCEPTION 'Found % invalid UUID format(s) in interview_responses.session_id. Migration aborted.', invalid_count;
        END IF;
        
        -- Validate UUID format in interview_events
        SELECT COUNT(*) INTO invalid_count
        FROM interview_events
        WHERE session_id !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
        
        IF invalid_count > 0 THEN
            RAISE EXCEPTION 'Found % invalid UUID format(s) in interview_events.session_id. Migration aborted.', invalid_count;
        END IF;
        
        RAISE NOTICE 'UUID validation passed. Proceeding with conversion...';
        
    ELSE
        RAISE NOTICE 'session_id columns are already UUID type (%). Skipping conversion.', current_type;
        RETURN;
    END IF;
END
$$;

-- Perform the actual conversion
DO $$
DECLARE
    current_type TEXT;
BEGIN
    -- Double-check current type before conversion
    SELECT data_type INTO current_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'interview_sessions'
      AND column_name = 'session_id';
    
    IF current_type IN ('character varying', 'varchar', 'text', 'character') THEN
        -- Step 1: Convert primary table (interview_sessions)
        ALTER TABLE interview_sessions 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        -- Step 2: Convert session-related tables
        ALTER TABLE session_ice_servers 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        ALTER TABLE session_question_pool 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        ALTER TABLE session_asked_questions 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        ALTER TABLE session_ai_metrics 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        ALTER TABLE session_tech_stack 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        -- Step 3: Convert interview_responses
        ALTER TABLE interview_responses 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        -- Step 4: Convert interview_events
        ALTER TABLE interview_events 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        RAISE NOTICE 'Successfully converted all session_id columns from VARCHAR(36) to UUID';
    ELSE
        RAISE NOTICE 'No conversion needed - session_id is already UUID type';
    END IF;
END
$$;

-- Verify conversion success
DO $$
DECLARE
    table_rec RECORD;
    all_uuid BOOLEAN := TRUE;
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
        IF table_rec.data_type != 'uuid' THEN
            all_uuid := FALSE;
            RAISE WARNING 'Table % still has session_id type: %', table_rec.table_name, table_rec.data_type;
        END IF;
    END LOOP;
    
    IF all_uuid THEN
        RAISE NOTICE 'SUCCESS: All session_id columns are now UUID type';
    ELSE
        RAISE EXCEPTION 'FAILURE: Some session_id columns are not UUID type';
    END IF;
END
$$;
