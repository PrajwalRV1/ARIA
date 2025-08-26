#!/bin/bash

# ARIA Services Redeployment Script
# Removes and redeploys user-management-service and interview-orchestrator-service

set -e
echo "🚀 Starting ARIA Services Redeployment Process"
echo "==============================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Backup current render.yaml
echo -e "${YELLOW}📦 Step 1: Backing up current render.yaml...${NC}"
cp render.yaml render.yaml.backup
echo "✅ Backup created: render.yaml.backup"

# Step 2: Remove only the problematic services from render.yaml (keeping others intact)
echo -e "${YELLOW}🗑️  Step 2: Removing problematic services from render.yaml...${NC}"
cp render-clean.yaml render.yaml
echo "✅ Updated render.yaml with only working services"

# Step 3: Commit configuration fixes
echo -e "${YELLOW}💾 Step 3: Committing configuration fixes...${NC}"
git add backend/user-management-service/src/main/resources/application-render.properties
git add backend/interview-orchestrator-service/src/main/resources/application-supabase.properties
git commit -m "FIX: Update Redis configuration for Render deployment

- Change from redis URL to host/port format to avoid localhost fallback
- Add SSL configuration for Upstash Redis
- Include prepared statement optimizations for Supabase pooler
- Ensure proper environment variable mapping"

echo "✅ Configuration fixes committed"

# Step 4: Wait for user confirmation
echo -e "${RED}⚠️  IMPORTANT: Before proceeding, manually remove the old services from Render dashboard:${NC}"
echo "   1. Go to https://dashboard.render.com"
echo "   2. Delete 'aria-user-management' service"
echo "   3. Delete 'aria-interview-orchestrator' service"
echo "   4. Keep all other services running!"
echo
read -p "Press Enter after you've removed the old services from Render dashboard..."

# Step 5: Deploy new configurations
echo -e "${YELLOW}🚀 Step 5: Deploying new service configurations...${NC}"
cp render-new.yaml render.yaml
git add render.yaml
git commit -m "DEPLOY: Add reconfigured services with v2 names

- aria-user-management-v2: Fixed Redis configuration
- aria-interview-orchestrator-v2: Fixed prepared statement issues
- Both services use proper environment variables
- autoDeploy disabled for controlled deployment"

git push origin main
echo "✅ New configurations pushed to repository"

echo -e "${GREEN}🎉 Redeployment process initiated!${NC}"
echo "==============================================="
echo "Next steps:"
echo "1. Monitor Render dashboard for new service deployments"
echo "2. Check health endpoints once services are running:"
echo "   - https://aria-user-management-v2.onrender.com/api/auth/actuator/health"
echo "   - https://aria-interview-orchestrator-v2.onrender.com/api/interview/actuator/health"
echo "3. Update frontend configuration to use new service URLs if needed"
echo
echo -e "${YELLOW}⏰ Expected deployment time: 5-10 minutes${NC}"
