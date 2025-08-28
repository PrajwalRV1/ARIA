# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

ARIA is a comprehensive AI-driven interview platform featuring adaptive questioning, real-time speech processing, and bias-free assessment capabilities. The platform uses a sophisticated microservices architecture with Angular 19 frontend and multiple backend services.

## Architecture Overview

### High-Level Structure
```
ARIA Platform
├── Frontend (Angular 19 + SSR) - Port 4200
├── Backend Services (Spring Boot 3.2.3)
│   ├── User Management Service - Port 8080
│   └── Interview Orchestrator Service - Port 8081
├── AI/ML Services (Python FastAPI)
│   ├── Adaptive Engine - Port 8001
│   ├── Speech Service - Port 8002
│   ├── Analytics Service - Port 8003
│   └── AI Avatar Service - Port 8006
└── Data Layer
    ├── Supabase PostgreSQL (Primary)
    ├── MongoDB Atlas (Documents)
    └── Upstash Redis (Cache/Sessions)
```

### Key Technologies
- **Frontend**: Angular 19, TypeScript, Monaco Editor, WebRTC
- **Backend**: Spring Boot 3.2.3 (Java 17), FastAPI (Python 3.11+)
- **Databases**: PostgreSQL (Supabase), MongoDB Atlas, Redis (Upstash)
- **AI/ML**: Item Response Theory (IRT), OpenCV, HuggingFace, Vosk, DeepSpeech
- **Infrastructure**: Docker, Multi-cloud (Render + Railway)

## Development Commands

### Local Development Setup
```bash
# Quick start all services
./deploy.sh

# Build frontend only
cd frontend && npm ci && npm run build

# Start frontend in development mode
cd frontend && npm run start:dev

# Test Python services
./deploy.sh test

# Build and test specific service
cd adaptive-engine && pip install -r requirements.txt && python -m py_compile *.py
```

### Spring Boot Services
```bash
# User Management Service
cd backend/user-management-service
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=supabase

# Interview Orchestrator Service  
cd backend/interview-orchestrator-service
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=supabase
```

### Python AI Services
```bash
# Start all AI services
for service in adaptive-engine speech-service ai-services/*/; do
  cd $service && python -m uvicorn main:app --host 0.0.0.0 --port 800X &
done

# Individual service examples
cd adaptive-engine && uvicorn main:app --host 0.0.0.0 --port 8001
cd speech-service && uvicorn main_simple:app --host 0.0.0.0 --port 8002
cd ai-services/analytics-service && uvicorn main:app --host 0.0.0.0 --port 8003
```

### Testing
```bash
# Frontend tests
cd frontend && npm test

# Backend tests
cd backend/user-management-service && mvn test
cd backend/interview-orchestrator-service && mvn test

# Python service syntax validation
python -m py_compile adaptive-engine/*.py
python -m py_compile speech-service/*.py
```

### Database Operations
```bash
# Flyway migrations (auto-run on startup)
mvn flyway:migrate -Dflyway.configFiles=src/main/resources/flyway.conf

# Check database connectivity
curl -f "jdbc:postgresql://aws-1-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require"
```

## Deployment

### Multi-Platform Architecture
- **Frontend + Backend Services**: Render (Free Tier)
- **AI/ML Services**: Railway (Free Tier)
- **Databases**: Supabase + MongoDB Atlas + Upstash (All Free)
- **Total Monthly Cost**: $0

### Deployment Commands
```bash
# Full deployment
./deploy.sh

# Deploy specific platforms
./deploy.sh railway  # AI services to Railway
./deploy.sh push     # Push to GitHub for Render auto-deploy

# Verify deployment
./verify-deployment.sh

# Redeploy services (if needed)
./redeploy-services.sh
```

### Health Check Endpoints
```bash
# Spring Boot services
curl https://aria-user-management-v2.onrender.com/api/auth/actuator/health
curl https://aria-interview-orchestrator-v2.onrender.com/api/interview/actuator/health

# Python services
curl https://aria-speech-service.onrender.com/health
curl https://aria-adaptive-engine.onrender.com/health
curl https://aria-analytics-service.onrender.com/health
```

## Core Architecture Concepts

### Microservices Communication
The system uses multiple communication patterns:
- **REST APIs**: Inter-service synchronous communication
- **WebSockets**: Real-time updates (speech, analytics, orchestration)
- **Redis Pub/Sub**: Event-driven coordination
- **WebRTC**: Video/audio streaming via Daily.co integration

### Database Strategy
- **PostgreSQL (Supabase)**: Core application data, user management, session data
- **MongoDB Atlas**: Documents (transcripts, code submissions, media files)
- **Redis (Upstash)**: Session management, caching, real-time data

### AI/ML Pipeline
1. **Adaptive Engine**: Item Response Theory for dynamic question selection
2. **Speech Processing**: Dual-engine (Vosk + DeepSpeech) transcription
3. **Analytics Service**: Real-time scoring, bias detection, behavioral analysis
4. **AI Avatar**: Conversational AI with Alex AI integration

