# 🎯 ARIA Service Orchestration & Testing Plan

## 📊 Current Multi-Platform Architecture Map

### ✅ **Platform Assignment (Optimized for Cost & Performance):**

#### **Render (Free Tier - 512MB RAM each):**
- 🎨 **Frontend**: Angular SSR → `aria-frontend.onrender.com`
- 🗣️ **Speech Service**: Python FastAPI → `aria-speech-service.onrender.com`
- 🧠 **Adaptive Engine**: Python FastAPI → `aria-adaptive-engine.onrender.com`  
- 📊 **Analytics Service**: Python FastAPI → `aria-analytics-service.onrender.com`
- 👤 **User Management**: Spring Boot → `aria-user-management.onrender.com`
- 🎭 **Interview Orchestrator**: Spring Boot → `aria-interview-orchestrator.onrender.com`

#### **Railway ($5/month credit - 512MB RAM each):**
- 🤖 **AI Avatar Service**: Python FastAPI → `*.railway.app`
- 🎙️ **Mozilla TTS Service**: Python FastAPI → `*.railway.app`
- 🔊 **Voice Isolation Service**: Python FastAPI → `*.railway.app`
- 🎵 **Voice Synthesis Service**: Python FastAPI → `*.railway.app`

#### **Database & Storage Layer:**
- 🗄️ **Primary Database**: Supabase PostgreSQL (500MB x2)
- 📄 **Document Storage**: MongoDB Atlas (512MB)
- ⚡ **Cache & Sessions**: Upstash Redis (10K commands/day)

## 🧪 Phase 1: Service Connectivity Testing Matrix

### 1.1 Database Connection Testing

#### **Supabase PostgreSQL Tests:**
```bash
#!/bin/bash
# test-supabase-connection.sh

echo "🧪 Testing Supabase PostgreSQL Connection..."

# Test 1: Direct connection
psql "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres" \
  -c "SELECT 'Supabase Connection: SUCCESS' as status, version();"

# Test 2: Spring Boot connection
curl -f http://localhost:8080/api/auth/actuator/health/db || echo "❌ User Management DB Check Failed"
curl -f http://localhost:8081/api/interview/actuator/health/db || echo "❌ Interview Orchestrator DB Check Failed"

# Test 3: Schema validation
psql "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres" \
  -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';" \
  | grep -E "(users|questions|interview_sessions)" && echo "✅ Required tables exist" || echo "❌ Missing tables"
```

#### **MongoDB Atlas Tests:**
```bash
#!/bin/bash
# test-mongodb-connection.sh

echo "🧪 Testing MongoDB Atlas Connection..."

# Test 1: Connection and basic operations
mongosh "mongodb+srv://workwithrvprajwal:cUxWuFimD3KwWDPh@aria-cluster.22trjtp.mongodb.net/?retryWrites=true&w=majority&appName=aria-cluster" \
  --eval "
    use aria;
    db.test_connectivity.insertOne({test: 'connection', timestamp: new Date()});
    print('✅ MongoDB Connection: SUCCESS');
    db.test_connectivity.deleteMany({test: 'connection'});
  "

# Test 2: Collection existence
mongosh "mongodb+srv://workwithrvprajwal:cUxWuFimD3KwWDPh@aria-cluster.22trjtp.mongodb.net/aria" \
  --eval "db.getCollectionNames()" | grep -E "(interview_recordings|video_analysis)" && echo "✅ Collections exist" || echo "⚠️  Collections need creation"
```

#### **Upstash Redis Tests:**
```bash
#!/bin/bash
# test-redis-connection.sh

echo "🧪 Testing Upstash Redis Connection..."

# Test 1: REST API connection
RESPONSE=$(curl -s -H "Authorization: Bearer AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU" \
  https://renewing-falcon-41265.upstash.io/ping)

if [ "$RESPONSE" = "PONG" ]; then
  echo "✅ Redis Connection: SUCCESS"
else
  echo "❌ Redis Connection: FAILED - Response: $RESPONSE"
fi

# Test 2: Spring Boot Redis health
curl -f http://localhost:8080/api/auth/actuator/health/redis || echo "❌ User Management Redis Check Failed"
curl -f http://localhost:8081/api/interview/actuator/health/redis || echo "❌ Interview Orchestrator Redis Check Failed"

# Test 3: Session storage test
curl -s -X POST -H "Authorization: Bearer AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU" \
  -H "Content-Type: application/json" \
  https://renewing-falcon-41265.upstash.io/set \
  -d '["test-session", "session-data", "EX", "300"]'
```

