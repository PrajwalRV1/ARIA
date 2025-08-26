-- V1.004 - Convert session_id columns from VARCHAR(36) to UUID type
-- This migration fixes the column type mismatch between JPA entities and database schema

-- Step 1: Convert interview_sessions.session_id from VARCHAR(36) to UUID
ALTER TABLE interview_sessions ALTER COLUMN session_id TYPE UUID USING session_id::UUID;

-- Step 2: Convert all foreign key references to UUID
ALTER TABLE session_ice_servers ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
ALTER TABLE session_question_pool ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
ALTER TABLE session_asked_questions ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
ALTER TABLE session_ai_metrics ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
ALTER TABLE session_tech_stack ALTER COLUMN session_id TYPE UUID USING session_id::UUID;

-- Step 3: Convert interview_responses.session_id from VARCHAR(36) to UUID
ALTER TABLE interview_responses ALTER COLUMN session_id TYPE UUID USING session_id::UUID;

-- Step 4: Convert interview_events.session_id from VARCHAR(36) to UUID
ALTER TABLE interview_events ALTER COLUMN session_id TYPE UUID USING session_id::UUID;
