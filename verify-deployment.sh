#!/bin/bash

# Deployment Verification Script for ARIA Interview Orchestrator
# This script verifies the fixes applied for database connectivity issues

echo "🔍 ARIA Interview Orchestrator - Deployment Verification"
echo "========================================================"
echo

# 1. Verify render.yaml configuration
echo "✅ Checking render.yaml configuration..."
if grep -q "aws-0-us-east-1.pooler.supabase.com" render.yaml; then
    echo "   ✓ Correct Supabase hostname found"
else
    echo "   ❌ Incorrect hostname in render.yaml"
fi

if grep -q "sslmode=require" render.yaml; then
    echo "   ✓ SSL mode correctly configured"
else
    echo "   ❌ Missing sslmode=require parameter"
fi

# 2. Verify application-supabase.properties
echo
echo "✅ Checking application-supabase.properties..."
PROPS_FILE="backend/interview-orchestrator-service/src/main/resources/application-supabase.properties"

if grep -q "server.address=0.0.0.0" "$PROPS_FILE"; then
    echo "   ✓ Server address correctly set to 0.0.0.0"
else
    echo "   ❌ Server address not set correctly"
fi

if grep -q "server.ssl.enabled=false" "$PROPS_FILE"; then
    echo "   ✓ Server SSL correctly disabled"
else
    echo "   ❌ Server SSL not disabled"
fi

if grep -q "spring.datasource.hikari.data-source-properties.ssl=true" "$PROPS_FILE"; then
    echo "   ✓ Database SSL correctly enabled"
else
    echo "   ❌ Database SSL not configured"
fi

# 3. Verify Flyway migration files exist
echo
echo "✅ Checking Flyway migration files..."
MIGRATION_DIR="backend/interview-orchestrator-service/src/main/resources/db/migration"
if [ -d "$MIGRATION_DIR" ]; then
    SQL_COUNT=$(find "$MIGRATION_DIR" -name "*.sql" | wc -l)
    echo "   ✓ Found $SQL_COUNT migration files"
    find "$MIGRATION_DIR" -name "*.sql" -exec basename {} \; | sed 's/^/   - /'
else
    echo "   ❌ Migration directory not found"
fi

# 4. Verify Dockerfile
echo
echo "✅ Checking Dockerfile..."
DOCKERFILE="backend/interview-orchestrator-service/Dockerfile"
if grep -q "EXPOSE 8081" "$DOCKERFILE"; then
    echo "   ✓ Port 8081 exposed correctly"
else
    echo "   ❌ Port not exposed correctly"
fi

if grep -q "actuator/health" "$DOCKERFILE"; then
    echo "   ✓ Health check configured"
else
    echo "   ❌ Health check not configured"
fi

# 5. Test database hostname resolution
echo
echo "✅ Testing database connectivity..."
echo "   Testing hostname resolution for aws-0-us-east-1.pooler.supabase.com..."
if nslookup aws-0-us-east-1.pooler.supabase.com > /dev/null 2>&1; then
    echo "   ✓ Hostname resolves correctly"
else
    echo "   ❌ Hostname does not resolve"
fi

# 6. Check git status
echo
echo "✅ Git status..."
if git status --porcelain | grep -q .; then
    echo "   ⚠️  Uncommitted changes detected:"
    git status --porcelain
else
    echo "   ✓ Working tree clean - all fixes committed"
fi

echo
echo "========================================================"
echo "📋 DEPLOYMENT CHECKLIST:"
echo "1. Push changes to trigger Render deployment"
echo "2. Monitor Render deployment logs for successful startup"
echo "3. Check health endpoint: /api/interview/actuator/health"
echo "4. Verify Flyway migrations complete successfully"
echo "5. Test interview orchestrator API endpoints"
echo
echo "🚀 If all checks above pass, your deployment should succeed!"
