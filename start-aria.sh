#!/bin/bash

# ARIA Platform Startup Script
# Advanced AI-driven interview platform with adaptive questioning and real-time analysis

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT=${1:-development}
ARIA_VERSION="1.0.0"
REQUIRED_SERVICES=("mysql" "redis" "user-management-service" "interview-orchestrator-service" "adaptive-engine" "speech-service" "frontend")

echo -e "${BLUE}🚀 Starting ARIA Platform v${ARIA_VERSION}${NC}"
echo -e "${BLUE}Environment: ${ENVIRONMENT}${NC}"
echo ""

# Function to print section headers
print_section() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
}

# Function to check prerequisites
check_prerequisites() {
    print_section "Checking Prerequisites"
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}❌ Docker is not installed${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Docker found: $(docker --version)${NC}"
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}❌ Docker Compose is not installed${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Docker Compose found: $(docker-compose --version)${NC}"
    
    # Check available ports
    echo -e "${YELLOW}📡 Checking required ports...${NC}"
    PORTS=(3306 6379 8080 8081 8001 8002 8003 4200 3478 80 443 9090 3000 3100)
    
    for port in "${PORTS[@]}"; do
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo -e "${YELLOW}⚠️  Port $port is already in use${NC}"
        else
            echo -e "${GREEN}✅ Port $port is available${NC}"
        fi
    done
    
    # Check system resources
    echo -e "${YELLOW}💻 System Resources:${NC}"
    echo "   RAM: $(free -h | awk '/^Mem:/ {print $2}') available"
    echo "   Disk: $(df -h . | awk 'NR==2 {print $4}') free space"
    
    echo ""
}

