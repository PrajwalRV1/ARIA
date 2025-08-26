# Flyway Repair Strategy for ARIA Interview Orchestrator

## Overview

This document outlines the repair strategy for fixing Flyway checksum mismatches after restoring the original migration files to their historically correct state.

## When to Use Flyway Repair

**Use `flyway repair` ONLY if:**
1. You have restored the original migration files (V1_001, V1_002, V1_003) to their VARCHAR(36) versions
2. The database still shows checksum mismatches despite the restored files
3. You have confirmed the migrations were already successfully applied to production

**DO NOT use `flyway repair` if:**
- The migration files have been modified but not yet deployed
- You're unsure about the production database state
- The checksums naturally match after file restoration

## Step-by-Step Repair Process

### 1. Verify Current State

First, check the current Flyway schema history:

```sql
SELECT 
    installed_rank,
    version,
    description,
    checksum,
    success,
    installed_on
FROM flyway_schema_history 
ORDER BY installed_rank;
```

### 2. Verify Migration File Restoration

Confirm that migration files have been restored to their original VARCHAR(36) versions:

```bash
# Check that session_id is defined as VARCHAR(36) in restored files
grep -n "session_id.*VARCHAR" src/main/resources/db/migration/V1_00*.sql
```

Expected output should show:
- V1_001: `session_id VARCHAR(36) NOT NULL PRIMARY KEY`
- V1_002: `session_id VARCHAR(36) NOT NULL`  
- V1_003: `session_id VARCHAR(36) NOT NULL`

### 3. Calculate Expected Checksums

Run this locally to get the checksums for the restored files:

```bash
# Connect to a local database and run migrations to see expected checksums
# OR use Flyway info command to see calculated checksums
flyway info -configFiles=src/main/resources/application-production.properties
```

### 4. Production Database Repair (If Needed)

**⚠️ PRODUCTION OPERATION - Use with extreme caution**

```bash
# Connect to production database
flyway repair -configFiles=src/main/resources/application-production.properties
```

This command will:
- Remove failed migration entries from flyway_schema_history
- Realign checksums for successfully applied migrations

### 5. Verify Repair Success

After repair, verify the state:

```sql
-- Check that all migration records show success=true and updated checksums
SELECT 
    version,
    description,
    checksum,
    success,
    CASE 
        WHEN success = true THEN 'OK'
        ELSE 'FAILED'
    END AS status
FROM flyway_schema_history 
WHERE version IN ('1.001', '1.002', '1.003')
ORDER BY version;
```

## Alternative: Manual Checksum Update

If `flyway repair` is not available or preferred, you can manually update checksums:

```sql
-- WARNING: Only use if you're certain of the correct checksum values
-- These values should match the checksums of the restored VARCHAR(36) migration files

-- Example (replace with actual calculated checksums):
UPDATE flyway_schema_history 
SET checksum = [CALCULATED_CHECKSUM_FOR_V1_001] 
WHERE version = '1.001' AND success = true;

UPDATE flyway_schema_history 
SET checksum = [CALCULATED_CHECKSUM_FOR_V1_002] 
WHERE version = '1.002' AND success = true;

UPDATE flyway_schema_history 
SET checksum = [CALCULATED_CHECKSUM_FOR_V1_003] 
WHERE version = '1.003' AND success = true;
```

## Post-Repair Verification

1. **Test Migration V1_004**: The new UUID conversion migration should now run successfully
2. **Verify Schema State**: Confirm all session_id columns are converted to UUID
3. **Test Application**: Ensure Hibernate validation passes with UUID entities

## Rollback Strategy

If the repair process fails:

1. **Database Rollback**: Restore database from backup taken before repair
2. **Migration Rollback**: Revert to the UUID-based migration files if needed
3. **Alternative Approach**: Use the comprehensive fix script instead of migration-based approach

## Emergency Contacts

- Database Administrator: [Contact Information]
- DevOps Team: [Contact Information]
- Backup/Recovery Team: [Contact Information]

## Monitoring

After repair and deployment, monitor:

- Application startup logs for Flyway validation success
- Hibernate schema validation messages
- Any UUID-related errors in application logs
- Database connection and query performance
