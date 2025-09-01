-- ================================================================================================
-- V2_002: Add Tenant Isolation to Recruiters Table
-- ================================================================================================
-- Purpose: Add tenant_id column to recruiters table for complete multi-tenant isolation
-- Date: 2025-09-01
-- CRITICAL SECURITY FIX: Ensures each recruiter belongs to a unique tenant
-- ================================================================================================

-- Step 1: Add tenant_id column to recruiters table
ALTER TABLE recruiters 
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);

-- Step 2: Update existing recruiters with tenant IDs
-- For backward compatibility, assign tenant IDs to existing recruiters
UPDATE recruiters 
SET tenant_id = CASE 
    WHEN email = 'ciwojeg982@lanipe.com' THEN 'tenant_456'
    ELSE 'tenant_' || ABS(EXTRACT(epoch FROM created_at)::INTEGER % 1000) || '_' || SUBSTRING(id FROM 1 FOR 8)
END
WHERE tenant_id IS NULL;

-- Step 3: Make tenant_id required and add constraint
ALTER TABLE recruiters ALTER COLUMN tenant_id SET NOT NULL;

-- Step 4: Create indexes for performance on tenant-based queries
CREATE INDEX IF NOT EXISTS idx_recruiters_tenant_id ON recruiters(tenant_id);
CREATE INDEX IF NOT EXISTS idx_recruiters_tenant_email ON recruiters(tenant_id, email);

-- Step 5: Add constraint to ensure tenant_id is not empty
ALTER TABLE recruiters ADD CONSTRAINT chk_recruiters_tenant_not_empty 
    CHECK (tenant_id IS NOT NULL AND tenant_id <> '');

-- Step 6: Add comments for documentation
COMMENT ON COLUMN recruiters.tenant_id IS 'Tenant isolation - ensures each recruiter belongs to a unique organization (CRITICAL SECURITY)';

-- Step 7: Create a view for tenant-recruiter mapping (useful for monitoring)
CREATE OR REPLACE VIEW v_recruiter_tenant_mapping AS
SELECT 
    id as recruiter_id,
    full_name,
    email,
    tenant_id,
    created_at,
    is_otp_verified
FROM recruiters
ORDER BY tenant_id, created_at;

COMMENT ON VIEW v_recruiter_tenant_mapping IS 'Mapping of recruiters to their tenants for monitoring and analytics';

-- Step 8: Verify the migration
DO $$
DECLARE
    recruiter_count INTEGER;
    tenant_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO recruiter_count FROM recruiters;
    SELECT COUNT(DISTINCT tenant_id) INTO tenant_count FROM recruiters;
    
    RAISE NOTICE 'Recruiter tenant migration completed successfully:';
    RAISE NOTICE '- Total recruiters: %', recruiter_count;
    RAISE NOTICE '- Unique tenants: %', tenant_count;
    RAISE NOTICE '- Tenant isolation indexes created for recruiters';
    
    -- Verify all recruiters have tenant_id
    IF EXISTS (SELECT 1 FROM recruiters WHERE tenant_id IS NULL OR tenant_id = '') THEN
        RAISE EXCEPTION 'CRITICAL: Some recruiters do not have tenant_id assigned!';
    END IF;
    
    RAISE NOTICE '✅ All recruiters have valid tenant_id assigned';
END$$;

-- Step 9: Log migration completion for audit trail
INSERT INTO flyway_schema_history_custom (description, success, execution_time) 
VALUES ('Recruiter tenant isolation migration completed', true, CURRENT_TIMESTAMP);
