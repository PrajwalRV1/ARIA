# ARIA Interview Orchestrator - Database Schema Fix

## Problem Summary

The ARIA Interview Orchestrator service was experiencing Flyway validation errors due to a checksum mismatch between the stored migration history and the current migration files. This occurred because:

1. **Migration Evolution**: The original migrations used `VARCHAR(36)` for session_id columns
2. **JPA Entity Mismatch**: JPA entities expect `UUID` type for sessionId fields  
3. **Migration Updates**: Migration files were updated to use UUID, but Flyway's stored checksums still referenced the old VARCHAR versions
4. **Validation Failure**: Flyway validation fails when checksums don't match between stored history and current files

## Root Cause

The issue stemmed from:
- Initial migrations created `session_id` columns as `VARCHAR(36)`
- JPA entities were configured to use `UUID` type for `sessionId` fields
- Migration files were later updated to use `UUID` type to match JPA expectations
- Flyway schema history table retained checksums from the old VARCHAR-based migrations
- This created a checksum mismatch causing validation to fail

## Solution Overview

The fix consists of three key components:

### 1. Improved V1_004 Migration (`V1_004__Convert_Session_ID_to_UUID.sql`)
- **Smart Conversion**: Checks current column types and only converts if needed
- **Idempotent**: Can be safely run multiple times
- **Comprehensive**: Handles all related tables with session_id columns
- **Error Handling**: Uses PostgreSQL PL/pgSQL blocks for better error handling

### 2. Comprehensive Fix Script (`fix_database_schema.sql`)
- **All-in-One**: Complete solution that fixes both checksum and schema issues
- **Verification**: Pre and post-fix verification of database state
- **Safety Checks**: Validates current state before making changes
- **Detailed Output**: Clear progress reporting and status verification

### 3. Verification Tools
- **Schema Verification** (`verify_schema.sql`): Standalone script to check database state
- **Detailed Reporting**: Comprehensive analysis of tables, columns, constraints, and data consistency

## Files Created/Modified

| File | Purpose |
|------|---------|
| `V1_004__Convert_Session_ID_to_UUID.sql` | Improved migration with smart UUID conversion |
| `fix_database_schema.sql` | Complete fix script (checksum + schema) |
| `verify_schema.sql` | Standalone verification script |
| `DATABASE_SCHEMA_FIX.md` | This documentation |

## How to Apply the Fix

### Option 1: Complete Fix (Recommended)
```bash
# Connect to your PostgreSQL database
psql -h hostname -U username -d database_name

# Run the complete fix script
\\i fix_database_schema.sql
```

### Option 2: Manual Steps
```bash
# 1. Fix Flyway checksums (if needed)
UPDATE flyway_schema_history SET checksum = -1848109661 WHERE version = '1.001' AND success = true;
UPDATE flyway_schema_history SET checksum = -677911012 WHERE version = '1.002' AND success = true; 
UPDATE flyway_schema_history SET checksum = 1658187676 WHERE version = '1.003' AND success = true;

# 2. Let the improved V1_004 migration handle UUID conversion on next app startup
```

### Option 3: Verification Only
```bash
# Check current database state
\\i verify_schema.sql
```

## Expected Checksums

| Migration | Expected Checksum | Purpose |
|-----------|-------------------|---------|
| V1.001 | -1848109661 | Create interview_sessions table (UUID version) |
| V1.002 | -677911012 | Create interview_responses table (UUID version) |
| V1.003 | 1658187676 | Create interview_events table (UUID version) |
| V1.004 | (varies) | Convert session_id columns to UUID type |

## Verification Steps

After applying the fix:

1. **Restart Spring Boot Application**
   ```bash
   # Stop and restart your Spring Boot service
   ```

2. **Check Application Logs**
   ```
   Look for: "Flyway validation successful" or similar messages
   Avoid: Any Flyway checksum mismatch errors
   ```

3. **Verify Database Schema**
   ```sql
   -- Run verification script
   \\i verify_schema.sql
   
   -- Or check manually
   SELECT table_name, data_type 
   FROM information_schema.columns 
   WHERE column_name = 'session_id' AND table_schema = 'public';
   ```

4. **Test Application Functionality**
   - Create a new interview session
   - Verify UUID generation works correctly
   - Check that related operations function properly

## Database Schema After Fix

### Tables with session_id columns:
- `interview_sessions` - Primary table (session_id as primary key, UUID type)
- `interview_responses` - Foreign key reference (session_id as UUID type)  
- `interview_events` - Foreign key reference (session_id as UUID type)
- Additional session-related tables (if they exist):
  - `session_ice_servers`
  - `session_question_pool` 
  - `session_asked_questions`
  - `session_ai_metrics`
  - `session_tech_stack`

### Key Relationships:
```
interview_sessions (session_id UUID PK)
├── interview_responses (session_id UUID FK)
├── interview_events (session_id UUID FK)
└── session_* tables (session_id UUID FK)
```

## Prevention for Future

To prevent similar issues:

1. **Consistent Data Types**: Ensure migration files and JPA entities use consistent data types from the start
2. **Migration Testing**: Test migrations thoroughly before committing
3. **Schema Validation**: Regularly verify that database schema matches entity expectations
4. **Version Control**: Keep migration files under strict version control and avoid modifying them after deployment

## Troubleshooting

### Common Issues:

**Problem**: Flyway still reports checksum mismatch
**Solution**: Verify the correct checksums were applied and restart the application

**Problem**: UUID conversion fails
**Solution**: Check that session_id values are valid UUID format strings

**Problem**: Foreign key constraint errors
**Solution**: Ensure all related tables are converted to UUID simultaneously

**Problem**: Application startup fails
**Solution**: Check PostgreSQL logs and verify uuid-ossp extension is enabled

### Support Commands:

```sql
-- Check Flyway history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Check column types  
SELECT table_name, column_name, data_type 
FROM information_schema.columns 
WHERE column_name = 'session_id';

-- Check for UUID extension
SELECT * FROM pg_extension WHERE extname = 'uuid-ossp';

-- Sample data type check
SELECT session_id, pg_typeof(session_id) FROM interview_sessions LIMIT 1;
```

## Contact

For questions or issues with this fix, please check:
1. Application logs for specific error messages
2. PostgreSQL logs for database-level errors  
3. Verify all steps were completed successfully
4. Ensure database user has appropriate permissions for schema modifications