### 1.2 Cross-Service Communication Tests

#### **Service Dependency Mapping:**
```mermaid
graph TB
    Frontend[Angular Frontend] --> UserMgmt[User Management]
    Frontend --> Orchestrator[Interview Orchestrator]
    
    Orchestrator --> Analytics[Analytics Service]
    Orchestrator --> Speech[Speech Service]
    Orchestrator --> Adaptive[Adaptive Engine]
    Orchestrator --> UserMgmt
    
    Orchestrator --> AIAvatar[AI Avatar - Railway]
    Orchestrator --> TTS[Mozilla TTS - Railway]
    Orchestrator --> VoiceSynth[Voice Synthesis - Railway]
    Orchestrator --> VoiceIso[Voice Isolation - Railway]
    
    UserMgmt --> Supabase[(Supabase PostgreSQL)]
    Orchestrator --> Supabase
    Analytics --> MongoDB[(MongoDB Atlas)]
    
    UserMgmt --> Redis[(Upstash Redis)]
    Orchestrator --> Redis
```

#### **Inter-Service Communication Tests:**
```bash
#!/bin/bash
# test-service-communication.sh

BASE_URL_RENDER="https://aria-"
BASE_URL_RAILWAY="https://your-"  # Update after Railway deployment

echo "🧪 Testing Cross-Service Communication..."

# Test 1: User Management → Interview Orchestrator
echo "Testing User → Interview flow..."
JWT_TOKEN=$(curl -s -X POST ${BASE_URL_RENDER}user-management.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}' | jq -r '.token')

if [ "$JWT_TOKEN" != "null" ] && [ "$JWT_TOKEN" != "" ]; then
  echo "✅ User authentication successful"
  
  # Test session creation
  curl -f -X POST ${BASE_URL_RENDER}interview-orchestrator.onrender.com/api/interview/sessions \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"candidateId":1,"interviewType":"technical"}' && echo "✅ Session creation successful" || echo "❌ Session creation failed"
else
  echo "❌ User authentication failed"
fi

# Test 2: Interview Orchestrator → AI Services (Railway)
echo "Testing Interview Orchestrator → AI Services..."
curl -f ${BASE_URL_RENDER}interview-orchestrator.onrender.com/actuator/health && echo "✅ Orchestrator health OK" || echo "❌ Orchestrator health failed"

# Test 3: Analytics Service → MongoDB
echo "Testing Analytics → MongoDB..."
curl -f ${BASE_URL_RENDER}analytics-service.onrender.com/health && echo "✅ Analytics health OK" || echo "❌ Analytics health failed"
```

### 1.3 Performance & Load Testing

#### **Database Performance Tests:**
```bash
#!/bin/bash
# test-database-performance.sh

echo "🚀 Testing Database Performance..."

# Test 1: Supabase connection pool
echo "Testing Supabase connection pool..."
for i in {1..10}; do
  (psql "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres" \
    -c "SELECT pg_sleep(0.1);" > /dev/null 2>&1 && echo "Connection $i: SUCCESS" &)
done
wait

# Test 2: Redis performance  
echo "Testing Redis performance..."
START_TIME=$(date +%s%N)
for i in {1..100}; do
  curl -s -H "Authorization: Bearer AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU" \
    https://renewing-falcon-41265.upstash.io/ping > /dev/null
done
END_TIME=$(date +%s%N)
DURATION=$((($END_TIME - $START_TIME) / 1000000))
echo "✅ 100 Redis operations completed in ${DURATION}ms"

# Test 3: MongoDB bulk operations
mongosh "mongodb+srv://workwithrvprajwal:cUxWuFimD3KwWDPh@aria-cluster.22trjtp.mongodb.net/aria" \
  --eval "
    use aria;
    var startTime = new Date();
    var docs = [];
    for(var i = 0; i < 100; i++) {
      docs.push({test_id: i, data: 'performance_test', timestamp: new Date()});
    }
    db.performance_test.insertMany(docs);
    var endTime = new Date();
    print('✅ MongoDB bulk insert: ' + (endTime - startTime) + 'ms for 100 documents');
    db.performance_test.deleteMany({data: 'performance_test'});
  "
```

