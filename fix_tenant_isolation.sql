-- Fix tenant isolation for recruiter's candidates
-- Update only the candidates created by this specific recruiter to have the correct tenant ID
UPDATE candidates 
SET tenant_id = 'tenant_456' 
WHERE recruiter_id = 'ciwojeg982@lanipe.com'
  AND tenant_id = 'default';

-- The other candidates with UUID recruiter_id belong to a different recruiter/tenant
-- They should keep tenant_id = 'default' unless we know their correct tenant

-- Verify the changes
SELECT id, name, email, tenant_id, recruiter_id, created_at 
FROM candidates 
ORDER BY created_at DESC;

-- Expected result after this update:
-- ID 22: tenant_id = 'tenant_456', recruiter_id = 'ciwojeg982@lanipe.com'
-- ID 20,21: tenant_id = 'default', recruiter_id = '3b9f7f1e-57d9-432c-b6d2-ef49349765ac'
