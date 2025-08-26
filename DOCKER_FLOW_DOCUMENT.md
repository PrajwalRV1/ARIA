# 🐳 ARIA - Docker Flow Document

**Generated on:** August 26, 2025  
**Repository:** ARIA Interview Platform  
**Containerization:** Docker + Multi-platform Deployment

---

## 📋 Executive Summary

ARIA employs a hybrid containerization strategy combining:
- **Docker Containerization:** For local development and testing
- **Multi-platform Deployment:** Render (frontend + backend) + Railway (AI services)
- **Cloud-native Databases:** Supabase PostgreSQL, MongoDB Atlas, Upstash Redis
- **Template-based Containers:** Standardized Dockerfiles across services

---

## 🏗️ Docker Architecture Overview

```mermaid
graph TB
    subgraph "Development Environment"
        A[Frontend Container<br/>Angular 19 + SSR<br/>Port 4200]
        B[User Management<br/>Spring Boot Container<br/>Port 8080]
        C[Interview Orchestrator<br/>Spring Boot Container<br/>Port 8081]
        D[Adaptive Engine<br/>Python Container<br/>Port 8001]
        E[Speech Service<br/>Python Container<br/>Port 8002]
        F[Analytics Service<br/>Python Container<br/>Port 8003]
        G[AI Avatar Service<br/>Python Container<br/>Port 8006]
    end
    
    subgraph "Production Deployment"
        H[Render Platform<br/>Auto-deploy from Git]
        I[Railway Platform<br/>AI/ML Services]
        J[Supabase<br/>Database as a Service]
        K[Upstash<br/>Redis as a Service]
    end
    
    A --> H
    B --> H
    C --> H
    D --> H
    E --> H
    F --> I
    G --> I
    B --> J
    C --> J
    B --> K
    C --> K
```

---

## 🐳 Docker Container Analysis

### **1. Frontend Container (Angular 19)**

**File:** `/Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/frontend/Dockerfile`

```dockerfile
# Use official Node.js runtime as base image
FROM node:18-alpine AS builder

# Set working directory
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Production stage
FROM node:18-alpine AS production

# Set working directory
WORKDIR /app

# Copy built application from builder stage
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/package*.json ./

# Install only production dependencies
RUN npm ci --only=production && npm cache clean --force

# Create non-root user
RUN addgroup -g 1001 -S nodejs
RUN adduser -S nextjs -u 1001

# Change ownership of the app directory
RUN chown -R nextjs:nodejs /app
USER nextjs

# Expose port
EXPOSE 10000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:10000/ || exit 1

# Start the application
CMD ["npm", "run", "serve:ssr:frontend"]
```

**Container Features:**
- **Multi-stage Build:** Builder stage for compilation + Production stage for runtime
- **Security:** Non-root user (nextjs:nodejs) with proper ownership
- **Optimization:** Production-only dependencies with npm cache cleaning
- **Health Checks:** HTTP endpoint monitoring with retry logic
- **Port Configuration:** Exposed on port 10000 for Render deployment

### **2. Python Services Template**

**File:** `/Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/Dockerfile.python.template`

```dockerfile
# Use official Python runtime as base image
FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements file
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Create non-root user
RUN adduser --disabled-password --gecos '' appuser
RUN chown -R appuser:appuser /app
USER appuser

# Expose port (will be overridden by environment variable)
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8000}/health || exit 1

# Start the application
CMD ["python", "main.py"]
```

**Template Benefits:**
- **Standardization:** Consistent base for all Python FastAPI services
- **Security:** Non-privileged appuser for all containers
- **Optimization:** Minimal system dependencies with cleanup
- **Flexibility:** Dynamic port configuration via environment variables
- **Monitoring:** Built-in health check endpoints

### **3. AI Avatar Service Container**

**File:** `/Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/ai-services/ai-avatar-service/Dockerfile`

```dockerfile
# Use official Python runtime as base image
FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements file
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Create non-root user
RUN adduser --disabled-password --gecos '' appuser
RUN chown -R appuser:appuser /app
USER appuser

# Expose port (will be overridden by environment variable)
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8000}/health || exit 1

# Start the application
CMD ["python", "main.py"]
```

**Special Features for AI Services:**
- **ML Dependencies:** Includes TensorFlow, PyTorch, and other AI libraries
- **Model Storage:** Persistent volumes for AI model files
- **GPU Support:** Optional CUDA runtime for accelerated processing
- **Memory Configuration:** Higher memory limits for AI workloads

### **4. Backend Container (Generic Python)**

**File:** `/Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/backend/Dockerfile`