# Function to setup environment
setup_environment() {
    print_section "Environment Setup"
    
    # Create necessary directories
    echo -e "${YELLOW}📁 Creating directories...${NC}"
    mkdir -p infrastructure/{nginx,coturn,monitoring/{prometheus,grafana,loki}}
    mkdir -p speech-service/{models,credentials}
    mkdir -p ai-analytics/models
    mkdir -p adaptive-engine/models
    mkdir -p logs
    
    # Generate environment file if it doesn't exist
    if [ ! -f .env ]; then
        echo -e "${YELLOW}📝 Creating .env file...${NC}"
        cat > .env << EOF
# ARIA Platform Environment Configuration
ENVIRONMENT=${ENVIRONMENT}
ARIA_VERSION=${ARIA_VERSION}

# External IP for TURN server
EXTERNAL_IP=$(curl -s ifconfig.me || echo "127.0.0.1")

# Database Configuration
MYSQL_ROOT_PASSWORD=aria_root_password_$(openssl rand -base64 12)
MYSQL_DATABASE=aria_db
MYSQL_USER=aria_user
MYSQL_PASSWORD=aria_password_$(openssl rand -base64 12)

# Redis Configuration
REDIS_PASSWORD=aria_redis_password_$(openssl rand -base64 12)

# JWT Secrets
JWT_SECRET=$(openssl rand -base64 32)
JWT_REFRESH_SECRET=$(openssl rand -base64 32)

# Email Configuration (Update with your SMTP settings)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@gmail.com
SMTP_PASSWORD=your-app-password

# TURN Server Configuration
TURN_USERNAME=aria_turn_user
TURN_PASSWORD=aria_turn_password_$(openssl rand -base64 12)
TURN_REALM=aria.local

# Monitoring
GRAFANA_ADMIN_PASSWORD=aria_grafana_password_$(openssl rand -base64 12)

# AI Service Configuration
GOOGLE_CLOUD_PROJECT_ID=your-project-id
HUGGINGFACE_API_TOKEN=your-huggingface-token

# Development flags
DEBUG_MODE=true
ENABLE_SWAGGER=true
LOG_LEVEL=INFO
EOF
        echo -e "${GREEN}✅ Environment file created${NC}"
    else
        echo -e "${GREEN}✅ Environment file exists${NC}"
    fi
    
    # Set proper permissions
    chmod +x scripts/*.sh 2>/dev/null || true
    
    echo ""
}

# Function to build services
build_services() {
    print_section "Building Services"
    
    echo -e "${YELLOW}🔨 Building Docker images...${NC}"
    
    if [ "$ENVIRONMENT" == "development" ]; then
        docker-compose build --parallel
    else
        docker-compose -f docker-compose.yml -f docker-compose.prod.yml build --parallel
    fi
    
    echo -e "${GREEN}✅ All services built successfully${NC}"
    echo ""
}

# Function to start services
start_services() {
    print_section "Starting Services"
    
    echo -e "${YELLOW}🚀 Starting ARIA platform...${NC}"
    
    # Start infrastructure services first
    echo -e "${YELLOW}Starting infrastructure services...${NC}"
    docker-compose up -d mysql redis
    
    # Wait for database to be ready
    echo -e "${YELLOW}⏳ Waiting for database to be ready...${NC}"
    timeout=60
    while ! docker exec aria-mysql mysqladmin ping -h localhost --silent; do
        sleep 2
        timeout=$((timeout - 2))
        if [ $timeout -le 0 ]; then
            echo -e "${RED}❌ Database failed to start within timeout${NC}"
            exit 1
        fi
    done
    echo -e "${GREEN}✅ Database is ready${NC}"
    
    # Start AI services
    echo -e "${YELLOW}Starting AI services...${NC}"
    docker-compose up -d adaptive-engine speech-service ai-analytics
    
    # Wait for AI services
    sleep 10
    
    # Start application services
    echo -e "${YELLOW}Starting application services...${NC}"
    docker-compose up -d user-management-service interview-orchestrator-service
    
    # Start frontend
    echo -e "${YELLOW}Starting frontend...${NC}"
    docker-compose up -d frontend
    
    # Start supporting services
    echo -e "${YELLOW}Starting supporting services...${NC}"
    docker-compose up -d coturn nginx
    
    # Start monitoring (if not in minimal mode)
    if [ "$ENVIRONMENT" != "minimal" ]; then
        echo -e "${YELLOW}Starting monitoring services...${NC}"
        docker-compose up -d prometheus grafana loki
    fi
    
    echo -e "${GREEN}✅ All services started${NC}"
    echo ""
}

# Function to check service health
check_health() {
    print_section "Health Check"
    
    echo -e "${YELLOW}🏥 Checking service health...${NC}"
    
    services_health=()
    
    for service in "${REQUIRED_SERVICES[@]}"; do
        echo -n "Checking $service... "
        
        if docker-compose ps $service | grep -q "Up"; then
            # Check if service has health check
            if docker inspect aria-$service 2>/dev/null | grep -q "Health"; then
                health=$(docker inspect --format='{{.State.Health.Status}}' aria-$service 2>/dev/null || echo "unknown")
                if [ "$health" == "healthy" ]; then
                    echo -e "${GREEN}✅ Healthy${NC}"
                    services_health+=("$service:healthy")
                else
                    echo -e "${YELLOW}⚠️  $health${NC}"
                    services_health+=("$service:$health")
                fi
            else
                echo -e "${GREEN}✅ Running${NC}"
                services_health+=("$service:running")
            fi
        else
            echo -e "${RED}❌ Not running${NC}"
            services_health+=("$service:down")
        fi
    done
    
    # Check API endpoints
    echo ""
    echo -e "${YELLOW}🌐 Checking API endpoints...${NC}"
    
    endpoints=(
        "http://localhost:8080/actuator/health:User Management"
        "http://localhost:8081/actuator/health:Interview Orchestrator"
        "http://localhost:8001/health:Adaptive Engine"
        "http://localhost:8002/health:Speech Service"
        "http://localhost:4200:Frontend"
    )
    
    for endpoint_info in "${endpoints[@]}"; do
        IFS=':' read -ra ADDR <<< "$endpoint_info"
        url="${ADDR[0]}"
        name="${ADDR[1]}"
        
        echo -n "Checking $name... "
        if curl -f -s "$url" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ Responding${NC}"
        else
            echo -e "${RED}❌ Not responding${NC}"
        fi
    done
    
    echo ""
}

# Function to show service URLs
show_urls() {
    print_section "Service URLs"
    
    echo -e "${GREEN}🌐 ARIA Platform is now running!${NC}"
    echo ""
    echo -e "${BLUE}Main Application:${NC}"
    echo "  🏠 Frontend:              http://localhost:4200"
    echo "  📱 Mobile App:            http://localhost:4200/mobile"
    echo ""
    echo -e "${BLUE}API Services:${NC}"
    echo "  👥 User Management:       http://localhost:8080/api"
    echo "  🎯 Interview Orchestrator: http://localhost:8081/api"
    echo "  🤖 Adaptive Engine:       http://localhost:8001"
    echo "  🎤 Speech Service:        http://localhost:8002"
    echo "  📊 AI Analytics:          http://localhost:8003"
    echo ""
    echo -e "${BLUE}Development Tools:${NC}"
    echo "  🗃️  Database Admin:        http://localhost:8080 (adminer)"
    echo "  📊 Redis Commander:       http://localhost:8081"
    echo ""
    echo -e "${BLUE}Monitoring:${NC}"
    echo "  📈 Prometheus:            http://localhost:9090"
    echo "  📊 Grafana:               http://localhost:3000"
    echo "  📋 Logs (Loki):           http://localhost:3100"
    echo ""
    echo -e "${BLUE}Documentation:${NC}"
    echo "  📚 API Docs:              http://localhost:8080/swagger-ui"
    echo "  🎯 Interview API Docs:    http://localhost:8081/swagger-ui"
    echo "  🤖 Adaptive Engine Docs: http://localhost:8001/docs"
    echo "  🎤 Speech Service Docs:   http://localhost:8002/docs"
    echo ""
    
    if [ "$ENVIRONMENT" == "development" ]; then
        echo -e "${YELLOW}💡 Development Tips:${NC}"
        echo "  • Use 'docker-compose logs -f [service]' to view logs"
        echo "  • Use 'docker-compose restart [service]' to restart a service"
        echo "  • Use './stop-aria.sh' to stop all services"
        echo "  • Check './logs/' directory for application logs"
        echo ""
    fi
    
    echo -e "${GREEN}🎉 ARIA Platform is ready for AI-powered interviews!${NC}"
    echo ""
}

# Function to setup sample data
setup_sample_data() {
    if [ "$ENVIRONMENT" == "development" ]; then
        print_section "Setting up Sample Data"
        
        echo -e "${YELLOW}📊 Loading sample interview questions...${NC}"
        # Wait a bit more for services to fully initialize
        sleep 15
        
        # Load sample questions via API
        curl -X POST http://localhost:8001/admin/load-sample-questions \
             -H "Content-Type: application/json" \
             -d '{"category": "software_engineering", "count": 50}' \
             2>/dev/null && echo -e "${GREEN}✅ Sample questions loaded${NC}" || echo -e "${YELLOW}⚠️  Could not load sample questions (service may still be starting)${NC}"
        
        echo ""
    fi
}

# Function to show logs
show_logs() {
    print_section "Service Logs"
    
    echo -e "${YELLOW}📋 Recent logs from all services:${NC}"
    docker-compose logs --tail=10
}

# Main execution
main() {
    echo -e "${BLUE}🎯 ARIA - Advanced Recruitment Intelligence Assistant${NC}"
    echo -e "${BLUE}   Production-ready AI interview platform${NC}"
    echo ""
    
    check_prerequisites
    setup_environment
    build_services
    start_services
    
    # Give services time to fully start
    echo -e "${YELLOW}⏳ Waiting for services to fully initialize...${NC}"
    sleep 30
    
    check_health
    setup_sample_data
    show_urls
    
    # Optionally show logs
    if [ "$2" == "--logs" ]; then
        show_logs
    fi
    
    echo -e "${GREEN}🚀 ARIA Platform startup complete!${NC}"
    echo -e "${BLUE}Run './stop-aria.sh' to stop all services${NC}"
}

# Handle script arguments
case "${1:-}" in
    "production"|"prod")
        ENVIRONMENT="production"
        main
        ;;
    "development"|"dev"|"")
        ENVIRONMENT="development"
        main
        ;;
    "minimal")
        ENVIRONMENT="minimal"
        main
        ;;
    "stop")
        echo -e "${YELLOW}🛑 Stopping ARIA Platform...${NC}"
        docker-compose down
        echo -e "${GREEN}✅ ARIA Platform stopped${NC}"
        ;;
    "restart")
        echo -e "${YELLOW}🔄 Restarting ARIA Platform...${NC}"
        docker-compose restart
        check_health
        show_urls
        ;;
    "logs")
        docker-compose logs -f
        ;;
    "status")
        check_health
        ;;
    "help"|"-h"|"--help")
        echo "ARIA Platform Control Script"
        echo ""
        echo "Usage: $0 [ENVIRONMENT] [OPTIONS]"
        echo ""
        echo "Environments:"
        echo "  development  Start in development mode (default)"
        echo "  production   Start in production mode"
        echo "  minimal      Start minimal services only"
        echo ""
        echo "Commands:"
        echo "  stop         Stop all services"
        echo "  restart      Restart all services"
        echo "  logs         Show live logs"
        echo "  status       Check service health"
        echo "  help         Show this help"
        echo ""
        echo "Options:"
        echo "  --logs       Show logs after startup"
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        echo "Use '$0 help' for usage information"
        exit 1
        ;;
esac
