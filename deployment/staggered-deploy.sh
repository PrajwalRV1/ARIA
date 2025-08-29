#!/bin/bash

# =========================================
# ARIA SERVICES STAGGERED DEPLOYMENT SCRIPT
# =========================================
# This script deploys services one by one with delays to avoid
# simultaneous database connection spikes during startup

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DEPLOY_DELAY=30  # seconds between deployments
CHECK_DELAY=10   # seconds between health checks
MAX_RETRIES=12   # maximum health check retries (2 minutes)

# Service URLs for health checks
get_service_url() {
    case "$1" in
        "user-management") echo "https://aria-user-management.onrender.com/actuator/health" ;;
        "interview-orchestrator") echo "https://aria-interview-orchestrator.onrender.com/actuator/health" ;;
        "analytics") echo "https://aria-analytics-service.onrender.com/health" ;;
        "speech") echo "https://aria-speech-service.onrender.com/health" ;;
        *) echo "" ;;
    esac
}

# Service deployment order (most critical first)
SERVICE_ORDER=(
    "user-management"
    "interview-orchestrator"
    "analytics"
    "speech"
)

log() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')] $1${NC}"
}

success() {
    echo -e "${GREEN}[SUCCESS] $1${NC}"
}

warning() {
    echo -e "${YELLOW}[WARNING] $1${NC}"
}

error() {
    echo -e "${RED}[ERROR] $1${NC}"
}

check_service_health() {
    local service_name=$1
    local health_url=$(get_service_url "$service_name")
    local retries=0
    
    if [ -z "$health_url" ]; then
        error "Unknown service: $service_name"
        return 1
    fi
    
    log "Checking health for $service_name at $health_url..."
    
    while [ $retries -lt $MAX_RETRIES ]; do
        if curl -f -s --connect-timeout 10 --max-time 30 "$health_url" > /dev/null 2>&1; then
            success "$service_name is healthy!"
            return 0
        fi
        
        retries=$((retries + 1))
        warning "$service_name not ready yet (attempt $retries/$MAX_RETRIES). Waiting ${CHECK_DELAY}s..."
        sleep $CHECK_DELAY
    done
    
    error "$service_name failed to become healthy after $MAX_RETRIES attempts"
    return 1
}

deploy_service() {
    local service_name=$1
    
    log "Starting deployment for $service_name..."
    
    case $service_name in
        "user-management")
            log "Deploying User Management Service..."
            # Add your deployment command here
            # For Render, you might use: render deploy --service=aria-user-management
            ;;
        "interview-orchestrator")
            log "Deploying Interview Orchestrator Service..."
            # Add your deployment command here
            # For Render, you might use: render deploy --service=aria-interview-orchestrator
            ;;
        "analytics")
            log "Deploying Analytics Service..."
            # Add your deployment command here
            ;;
        "speech")
            log "Deploying Speech Service..."
            # Add your deployment command here
            ;;
    esac
    
    success "Deployment initiated for $service_name"
}

check_database_connections() {
    log "Checking current database connection usage..."
    
    # This would require Supabase CLI or API access
    # For now, we'll just log a reminder
    warning "Manual check required: Visit Supabase dashboard to monitor active connections"
    warning "Dashboard: https://supabase.com/dashboard/project/deqfzxsmuydhrepyiagq"
}

main() {
    log "Starting ARIA staggered deployment..."
    log "Services will be deployed in order: ${SERVICE_ORDER[*]}"
    log "Delay between deployments: ${DEPLOY_DELAY} seconds"
    
    check_database_connections
    
    for service in "${SERVICE_ORDER[@]}"; do
        log "=== Deploying $service ==="
        
        # Check if service is currently healthy (optional)
        if check_service_health "$service"; then
            warning "$service is already healthy. Proceeding with deployment..."
        fi
        
        # Deploy the service
        deploy_service "$service"
        
        # Wait for the service to become healthy
        log "Waiting for $service to become healthy..."
        if check_service_health "$service"; then
            success "$service deployment completed successfully!"
        else
            error "$service deployment may have issues. Check logs manually."
            read -p "Continue with next service? (y/n): " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                error "Deployment aborted by user"
                exit 1
            fi
        fi
        
        # Wait before deploying next service
        if [[ "$service" != "${SERVICE_ORDER[-1]}" ]]; then
            log "Waiting ${DEPLOY_DELAY} seconds before next deployment..."
            sleep $DEPLOY_DELAY
        fi
    done
    
    success "All services deployed successfully!"
    log "Final health check for all services..."
    
    for service in "${SERVICE_ORDER[@]}"; do
        check_service_health "$service" || warning "$service may need attention"
    done
    
    log "Deployment complete. Monitor services for stability."
}

# Show usage if --help is passed
if [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
    echo "ARIA Staggered Deployment Script"
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  --help, -h    Show this help message"
    echo "  --check-only  Only check service health, don't deploy"
    echo ""
    echo "This script deploys ARIA services one by one with delays"
    echo "to prevent database connection limit issues."
    exit 0
fi

# Check-only mode
if [[ "$1" == "--check-only" ]]; then
    log "Running health checks only..."
    for service in "${SERVICE_ORDER[@]}"; do
        check_service_health "$service" || warning "$service needs attention"
    done
    exit 0
fi

# Run main deployment
main
