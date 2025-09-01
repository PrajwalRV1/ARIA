-- Fix candidate data inconsistencies
-- Update tenant_id from "default" to "tenant_456" for candidates that should belong to that tenant
UPDATE candidates 
SET tenant_id = 'tenant_456' 
WHERE tenant_id = 'default' 
  AND recruiter_id IN ('ciwojeg982@lanipe.com', '3b9f7f1e-57d9-432c-b6d2-ef49349765ac');

-- Optional: If you want to standardize ALL recruiter IDs to email format
-- (This assumes the UUID recruiter IDs should map to the same email)
UPDATE candidates 
SET recruiter_id = 'ciwojeg982@lanipe.com' 
WHERE recruiter_id = '3b9f7f1e-57d9-432c-b6d2-ef49349765ac';

-- Verify the changes
SELECT id, name, email, tenant_id, recruiter_id, created_at 
FROM candidates 
ORDER BY created_at DESC;