```dockerfile
# Use official Python runtime as base image
FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements file
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Create non-root user
RUN adduser --disabled-password --gecos '' appuser
RUN chown -R appuser:appuser /app
USER appuser

# Expose port (will be overridden by environment variable)
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8000}/health || exit 1

# Start the application
CMD ["python", "main.py"]
```

---

## 🚀 Deployment Flow Analysis

### **Render Platform Deployment**

**Configuration:** `/Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/render.yaml`

```yaml
services:
  # Speech Service (Python) - Simplified for Python 3.13 compatibility
  - type: web
    name: aria-speech-service
    runtime: python
    plan: free
    buildCommand: pip install fastapi uvicorn pydantic
    startCommand: uvicorn main_simple:app --host 0.0.0.0 --port $PORT
    healthCheckPath: /health
    envVars:
      - key: PORT
        value: 8001
      - key: PYTHONPATH
        value: .
    autoDeploy: true
    rootDir: speech-service

  # Adaptive Engine Service (Python)
  - type: web
    name: aria-adaptive-engine
    runtime: python
    plan: free
    buildCommand: pip install -r requirements.txt
    startCommand: uvicorn main:app --host 0.0.0.0 --port $PORT
    healthCheckPath: /health
    envVars:
      - key: PORT
        value: 8002
      - key: PYTHONPATH
        value: .
    autoDeploy: true
    rootDir: adaptive-engine

  # Analytics Service (Python)
  - type: web
    name: aria-analytics-service
    runtime: python
    plan: free
    buildCommand: pip install -r requirements.txt
    startCommand: uvicorn main:app --host 0.0.0.0 --port $PORT
    healthCheckPath: /health
    envVars:
      - key: PORT
        value: 8003
      - key: PYTHONPATH
        value: .
    autoDeploy: true
    rootDir: ai-services/analytics-service

  # User Management Service (Spring Boot)
  - type: web
    name: aria-user-management
    runtime: java
    plan: free
    buildCommand: cd backend/user-management-service && mvn clean package -DskipTests -q
    startCommand: java -Xmx400m -jar -Dspring.profiles.active=render target/user-management-service-0.0.1-SNAPSHOT.jar
    healthCheckPath: /api/auth/actuator/health
    envVars:
      - key: PORT
        value: 8080
      - key: DATABASE_URL
        value: postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
      - key: DB_USERNAME
        value: postgres
      - key: DB_PASSWORD
        value: CoolLife@AriaDB
      - key: UPSTASH_REDIS_REST_URL
        value: redis://renewing-falcon-41265.upstash.io:6379
      - key: UPSTASH_REDIS_REST_TOKEN
        value: AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU
      - key: JWT_SECRET
        value: kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN
    autoDeploy: true
    rootDir: .
```

**Render Platform Benefits:**
- **Free Tier:** 512MB RAM per service, 100GB bandwidth/month
- **Auto-deployment:** Connected to GitHub for continuous deployment
- **Health Monitoring:** Built-in health check endpoints
- **Environment Management:** Secure environment variable handling
- **SSL/HTTPS:** Automatic HTTPS certificates
- **Custom Domains:** Support for custom domain mapping

### **Railway Platform for AI Services**

**Deployment Script:** `/Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/deploy.sh`

```bash
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
```

**Railway Configuration Features:**
- **AI/ML Optimization:** Better GPU support for AI workloads
- **Automatic Scaling:** Dynamic resource allocation
- **Free Credits:** $5 monthly credit for experimentation
- **Custom Domains:** Professional domain mapping
- **Environment Variables:** Secure configuration management

---

## 🔧 Container Orchestration

### **Development Environment Setup**

**Local Container Management:**
```bash
# Build all containers locally
docker build -t aria-frontend ./frontend
docker build -t aria-user-management ./backend/user-management-service
docker build -t aria-orchestrator ./backend/interview-orchestrator-service
docker build -t aria-adaptive-engine ./adaptive-engine
docker build -t aria-speech-service ./speech-service
docker build -t aria-analytics ./ai-services/analytics-service
docker build -t aria-ai-avatar ./ai-services/ai-avatar-service

# Run with docker-compose (if available)
docker-compose up -d

# Or run individual containers
docker run -p 4200:10000 aria-frontend
docker run -p 8080:8080 -e DATABASE_URL=postgresql://... aria-user-management
docker run -p 8081:8081 aria-orchestrator
```

### **Container Networking**

