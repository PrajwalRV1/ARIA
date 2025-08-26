-- ================================================================================
-- V1.004 - Convert session_id columns from VARCHAR(36) to UUID type
-- ================================================================================
-- This migration fixes the column type mismatch between JPA entities and database schema
-- It handles both cases: when columns are already UUID or when they need conversion

-- Check if conversion is needed by testing the data type of interview_sessions.session_id
DO $$
DECLARE
    column_type TEXT;
BEGIN
    -- Get the current data type of session_id in interview_sessions
    SELECT data_type INTO column_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'interview_sessions'
      AND column_name = 'session_id';
    
    -- Only perform conversion if the column is currently VARCHAR/TEXT
    IF column_type IN ('character varying', 'varchar', 'text', 'character') THEN
        RAISE NOTICE 'Converting session_id columns from % to UUID', column_type;
        
        -- Step 1: Convert interview_sessions.session_id from VARCHAR(36) to UUID
        EXECUTE 'ALTER TABLE interview_sessions ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        
        -- Step 2: Convert all foreign key references to UUID
        EXECUTE 'ALTER TABLE session_ice_servers ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        EXECUTE 'ALTER TABLE session_question_pool ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        EXECUTE 'ALTER TABLE session_asked_questions ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        EXECUTE 'ALTER TABLE session_ai_metrics ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        EXECUTE 'ALTER TABLE session_tech_stack ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        
        -- Step 3: Convert interview_responses.session_id from VARCHAR(36) to UUID
        EXECUTE 'ALTER TABLE interview_responses ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        
        -- Step 4: Convert interview_events.session_id from VARCHAR(36) to UUID
        EXECUTE 'ALTER TABLE interview_events ALTER COLUMN session_id TYPE UUID USING session_id::UUID';
        
        RAISE NOTICE 'Successfully converted all session_id columns to UUID type';
    ELSE
        RAISE NOTICE 'session_id columns are already UUID type (%), no conversion needed', column_type;
    END IF;
END
$$;