### Security Architecture
- **JWT Authentication**: Access/refresh token pattern
- **Role-based Access**: RECRUITER, CANDIDATE, ADMIN roles
- **SSL Everywhere**: All services use HTTPS
- **Token Validation**: Multiple validation endpoints for different access patterns

## Key Implementation Details

### Interview Flow Architecture
The platform implements a 6-stage interview process:
1. **Introduction & Setup** (2-3 min)
2. **Technical Theory** (15-20 min) 
3. **Coding Challenges** (20-25 min)
4. **Cultural/Behavioral** (10-15 min)
5. **Candidate Q&A** (5-10 min)
6. **Conclusion** (2-3 min)

### Real-time Features
- **Live Transcription**: WebSocket streaming with <500ms latency
- **Adaptive Questioning**: <200ms question selection using IRT
- **Bias Detection**: Real-time fairness monitoring
- **Code Analysis**: Monaco Editor integration with live feedback

### Performance Targets
- **Response Time**: <2000ms for complete processing cycle
- **Speech Recognition**: 92%+ accuracy, 210ms average latency
- **Concurrent Sessions**: 1,000+ simultaneous interviews
- **Uptime**: 99.9% availability target

## Development Guidelines

### Code Organization
- **Frontend**: Component-based architecture with shared services
- **Backend**: Domain-driven design with clear service boundaries  
- **AI Services**: Modular design with pluggable engines
- **Database**: Migration-based schema management

### Configuration Management
- **Environment Files**: `.env.example` template with all required variables
- **Spring Profiles**: `supabase`, `render`, `development` profiles
- **Service Discovery**: URL-based configuration for microservices

### Error Handling
- **Resilience Patterns**: Circuit breakers, retries with backoff
- **Graceful Degradation**: Fallback mechanisms (e.g., Vosk → DeepSpeech)
- **Health Monitoring**: Comprehensive health checks and metrics

### Testing Strategy
- **Frontend**: Angular testing with Karma/Jasmine
- **Backend**: Spring Boot test framework with test profiles
- **AI Services**: Syntax validation and basic integration tests
- **E2E**: Manual testing workflow documented in deployment verification

## Common Development Tasks

### Adding New Questions
1. Update database via Flyway migrations in `database/migrations/`
2. Modify IRT parameters in `adaptive-engine/question_selector.py`
3. Test question flow via adaptive engine API endpoints

### Extending AI Capabilities
1. Add new analysis modules to `ai-services/analytics-service/`
2. Update WebSocket message handlers for real-time features
3. Integrate with existing bias detection and scoring systems

### Database Schema Changes
1. Create Flyway migration in `backend/*/src/main/resources/db/migration/`
2. Update JPA entities in corresponding service
3. Test with both local and Supabase environments

### Service Integration
1. Update service URLs in environment configurations
2. Add health check endpoints for new services
3. Configure CORS origins in application properties
4. Update render.yaml or railway.json for deployment

## Critical Files

### Core Configuration
- `.env.example` - Environment template with all required variables
- `render.yaml` - Multi-service deployment configuration
- `backend/*/src/main/resources/application-*.properties` - Service configurations

### Key Implementation Files
- `adaptive-engine/main.py` - IRT-based adaptive questioning
- `ai-services/ai-avatar-service/main.py` - Alex AI integration
- `frontend/src/app/services/` - Angular service layer
- `backend/*/src/main/java/com/company/` - Spring Boot controllers and services

### Database Schema
- `database/migrations/*.sql` - Database table definitions
- `backend/*/src/main/resources/db/migration/` - Flyway migrations

### Deployment Scripts
- `deploy.sh` - Main deployment automation
- `verify-deployment.sh` - Post-deployment validation
- `.github/workflows/deploy.yml` - CI/CD pipeline

## Performance Considerations

### Known Bottlenecks
- **Code Analysis**: Live code analysis not fully implemented
- **Millisecond Timing**: Performance timing guarantees need optimization
- **WebSocket Scaling**: Consider connection pooling for high concurrency

### Optimization Opportunities
- **Database Queries**: Connection pooling configured but may need tuning
- **Redis Caching**: Extensive caching strategy implemented
- **Service Communication**: Consider async patterns for non-critical operations

## Development Environment Notes

### Local Development
- Services run on localhost with SSL certificates in `ssl-certs/`
- Frontend proxy configuration handles CORS for development
- Redis and database connections configured for both local and cloud

### Cloud Development
- Render services auto-deploy from GitHub main branch
- Railway services require manual deployment trigger
- Environment variables managed per-service in deployment platforms

### Debugging
- Health check endpoints available for all services
- Structured logging with correlation IDs
- Service status monitoring via deployment scripts

This architecture represents a production-ready, scalable interview platform with sophisticated AI capabilities and comprehensive development tooling.
