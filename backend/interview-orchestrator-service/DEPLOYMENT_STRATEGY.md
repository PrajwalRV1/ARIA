# Complete Interview Orchestrator Service Deployment Strategy

## Current Issues Analysis

### Primary Issues:
1. **Profile Mismatch**: App using `supabase` profile instead of `production`
2. **Schema Validation**: Hibernate strict validation causing type mismatch errors
3. **Configuration Inconsistency**: Different configurations between profiles
4. **JPA/Hibernate Issues**: Column type mapping problems with PostgreSQL

### Symptoms Observed:
- ✅ Build successful, JAR compiled
- ✅ Database connection established
- ✅ Flyway migrations completed (V1.004)
- ❌ Hibernate EntityManagerFactory creation fails
- ❌ Schema validation errors for column types

## Comprehensive Fix Strategy

### Phase 1: Fix Profile Configuration
- **Issue**: App defaults to `supabase` profile, needs `production` profile for Render
- **Solution**: Update supabase profile to be deployment-ready OR ensure production profile is used

### Phase 2: Resolve Schema Validation Issues
- **Issue**: Hibernate expects specific column types but PostgreSQL has different types
- **Solution**: Align entity column definitions with actual PostgreSQL schema

### Phase 3: Ensure Service Isolation
- **Issue**: Changes must not affect other deployed services
- **Solution**: Only modify this service's configuration, keep database schema consistent

### Phase 4: Progressive Deployment
- **Issue**: Need safe, testable deployment approach
- **Solution**: Deploy with validation disabled first, then gradually enable features

## Implementation Plan

### Immediate Actions:
1. Fix supabase profile to be production-ready (since it's being used)
2. Disable schema validation temporarily to ensure startup
3. Align column type definitions with PostgreSQL reality
4. Ensure proper port binding and health checks

### Progressive Rollout:
1. Deploy with minimal validation
2. Verify basic functionality
3. Gradually enable validation and features
4. Monitor for issues affecting other services

## Risk Mitigation

### Database Impact:
- No schema changes required (Flyway already completed)
- Only configuration changes to match existing schema
- No impact on other services using same database

### Service Dependencies:
- Interview Orchestrator is a standalone service
- Changes isolated to this service configuration only
- Other services (user-management, etc.) remain unaffected

### Rollback Plan:
- Configuration-only changes (easily reversible)
- Database schema unchanged (safe state)
- Can revert to previous working configuration quickly
