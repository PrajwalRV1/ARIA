#!/bin/bash

# Render CLI Commands for Service Management
# Note: These commands require Render CLI to be installed and authenticated

echo "🔧 Render CLI Service Management Commands"
echo "========================================"

# List all services
echo "📋 List all services:"
echo "render services list"

# Delete old services (run these manually after confirming)
echo ""
echo "🗑️  Delete old problematic services:"
echo "render service delete aria-user-management"
echo "render service delete aria-interview-orchestrator"

# Deploy from blueprint
echo ""
echo "🚀 Deploy new services from blueprint:"
echo "render deploy --file render-new.yaml"

# Monitor service logs
echo ""
echo "📊 Monitor service logs:"
echo "render service logs aria-user-management-v2 --follow"
echo "render service logs aria-interview-orchestrator-v2 --follow"

# Check service status
echo ""
echo "✅ Check service status:"
echo "render service get aria-user-management-v2"
echo "render service get aria-interview-orchestrator-v2"

echo ""
echo "💡 Install Render CLI if not already installed:"
echo "npm install -g @render-com/cli"
echo "render login"