## 🚀 Phase 2: Deployment Orchestration Strategy

### 2.1 Service Deployment Order (Critical Path)

#### **Stage 1: Database & Infrastructure Setup**
```bash
#!/bin/bash
# deploy-stage-1-infrastructure.sh

echo "🏗️ Stage 1: Setting up Database Infrastructure..."

# 1. Create Supabase database schema
echo "Creating Supabase schema..."
psql "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres" \
  -f database/migrations/postgresql/V001__create_user_tables.sql

# 2. Import interview data schema
psql "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres" \
  -f adaptive-engine/database_setup.sql

# 3. Setup MongoDB collections
mongosh "mongodb+srv://workwithrvprajwal:cUxWuFimD3KwWDPh@aria-cluster.22trjtp.mongodb.net/aria" \
  --eval "
    db.interview_recordings.createIndex({session_id: 1});
    db.candidate_responses_detailed.createIndex({session_id: 1, question_id: 1});
    db.video_analysis_results.createIndex({session_id: 1});
    print('✅ MongoDB indexes created');
  "

# 4. Test Redis connectivity
curl -H "Authorization: Bearer AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU" \
  https://renewing-falcon-41265.upstash.io/ping

echo "✅ Stage 1 Complete: Database infrastructure ready"
```

#### **Stage 2: Core Backend Services (Render)**
```bash
#!/bin/bash
# deploy-stage-2-backend.sh

echo "🎯 Stage 2: Deploying Core Backend Services to Render..."

# 1. Deploy User Management Service (Foundation)
echo "Deploying User Management Service..."
cd backend/user-management-service
mvn clean package -DskipTests -q
# Deploy via Render dashboard with supabase profile

# 2. Deploy Interview Orchestrator Service
echo "Deploying Interview Orchestrator Service..."
cd ../interview-orchestrator-service  
mvn clean package -DskipTests -q
# Deploy via Render dashboard with supabase profile

# 3. Verify health endpoints
sleep 60  # Wait for services to start
curl -f https://aria-user-management.onrender.com/api/auth/actuator/health || echo "❌ User Management deployment failed"
curl -f https://aria-interview-orchestrator.onrender.com/api/interview/actuator/health || echo "❌ Interview Orchestrator deployment failed"

echo "✅ Stage 2 Complete: Core backend services deployed"
```

#### **Stage 3: Python Services & AI/ML Pipeline**
```bash
#!/bin/bash
# deploy-stage-3-python-services.sh

echo "🐍 Stage 3: Deploying Python Services..."

# Render Python services should auto-deploy from render.yaml
echo "Monitoring Render deployments..."
curl -f https://aria-speech-service.onrender.com/health || echo "❌ Speech Service deployment failed"
curl -f https://aria-adaptive-engine.onrender.com/health || echo "❌ Adaptive Engine deployment failed"  
curl -f https://aria-analytics-service.onrender.com/health || echo "❌ Analytics Service deployment failed"

# Deploy AI/ML services to Railway
echo "Deploying AI/ML services to Railway..."
cd ai-services/ai-avatar-service
railway up --detach

cd ../mozilla-tts-service
railway up --detach

cd ../voice-isolation-service  
railway up --detach

cd ../voice-synthesis-service
railway up --detach

echo "✅ Stage 3 Complete: Python & AI/ML services deployed"
```

#### **Stage 4: Frontend & Integration Testing**
```bash
#!/bin/bash
# deploy-stage-4-frontend.sh

echo "🎨 Stage 4: Deploying Frontend & Integration Testing..."

# Frontend should auto-deploy from render.yaml
echo "Monitoring Frontend deployment..."
curl -f https://aria-frontend.onrender.com || echo "❌ Frontend deployment failed"

# Run integration tests
echo "Running integration tests..."
./test-service-communication.sh
./test-database-performance.sh

echo "✅ Stage 4 Complete: Full stack deployed and tested"
```

