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

-- Perform the actual conversion with proper foreign key handling
DO $$
DECLARE
    current_type TEXT;
    constraint_rec RECORD;
BEGIN
    -- Double-check current type before conversion
    SELECT data_type INTO current_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'interview_sessions'
      AND column_name = 'session_id';
    
    IF current_type IN ('character varying', 'varchar', 'text', 'character') THEN
        RAISE NOTICE 'Starting UUID conversion process...';
        
        -- Step 1: Drop all foreign key constraints that reference interview_sessions.session_id
        RAISE NOTICE 'Dropping foreign key constraints...';
        
        FOR constraint_rec IN
            SELECT tc.constraint_name, tc.table_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu 
                ON tc.constraint_name = kcu.constraint_name
            JOIN information_schema.constraint_column_usage ccu 
                ON ccu.constraint_name = tc.constraint_name
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_schema = 'public'
              AND ccu.table_name = 'interview_sessions'
              AND ccu.column_name = 'session_id'
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', constraint_rec.table_name, constraint_rec.constraint_name);
            RAISE NOTICE 'Dropped constraint: % from table %', constraint_rec.constraint_name, constraint_rec.table_name;
        END LOOP;
        
        -- Step 2: Convert all session_id columns to UUID simultaneously
        RAISE NOTICE 'Converting all session_id columns to UUID...';
        
        -- Primary table
        ALTER TABLE interview_sessions 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        -- Session-related tables (if they exist)
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_ice_servers') THEN
            ALTER TABLE session_ice_servers 
            ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_question_pool') THEN
            ALTER TABLE session_question_pool 
            ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_asked_questions') THEN
            ALTER TABLE session_asked_questions 
            ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_ai_metrics') THEN
            ALTER TABLE session_ai_metrics 
            ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_tech_stack') THEN
            ALTER TABLE session_tech_stack 
            ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        END IF;
        
        -- Main related tables
        ALTER TABLE interview_responses 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        ALTER TABLE interview_events 
        ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
        
        -- Step 3: Recreate foreign key constraints
        RAISE NOTICE 'Recreating foreign key constraints...';
        
        -- Recreate constraints for session-related tables
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_ice_servers') THEN
            ALTER TABLE session_ice_servers 
            ADD CONSTRAINT session_ice_servers_session_id_fkey 
            FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_question_pool') THEN
            ALTER TABLE session_question_pool 
            ADD CONSTRAINT session_question_pool_session_id_fkey 
            FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_asked_questions') THEN
            ALTER TABLE session_asked_questions 
            ADD CONSTRAINT session_asked_questions_session_id_fkey 
            FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_ai_metrics') THEN
            ALTER TABLE session_ai_metrics 
            ADD CONSTRAINT session_ai_metrics_session_id_fkey 
            FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'session_tech_stack') THEN
            ALTER TABLE session_tech_stack 
            ADD CONSTRAINT session_tech_stack_session_id_fkey 
            FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE;
        END IF;
        
        -- Recreate constraints for main related tables
        ALTER TABLE interview_responses 
        ADD CONSTRAINT interview_responses_session_id_fkey 
        FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE;
        
        ALTER TABLE interview_events 
        ADD CONSTRAINT interview_events_session_id_fkey 
        FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE;
        
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
