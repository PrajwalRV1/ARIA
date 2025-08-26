# ARIA Interview Orchestrator - Deployment Plan for Render

## Pre-Deployment Checklist

### 1. Code Preparation
- [x] Restored historical migration files (V1_001, V1_002, V1_003) to original VARCHAR(36) versions
- [x] Created new V1_004 migration for UUID conversion with data validation
- [x] Created Flyway repair strategy documentation
- [ ] Commit and push all changes to repository

### 2. Database Backup (Critical)
- [ ] **MANDATORY**: Take full database backup before deployment
- [ ] Verify backup integrity and restoration procedure
- [ ] Document backup location and restoration commands

### 3. Environment Variables Check
Ensure Render environment has:
```
DATABASE_URL=postgresql://[credentials]
SPRING_PROFILES_ACTIVE=production
SPRING_FLYWAY_ENABLED=true
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

## Deployment Sequence

### Phase 1: Pre-Deployment Validation (5 min)

1. **Check Current Database State**
   ```bash
   # Connect to production database
   psql $DATABASE_URL -c "
   SELECT version, checksum, success 
   FROM flyway_schema_history 
   WHERE version IN ('1.001', '1.002', '1.003') 
   ORDER BY version;"
   ```

2. **Verify Schema State**
   ```bash
   psql $DATABASE_URL -c "
   SELECT table_name, data_type 
   FROM information_schema.columns 
   WHERE column_name = 'session_id' AND table_schema = 'public';"
   ```

### Phase 2: Deployment (10-15 min)

1. **Deploy to Render**
   - Trigger deployment from GitHub repository
   - Monitor deployment logs in real-time

2. **Expected Log Messages**
   ```
   ✅ SUCCESS INDICATORS:
   - "Flyway: Validating schema history"
   - "Current session_id type: character varying. Starting UUID conversion..."
   - "UUID validation passed. Proceeding with conversion..."
   - "Successfully converted all session_id columns from VARCHAR(36) to UUID"
   - "SUCCESS: All session_id columns are now UUID type"
   - "Started InterviewOrchestratorServiceApplication"

   ❌ FAILURE INDICATORS:
   - "Flyway validation failed"
   - "Found [N] invalid UUID format(s)"
   - "FAILURE: Some session_id columns are not UUID type"
   - Application startup failure
   ```

### Phase 3: Post-Deployment Validation (10 min)

1. **Verify Flyway Migration Success**
   ```bash
   psql $DATABASE_URL -c "
   SELECT version, description, success, installed_on 
   FROM flyway_schema_history 
   ORDER BY installed_rank DESC LIMIT 5;"
   ```

2. **Verify UUID Conversion**
   ```bash
   psql $DATABASE_URL -c "
   SELECT 
       t.table_name,
       c.data_type,
       CASE WHEN c.data_type = 'uuid' THEN 'CORRECT' ELSE 'ISSUE' END as status
   FROM information_schema.tables t
   JOIN information_schema.columns c ON t.table_name = c.table_name
   WHERE t.table_schema = 'public'
     AND c.column_name = 'session_id'
     AND t.table_type = 'BASE TABLE'
   ORDER BY t.table_name;"
   ```

3. **Test Application Health**
   ```bash
   curl https://[your-render-url]/actuator/health
   ```

4. **Test Core Functionality**
   - Create a test interview session
   - Verify UUID generation works
   - Check that data persists correctly

## Rollback Strategy

### Immediate Rollback (If deployment fails)

1. **Stop Current Deployment**
   - Cancel deployment in Render dashboard
   - Revert to previous working deployment

2. **Database State Check**
   ```bash
   # Check if V1_004 was partially applied
   psql $DATABASE_URL -c "
   SELECT * FROM flyway_schema_history WHERE version = '1.004';"
   ```

3. **Manual Rollback (If needed)**
   ```sql
   -- Only if V1_004 partially succeeded and needs reversal
   -- Convert UUID back to VARCHAR(36) - DATA LOSS POSSIBLE
   ALTER TABLE interview_sessions ALTER COLUMN session_id TYPE VARCHAR(36);
   ALTER TABLE interview_responses ALTER COLUMN session_id TYPE VARCHAR(36);
   ALTER TABLE interview_events ALTER COLUMN session_id TYPE VARCHAR(36);
   -- [Repeat for all session-related tables]
   
   -- Remove V1_004 from migration history
   DELETE FROM flyway_schema_history WHERE version = '1.004';
   ```

### Alternative Fix (If rollback needed)

1. **Use Emergency Fix Script**
   ```bash
   psql $DATABASE_URL < fix_database_schema.sql
   ```

2. **Redeploy without Migration Changes**
   - Deploy using previous working migration files
   - Skip Flyway validation temporarily if needed

## Monitoring Plan

### Real-time Monitoring (First 30 min)
- **Application Logs**: Monitor for UUID-related errors
- **Database Connections**: Verify connection pool health
- **API Endpoints**: Test critical endpoints
- **Performance Metrics**: Check response times

### Extended Monitoring (24 hours)
- **Database Performance**: Query execution times
- **Memory Usage**: JVM heap and database connections
- **Error Rates**: Any UUID conversion issues
- **User Functionality**: End-to-end interview flow

## Troubleshooting Guide

### Common Issues and Solutions

**Issue**: Flyway checksum mismatch after restoration
**Solution**: Use flyway repair as documented in FLYWAY_REPAIR_STRATEGY.md

**Issue**: UUID conversion fails due to invalid data
**Solution**: 
1. Check invalid UUID formats in production data
2. Clean or fix invalid UUIDs manually
3. Re-run V1_004 migration

**Issue**: Hibernate validation fails
**Solution**:
1. Verify all session_id columns are UUID type
2. Check JPA entity annotations match database schema
3. Restart application to reload Hibernate metadata

**Issue**: Application won't start after migration
**Solution**:
1. Check application logs for specific errors
2. Verify database connection and credentials
3. Use rollback strategy if critical

## Success Criteria

✅ **Deployment Successful When**:
- All Flyway migrations show `success=true`
- All session_id columns are UUID type
- Application starts without errors
- Health check endpoint responds
- Test interview session can be created
- No UUID-related errors in logs

## Emergency Contacts

- **Database Issues**: [DBA Contact]
- **Deployment Issues**: [DevOps Contact]  
- **Application Issues**: [Development Team]
- **Incident Management**: [On-call Engineer]

## Post-Deployment Tasks

1. **Update Documentation**: Mark migration as completed
2. **Clean Up**: Remove temporary fix scripts if successful
3. **Performance Baseline**: Establish new performance metrics
4. **Team Communication**: Notify stakeholders of successful deployment
5. **Monitor**: Continue monitoring for 24-48 hours