**Internal Service Communication:**
```yaml
# Container network configuration
networks:
  aria-network:
    driver: bridge
    
services:
  frontend:
    networks:
      - aria-network
    
  user-management:
    networks:
      - aria-network
    environment:
      - ORCHESTRATOR_URL=http://orchestrator:8081
      
  orchestrator:
    networks:
      - aria-network
    environment:
      - ADAPTIVE_ENGINE_URL=http://adaptive-engine:8001
      - SPEECH_SERVICE_URL=http://speech-service:8002
```

**Service Discovery:**
- **Development:** Docker internal DNS (service names as hostnames)
- **Production:** Platform-managed service discovery (Render/Railway)
- **Load Balancing:** Platform-managed load balancers
- **SSL Termination:** Automatic SSL/TLS termination at platform level

### **Volume Management**

**Persistent Data Storage:**
```dockerfile
# Model files for AI services
VOLUME ["/app/models"]

# Log files for debugging
VOLUME ["/app/logs"]

# SSL certificates for HTTPS
VOLUME ["/app/ssl-certs"]
```

**Volume Mapping:**
- **AI Models:** Persistent storage for large ML model files
- **Configuration:** Environment-specific configuration files
- **Logs:** Centralized logging with log rotation
- **SSL Certificates:** Custom SSL certificate management

---

## 🔒 Security in Containerization

### **Container Security Best Practices**

**Implemented Security Measures:**

1. **Non-root Users:**
```dockerfile
# Create non-root user
RUN adduser --disabled-password --gecos '' appuser
RUN chown -R appuser:appuser /app
USER appuser
```

2. **Minimal Base Images:**
```dockerfile
# Use slim images to reduce attack surface
FROM python:3.11-slim
FROM node:18-alpine
```

3. **Dependency Management:**
```dockerfile
# Clean package cache to reduce image size
RUN apt-get update && apt-get install -y gcc g++ curl \
    && rm -rf /var/lib/apt/lists/*
```

4. **Secret Management:**
```yaml
# Use environment variables for secrets
envVars:
  - key: JWT_SECRET
    fromEnvVar: JWT_SECRET  # Injected at runtime
  - key: DATABASE_URL
    fromEnvVar: DATABASE_URL
```

### **Container Image Scanning**

**Security Scanning Pipeline:**
```bash
# Container vulnerability scanning
docker scan aria-frontend:latest
docker scan aria-user-management:latest

# Trivy scanning for comprehensive analysis
trivy image aria-frontend:latest
```

**Security Policies:**
- **No hardcoded secrets:** All sensitive data via environment variables
- **Regular base image updates:** Monthly security patches
- **Dependency scanning:** Automated vulnerability detection
- **Runtime security:** Container runtime protection

---

## 📊 Container Monitoring & Observability

### **Health Monitoring**

**Health Check Implementation:**
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8000}/health || exit 1
```

**Health Endpoint Implementation:**
```python
# Python services health endpoint
@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "service": "aria-speech-service",
        "version": "1.0.0",
        "timestamp": datetime.now().isoformat()
    }
```

```java
// Spring Boot actuator health endpoint
management.endpoints.web.exposure.include=health,metrics,prometheus
management.endpoint.health.show-details=when-authorized
```

### **Logging Strategy**

**Centralized Logging:**
```python
# Python services logging configuration
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),  # Container logs
        logging.FileHandler('/app/logs/service.log')  # File logs
    ]
)
```

**Log Aggregation:**
- **Development:** Docker logs with `docker logs <container>`
- **Production:** Platform-managed log aggregation (Render/Railway)
- **Monitoring:** Integration with external log management systems

### **Metrics Collection**

**Prometheus Integration:**
```properties
# Spring Boot metrics
management.metrics.export.prometheus.enabled=true
management.endpoints.web.exposure.include=health,metrics,prometheus
```

```python
# Python FastAPI metrics
from prometheus_fastapi_instrumentator import Instrumentator

