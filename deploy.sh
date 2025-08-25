#!/bin/bash

# ARIA Quick Deployment Script
# This script helps automate the deployment process

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if required tools are installed
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check if npm is installed
    if ! command -v npm &> /dev/null; then
        print_error "npm is not installed. Please install Node.js first."
        exit 1
    fi
    
    # Check if git is installed
    if ! command -v git &> /dev/null; then
        print_error "git is not installed. Please install Git first."
        exit 1
    fi
    
    print_success "Prerequisites check passed!"
}

# Install Railway CLI if not present
install_railway_cli() {
    if ! command -v railway &> /dev/null; then
        print_status "Installing Railway CLI..."
        npm install -g @railway/cli
        print_success "Railway CLI installed!"
    else
        print_success "Railway CLI already installed!"
    fi
}

# Setup environment variables
setup_environment() {
    print_status "Setting up environment variables..."
    
    if [ ! -f .env ]; then
        print_status "Creating .env file from template..."
        cp .env.example .env
        print_warning "Please edit .env file with your actual values before proceeding!"
        print_status "Opening .env file for editing..."
        
        # Try to open with common editors
        if command -v code &> /dev/null; then
            code .env
        elif command -v nano &> /dev/null; then
            nano .env
        elif command -v vim &> /dev/null; then
            vim .env
        else
            print_warning "Please manually edit the .env file with your configuration values."
        fi
        
        read -p "Press Enter after you've configured your .env file..."
    else
        print_success ".env file already exists!"
    fi
}

# Build frontend
build_frontend() {
    print_status "Building Angular frontend..."
    cd frontend
    
    if [ ! -d "node_modules" ]; then
        print_status "Installing frontend dependencies..."
        npm ci
    fi
    
    print_status "Building frontend for production..."
    npm run build
    
    cd ..
    print_success "Frontend build completed!"
}

# Test Python services
test_python_services() {
    print_status "Testing Python services..."
    
    for service in speech-service adaptive-engine ai-services/analytics-service; do
        if [ -d "$service" ]; then
            print_status "Testing $service..."
            cd "$service"
            
            if [ -f requirements.txt ]; then
                # Create virtual environment for testing
                python3 -m venv test_env
                source test_env/bin/activate
                pip install -r requirements.txt
                
                # Basic syntax check
                python -m py_compile *.py
                
                deactivate
                rm -rf test_env
            fi
            
            cd - > /dev/null
            print_success "$service tested successfully!"
        fi
    done
}

# Deploy to Railway
deploy_railway() {
    print_status "Deploying AI/ML services to Railway..."
    
    # Check if user is logged in to Railway
    if ! railway whoami &> /dev/null; then
        print_status "Please log in to Railway..."
        railway login
    fi
    
    # Deploy each service
    services=(
        "ai-services/ai-avatar-service"
        "ai-services/mozilla-tts-service"
        "ai-services/voice-isolation-service"
        "ai-services/voice-synthesis-service"
    )
    
    for service in "${services[@]}"; do
        if [ -d "$service" ]; then
            print_status "Deploying $service to Railway..."
            cd "$service"
            
            # Initialize Railway project if not exists
            if [ ! -f railway.toml ]; then
                railway init --name "$(basename "$service")"
            fi
            
            # Deploy service
            railway up --detach
            
            cd - > /dev/null
            print_success "$service deployed to Railway!"
        else
            print_warning "Service directory $service not found!"
        fi
    done
}

# Commit and push to GitHub (for Render auto-deployment)
push_to_github() {
    print_status "Pushing to GitHub for Render auto-deployment..."
    
    # Check if we're in a git repository
    if [ ! -d .git ]; then
        print_status "Initializing Git repository..."
        git init
        git branch -M main
    fi
    
    # Add all files
    git add .
    git commit -m "Deploy ARIA with multi-platform configuration" || print_warning "No changes to commit"
    
    # Check if origin is set
    if ! git remote get-url origin &> /dev/null; then
        print_warning "No GitHub origin set. Please add your repository:"
        print_status "git remote add origin https://github.com/yourusername/ARIA.git"
        print_status "Then run: git push -u origin main"
    else
        print_status "Pushing to GitHub..."
        git push -u origin main
        print_success "Code pushed to GitHub! Render will auto-deploy."
    fi
}

# Display deployment status
show_deployment_status() {
    print_success "🎉 ARIA Deployment Configuration Complete!"
    print_status ""
    print_status "Next Steps:"
    print_status "1. Set up accounts on required platforms (see DEPLOYMENT.md)"
    print_status "2. Configure databases (Supabase, MongoDB Atlas, Upstash)"
    print_status "3. Connect GitHub to Render for auto-deployment"
    print_status "4. Set up monitoring with UptimeRobot"
    print_status "5. Configure Cloudflare for DNS and CDN"
    print_status ""
    print_status "Service URLs after deployment:"
    print_status "- Frontend: https://aria-frontend.onrender.com"
    print_status "- API Services: https://aria-*-service.onrender.com"
    print_status "- AI Services: https://your-*-service.railway.app"
    print_status ""
    print_status "📖 Full deployment guide: DEPLOYMENT.md"
    print_status "🔧 Configuration template: .env.example"
}

# Main deployment function
main() {
    print_status "🚀 Starting ARIA Deployment Setup..."
    print_status ""
    
    # Run deployment steps
    check_prerequisites
    install_railway_cli
    setup_environment
    build_frontend
    test_python_services
    
    # Ask user if they want to deploy now
    read -p "Do you want to deploy to Railway now? (y/N): " deploy_now
    if [[ $deploy_now =~ ^[Yy]$ ]]; then
        deploy_railway
    else
        print_status "Skipping Railway deployment. You can run it later with: ./deploy.sh railway"
    fi
    
    # Ask about GitHub push
    read -p "Do you want to push to GitHub for Render deployment? (y/N): " push_now
    if [[ $push_now =~ ^[Yy]$ ]]; then
        push_to_github
    else
        print_status "Skipping GitHub push. Remember to push your code for Render deployment."
    fi
    
    show_deployment_status
}

# Handle command line arguments
case "${1:-}" in
    "railway")
        deploy_railway
        ;;
    "frontend")
        build_frontend
        ;;
    "test")
        test_python_services
        ;;
    "push")
        push_to_github
        ;;
    *)
        main
        ;;
esac