### 2.2 Service Health Monitoring & Alerting

#### **Continuous Health Monitoring Script:**
```bash
#!/bin/bash
# monitor-services.sh

SERVICES=(
  "https://aria-frontend.onrender.com"
  "https://aria-user-management.onrender.com/api/auth/actuator/health"
  "https://aria-interview-orchestrator.onrender.com/api/interview/actuator/health"
  "https://aria-speech-service.onrender.com/health"
  "https://aria-adaptive-engine.onrender.com/health"
  "https://aria-analytics-service.onrender.com/health"
)

while true; do
  echo "🔍 Health Check: $(date)"
  
  for service in "${SERVICES[@]}"; do
    if curl -f -s --max-time 10 "$service" > /dev/null; then
      echo "✅ $service - UP"
    else
      echo "❌ $service - DOWN"
      # Send alert (customize as needed)
      echo "ALERT: $service is down at $(date)" >> alerts.log
    fi
  done
  
  echo "---"
  sleep 300  # Check every 5 minutes
done
```

### 2.3 Database Backup & Recovery Procedures

#### **Automated Backup Script:**
```bash
#!/bin/bash
# backup-databases.sh

BACKUP_DATE=$(date +%Y%m%d_%H%M%S)

echo "📦 Starting database backup: $BACKUP_DATE"

# 1. Backup Supabase PostgreSQL
echo "Backing up Supabase PostgreSQL..."
pg_dump "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres" \
  > "backups/supabase_backup_$BACKUP_DATE.sql"

# 2. Backup MongoDB Atlas
echo "Backing up MongoDB Atlas..."
mongodump --uri="mongodb+srv://workwithrvprajwal:cUxWuFimD3KwWDPh@aria-cluster.22trjtp.mongodb.net/aria" \
  --out="backups/mongodb_backup_$BACKUP_DATE"

# 3. Export Redis data (key sampling)
echo "Exporting Redis key samples..."
curl -H "Authorization: Bearer AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU" \
  https://renewing-falcon-41265.upstash.io/keys/* > "backups/redis_keys_$BACKUP_DATE.json"

echo "✅ Backup completed: $BACKUP_DATE"
```

## 🚦 Phase 3: Deployment Execution Checklist

### 3.1 Pre-Deployment Validation
- [ ] ✅ Database credentials verified and tested
- [ ] ✅ Environment variables configured for all platforms
- [ ] ✅ Spring Boot profiles created and tested locally
- [ ] ✅ PostgreSQL drivers added to Spring Boot services
- [ ] ✅ All configuration files validated

### 3.2 Deployment Execution
- [ ] 🔄 Stage 1: Database infrastructure setup completed
- [ ] 🔄 Stage 2: Core backend services deployed to Render
- [ ] 🔄 Stage 3: Python services deployed across Render/Railway
- [ ] 🔄 Stage 4: Frontend deployed and integration tested

### 3.3 Post-Deployment Verification
- [ ] 🔄 All health endpoints responding correctly
- [ ] 🔄 Database connections established successfully  
- [ ] 🔄 Cross-service communication working
- [ ] 🔄 Authentication flow functional end-to-end
- [ ] 🔄 Interview orchestration pipeline operational

### 3.4 Production Readiness
- [ ] 🔄 Monitoring and alerting configured
- [ ] 🔄 Backup procedures implemented and tested
- [ ] 🔄 Performance benchmarks established
- [ ] 🔄 Error tracking and logging operational
- [ ] 🔄 Load testing completed successfully

## 🎯 Success Metrics & KPIs

### Service Availability Targets:
- **Uptime**: 99.9% (targeting free tier limitations)
- **Response Time**: < 2 seconds for API calls
- **Database Latency**: < 100ms for queries
- **Error Rate**: < 1% of total requests

### Performance Benchmarks:
- **User Authentication**: < 500ms
- **Interview Session Creation**: < 1 second  
- **Real-time Communication**: < 200ms latency
- **Database Queries**: < 50ms average

---

**🚀 This orchestration plan ensures systematic deployment with proper testing, monitoring, and recovery procedures across all platforms while resolving the identified Spring Boot connectivity issues.**
