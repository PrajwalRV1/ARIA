-- ================================================================================================
-- V2_001: Add Tenant Isolation to Candidates Table
-- ================================================================================================
-- Purpose: Add tenant_id columns and indexes to enforce data isolation between organizations
-- Date: 2025-08-30
-- CRITICAL SECURITY FIX: Prevents horizontal privilege escalation (BOLA)
-- ================================================================================================

-- Step 1: Add tenant isolation columns to candidates table
ALTER TABLE candidates 
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255) NOT NULL DEFAULT 'default',
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- Step 2: Update existing records with default tenant
-- This ensures all existing data has a tenant assigned
UPDATE candidates SET tenant_id = 'default' WHERE tenant_id IS NULL OR tenant_id = '';

-- Step 3: Create indexes for performance on tenant-based queries
-- These indexes are critical for performance when filtering by tenant
CREATE INDEX IF NOT EXISTS idx_candidates_tenant_recruiter ON candidates(tenant_id, recruiter_id);
CREATE INDEX IF NOT EXISTS idx_candidates_tenant_status ON candidates(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_candidates_tenant_requisition ON candidates(tenant_id, requisition_id);
CREATE INDEX IF NOT EXISTS idx_candidates_tenant_created ON candidates(tenant_id, created_at);

-- Step 4: Create composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_candidates_tenant_status_created ON candidates(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_candidates_tenant_recruiter_status ON candidates(tenant_id, recruiter_id, status);

-- Step 5: Add constraint to ensure tenant_id is not null or empty
-- PostgreSQL doesn't support IF NOT EXISTS with ADD CONSTRAINT, so we check first
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'chk_candidates_tenant_not_empty' 
        AND table_name = 'candidates'
    ) THEN
        ALTER TABLE candidates ADD CONSTRAINT chk_candidates_tenant_not_empty 
            CHECK (tenant_id IS NOT NULL AND tenant_id <> '');
    END IF;
END$$;

-- Step 6: Add foreign key constraint if tenants table exists (commented out for now)
-- This would be enabled once a proper tenants table is implemented
-- ALTER TABLE candidates ADD CONSTRAINT fk_candidates_tenant 
--   FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- Step 7: Add audit triggers for created_by and updated_by (PostgreSQL)
-- Create a function to update audit fields
CREATE OR REPLACE FUNCTION update_candidate_audit_fields()
RETURNS TRIGGER AS $$
BEGIN
    -- Set updated_by from current user context (if available)
    -- This would be set by the application, but we provide a fallback
    IF NEW.updated_by IS NULL THEN
        NEW.updated_by = COALESCE(current_setting('app.current_user_id', true), 'system');
    END IF;
    
    -- Ensure updated_at is always set
    NEW.updated_at = CURRENT_TIMESTAMP;
    
    -- For inserts, set created_by
    IF TG_OP = 'INSERT' THEN
        IF NEW.created_by IS NULL THEN
            NEW.created_by = COALESCE(current_setting('app.current_user_id', true), 'system');
        END IF;
        NEW.created_at = CURRENT_TIMESTAMP;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for audit fields
DROP TRIGGER IF EXISTS trg_candidates_audit ON candidates;
CREATE TRIGGER trg_candidates_audit
    BEFORE INSERT OR UPDATE ON candidates
    FOR EACH ROW
    EXECUTE FUNCTION update_candidate_audit_fields();

-- Step 8: Add comments for documentation
COMMENT ON COLUMN candidates.tenant_id IS 'Tenant isolation - ensures data separation between organizations (CRITICAL SECURITY)';
COMMENT ON COLUMN candidates.created_by IS 'User who created this candidate record (audit trail)';  
COMMENT ON COLUMN candidates.updated_by IS 'User who last updated this candidate record (audit trail)';

-- Step 9: Create a view for tenant statistics (optional, for monitoring)
CREATE OR REPLACE VIEW v_tenant_candidate_stats AS
SELECT 
    tenant_id,
    COUNT(*) as total_candidates,
    COUNT(DISTINCT recruiter_id) as unique_recruiters,
    COUNT(DISTINCT status) as status_variety,
    MIN(created_at) as first_candidate_date,
    MAX(created_at) as last_candidate_date,
    COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE - INTERVAL '30 days') as recent_candidates
FROM candidates
GROUP BY tenant_id;

COMMENT ON VIEW v_tenant_candidate_stats IS 'Tenant-level candidate statistics for monitoring and analytics';

-- Step 10: Create security policy (Row Level Security - RLS) for additional protection
-- Note: This is commented out initially as it requires application-level session context
-- Enable RLS on candidates table (uncomment when ready)
-- ALTER TABLE candidates ENABLE ROW LEVEL SECURITY;

-- Create policy to ensure users only see their tenant's data
-- CREATE POLICY candidates_tenant_isolation ON candidates
--     FOR ALL
--     TO PUBLIC
--     USING (tenant_id = current_setting('app.current_tenant_id', true));

-- Create policy for admins to access all tenants (uncomment when ready)
-- CREATE POLICY candidates_admin_access ON candidates
--     FOR ALL
--     TO PUBLIC
--     USING (
--         current_setting('app.current_user_role', true) = 'ADMIN' OR
--         tenant_id = current_setting('app.current_tenant_id', true)
--     );

-- Step 11: Verify the migration
-- Count records by tenant to ensure data integrity
DO $$
DECLARE
    tenant_count INTEGER;
    total_candidates INTEGER;
BEGIN
    SELECT COUNT(DISTINCT tenant_id) INTO tenant_count FROM candidates;
    SELECT COUNT(*) INTO total_candidates FROM candidates;
    
    RAISE NOTICE 'Migration completed successfully:';
    RAISE NOTICE '- Total candidates: %', total_candidates;
    RAISE NOTICE '- Unique tenants: %', tenant_count;
    RAISE NOTICE '- Tenant isolation indexes created';
    RAISE NOTICE '- Audit triggers enabled';
    
    -- Verify all records have tenant_id
    IF EXISTS (SELECT 1 FROM candidates WHERE tenant_id IS NULL OR tenant_id = '') THEN
        RAISE EXCEPTION 'CRITICAL: Some candidates do not have tenant_id assigned!';
    END IF;
    
    RAISE NOTICE '✅ All candidates have valid tenant_id assigned';
END$$;

-- Step 12: Log migration completion for audit trail
-- Create custom history table if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'flyway_schema_history_custom'
    ) THEN
        CREATE TABLE flyway_schema_history_custom (
            id SERIAL PRIMARY KEY,
            description TEXT,
            success BOOLEAN,
            execution_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    END IF;
    
    INSERT INTO flyway_schema_history_custom (description, success, execution_time) 
    VALUES ('Tenant isolation migration completed', true, CURRENT_TIMESTAMP);
END$$;
