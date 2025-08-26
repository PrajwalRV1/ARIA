-- ================================================================================
-- Database Schema Verification Script
-- ================================================================================
-- This script verifies that all tables are properly created and session_id columns
-- are using UUID type as expected by the JPA entities

-- Check if UUID extension is enabled
SELECT EXISTS(
    SELECT 1 FROM pg_extension WHERE extname = 'uuid-ossp'
) AS "uuid_extension_enabled";

-- ================================================================================
-- Verify Table Existence
-- ================================================================================
SELECT 
    schemaname,
    tablename,
    CASE 
        WHEN tablename IN ('interview_sessions', 'interview_responses', 'interview_events', 'flyway_schema_history') 
        THEN 'EXPECTED'
        ELSE 'ADDITIONAL'
    END AS table_status
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY tablename;

-- ================================================================================
-- Verify session_id Column Types
-- ================================================================================
SELECT 
    t.table_name,
    c.column_name,
    c.data_type,
    c.is_nullable,
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
-- Check Primary Keys
-- ================================================================================
SELECT 
    tc.table_name,
    kcu.column_name,
    tc.constraint_name,
    tc.constraint_type
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.constraint_type = 'PRIMARY KEY'
  AND tc.table_schema = 'public'
  AND tc.table_name IN ('interview_sessions', 'interview_responses', 'interview_events')
ORDER BY tc.table_name;

-- ================================================================================
-- Check Foreign Key Relationships
-- ================================================================================
SELECT 
    tc.table_name AS child_table,
    kcu.column_name AS child_column,
    ccu.table_name AS parent_table,
    ccu.column_name AS parent_column,
    tc.constraint_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage ccu 
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = 'public'
  AND (tc.table_name IN ('interview_responses', 'interview_events') 
       OR ccu.table_name IN ('interview_sessions'))
ORDER BY tc.table_name, kcu.column_name;

-- ================================================================================
-- Check Flyway Schema History
-- ================================================================================
SELECT 
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_by,
    installed_on,
    execution_time,
    success
FROM flyway_schema_history
ORDER BY installed_rank;

-- ================================================================================
-- Verify Data Consistency (if any data exists)
-- ================================================================================
-- Check if session_id values in related tables are valid UUIDs
SELECT 'interview_sessions' AS table_name, COUNT(*) AS total_records
FROM interview_sessions
UNION ALL
SELECT 'interview_responses' AS table_name, COUNT(*) AS total_records
FROM interview_responses
UNION ALL
SELECT 'interview_events' AS table_name, COUNT(*) AS total_records
FROM interview_events;

-- ================================================================================
-- Sample Data Type Verification (if data exists)
-- ================================================================================
-- This will show actual session_id values and their types
SELECT 
    'interview_sessions' AS source_table,
    session_id,
    pg_typeof(session_id) AS actual_type
FROM interview_sessions
LIMIT 3
UNION ALL
SELECT 
    'interview_responses' AS source_table,
    session_id,
    pg_typeof(session_id) AS actual_type
FROM interview_responses
LIMIT 3
UNION ALL
SELECT 
    'interview_events' AS source_table,
    session_id,
    pg_typeof(session_id) AS actual_type
FROM interview_events
LIMIT 3;

-- ================================================================================
-- Summary Report
-- ================================================================================
SELECT 
    'Schema Verification Complete' AS status,
    CURRENT_TIMESTAMP AS verified_at;