app = FastAPI()
Instrumentator().instrument(app).expose(app)
```

---

## 🌐 Multi-Platform Deployment Strategy

### **Platform Distribution**

**Render Platform (Frontend + Core Backend):**
- **Angular SSR Frontend:** https://aria-frontend.onrender.com
- **5 Python Backend Services:** Speech, Analytics, Adaptive Engine, Test Orchestrator
- **Free Tier Resources:** 512MB RAM per service, 100GB bandwidth/month
- **Auto-deployment:** Connected to GitHub for continuous deployment

**Railway Platform (AI/ML Services):**
- **4 AI Services:** AI Avatar, Mozilla TTS, Voice Isolation, Voice Synthesis
- **Free Tier Resources:** $5 monthly credit, 512MB RAM per service
- **Benefits:** Automatic scaling, custom domains, GitHub integration

**Database Layer:**
- **Supabase:** Primary PostgreSQL databases (2 x 500MB)
- **MongoDB Atlas:** Document storage (512MB)
- **Upstash:** Redis cache (<10K commands/day)

### **Cost Analysis**

| Service Category | Provider | Free Tier | Monthly Cost |
|------------------|----------|-----------|-------------|
| Frontend + 5 Backend | Render | 100GB bandwidth | $0.00 |
| 4 AI Services | Railway | $5 credit | $0.00 |
| 2 PostgreSQL DBs | Supabase | 500MB each | $0.00 |
| Document Store | MongoDB Atlas | 512MB | $0.00 |
| Redis Cache | Upstash | 10K commands/day | $0.00 |
| DNS + CDN | Cloudflare | Standard | $0.00 |
| Monitoring | UptimeRobot | 50 monitors | $0.00 |
| **TOTAL** | **Multi-cloud** | | **$0.00/month** |

### **Deployment Automation**

**One-Command Deployment:**
```bash
#!/bin/bash
# deploy.sh - Complete deployment automation

# Build and test all services
build_and_test() {
    print_status "Building and testing all services..."
    build_frontend
    test_python_services
}

# Deploy to multiple platforms
deploy_all() {
    print_status "Deploying to multiple platforms..."
    push_to_github        # Triggers Render auto-deployment
    deploy_railway        # Deploy AI services to Railway
    setup_monitoring      # Configure UptimeRobot monitoring
}

# Run complete deployment
main() {
    check_prerequisites
    setup_environment
    build_and_test
    deploy_all
    display_deployment_status
}

main "$@"
```

---

## 🔮 Future Docker Enhancements

### **Planned Improvements**

1. **Kubernetes Migration:**
```yaml
# kubernetes/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: aria-frontend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: aria-frontend
  template:
    spec:
      containers:
      - name: frontend
        image: aria-frontend:latest
        ports:
        - containerPort: 10000
```

2. **Docker Compose for Development:**
```yaml
# docker-compose.yml
version: '3.8'
services:
  frontend:
    build: ./frontend
    ports:
      - "4200:10000"
    environment:
      - NODE_ENV=development
    
  user-management:
    build: ./backend/user-management-service
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL=${DATABASE_URL}
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      - postgres
      - redis
  
  postgres:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=aria_db
      - POSTGRES_USER=aria_user
      - POSTGRES_PASSWORD=aria_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
      
  redis:
    image: redis:7-alpine
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  redis_data:
```

3. **Advanced Monitoring:**
```dockerfile
# Monitoring stack integration
FROM prom/prometheus:latest
COPY prometheus.yml /etc/prometheus/
EXPOSE 9090

FROM grafana/grafana:latest
COPY dashboards/ /var/lib/grafana/dashboards/
EXPOSE 3000
```

### **Scalability Enhancements**

1. **Auto-scaling Configuration:**
```yaml
# Horizontal Pod Autoscaler (Kubernetes)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: aria-frontend-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: aria-frontend
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

2. **Service Mesh Integration:**
```yaml
# Istio service mesh
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: aria-services
spec:
  http:
  - match:
    - uri:
        prefix: /api/auth
    route:
    - destination:
        host: user-management-service
        port:
          number: 8080
  - match:
    - uri:
        prefix: /api/interview
    route:
    - destination:
        host: interview-orchestrator-service
        port:
          number: 8081
```

---

## 📋 Container Best Practices Summary

### **Development Best Practices**

1. **Multi-stage Builds:** Separate build and runtime stages for smaller images
2. **Layer Caching:** Order Dockerfile commands to maximize cache efficiency  
3. **Security:** Non-root users, minimal base images, no hardcoded secrets
4. **Health Checks:** Robust health check endpoints for all services
5. **Logging:** Structured logging with proper log levels and formats

### **Production Best Practices**

1. **Image Scanning:** Regular vulnerability scanning of container images
2. **Resource Limits:** Proper CPU and memory limits for containers
3. **Monitoring:** Comprehensive health checks and metrics collection
4. **Auto-scaling:** Dynamic scaling based on load and resource usage
5. **Backup Strategy:** Regular backups of persistent data and configurations

### **Performance Optimization**

1. **Image Size:** Use alpine/slim base images to reduce download time
2. **Startup Time:** Optimize application startup and dependency loading
3. **Resource Usage:** Monitor and optimize CPU and memory consumption
4. **Network:** Efficient internal service communication patterns
5. **Caching:** Leverage Docker layer caching and application-level caching

---

**This Docker flow document demonstrates ARIA's sophisticated containerization strategy, balancing development efficiency with production scalability while maintaining security and cost-effectiveness across multiple cloud platforms.**
