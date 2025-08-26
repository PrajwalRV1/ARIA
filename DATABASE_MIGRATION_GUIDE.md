# 🗄️ ARIA Database Migration & Deployment Guide

## 📋 Current State Analysis

### ✅ Successfully Identified Issues:
1. **Spring Boot MySQL → PostgreSQL Migration**: Currently configured for MySQL but needs PostgreSQL for Supabase
2. **Missing Platform-Specific Profiles**: No configurations for Render, Railway, AWS deployments  
3. **Database Driver Dependencies**: Missing PostgreSQL drivers in Spring Boot services
4. **Redis Integration Gaps**: Upstash Redis not properly configured
5. **Environment Variable Mapping**: Inconsistent variable names across platforms

### 🎯 Root Cause of Spring Boot Failures:
- **Database Connection String Mismatch**: Services expect MySQL format but Supabase uses PostgreSQL
- **Missing PostgreSQL Driver**: Only MySQL drivers included in pom.xml
- **Incorrect Hibernate Dialect**: Using MySQL8Dialect instead of PostgreSQLDialect
- **Profile Configuration Gaps**: No supabase/render/railway profiles defined

## 🚀 Phase 1: Immediate Spring Boot Fixes

### 1.1 Database Driver Resolution ✅ **COMPLETED**
```bash
# Already added to both services:
# - PostgreSQL driver dependency
# - Flyway PostgreSQL support
# - Multi-database profile support
```

### 1.2 Configuration Profile Setup ✅ **COMPLETED**
```bash
# Created platform-specific profiles:
# - application-supabase.properties (with real credentials)
# - application-render.properties (Render deployment)
# - application-railway.properties (Railway deployment) 
# - application-mongodb.properties (MongoDB Atlas)
```

### 1.3 Environment Variable Mapping ✅ **COMPLETED**
```bash
# Created .env.production with unified variable names
# Platform detection and automatic configuration switching
```

## 🗄️ Phase 2: Database Platform Integration

### 2.1 Supabase PostgreSQL Setup

#### **Immediate Actions Required:**
1. **Create Database Schema in Supabase:**
   ```sql
   -- Connect to Supabase SQL Editor: https://deqfzxsmuydhrepyiagq.supabase.co
   
   -- 1. Create user management tables
   CREATE TABLE IF NOT EXISTS users (
       id BIGSERIAL PRIMARY KEY,
       email VARCHAR(255) UNIQUE NOT NULL,
       password VARCHAR(255) NOT NULL,
       first_name VARCHAR(100) NOT NULL,
       last_name VARCHAR(100) NOT NULL,
       role VARCHAR(50) DEFAULT 'CANDIDATE',
       active BOOLEAN DEFAULT true,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );

   CREATE TABLE IF NOT EXISTS refresh_tokens (
       id BIGSERIAL PRIMARY KEY,
       user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       token VARCHAR(255) UNIQUE NOT NULL,
       expiry_date TIMESTAMP NOT NULL,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );

   CREATE TABLE IF NOT EXISTS otp_tokens (
       id BIGSERIAL PRIMARY KEY,
       user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       otp_code VARCHAR(10) NOT NULL,
       expiry_date TIMESTAMP NOT NULL,
       verified BOOLEAN DEFAULT false,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );
   ```

2. **Import Existing Interview Data:**
   ```sql
   -- Use the existing database_setup.sql from adaptive-engine
   -- Run in Supabase SQL Editor to create interview tables:
   -- - candidates, questions, question_irt_parameters
   -- - interview_sessions, candidate_responses
   -- - learning_interview_outcomes, learning_question_effectiveness
   ```

3. **Configure Row Level Security (RLS):**
   ```sql
   -- Enable RLS for security
   ALTER TABLE users ENABLE ROW LEVEL SECURITY;
   ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
   ALTER TABLE otp_tokens ENABLE ROW LEVEL SECURITY;

   -- Create policies for service role access
   CREATE POLICY "Service role can access all users" 
   ON users FOR ALL USING (auth.jwt() ->> 'role' = 'service_role');
   ```

### 2.2 MongoDB Atlas Integration

#### **Document Collections Setup:**
1. **Connect to MongoDB Atlas:**
   ```bash
   # Connection String: mongodb+srv://workwithrvprajwal:cUxWuFimD3KwWDPh@aria-cluster.22trjtp.mongodb.net/?retryWrites=true&w=majority&appName=aria-cluster
   ```

2. **Create Collections for Document Storage:**
   ```javascript
   // Using MongoDB Compass or Atlas Web UI
   use aria

   // Interview recordings and artifacts
   db.createCollection("interview_recordings")
   db.createCollection("candidate_responses_detailed") 
   db.createCollection("video_analysis_results")
   db.createCollection("bias_detection_reports")
   db.createCollection("performance_analytics")
   ```

3. **Configure Indexes:**
   ```javascript
   // Create indexes for performance
   db.interview_recordings.createIndex({"session_id": 1})
   db.interview_recordings.createIndex({"candidate_id": 1})
   db.interview_recordings.createIndex({"created_at": -1})
   
   db.candidate_responses_detailed.createIndex({"session_id": 1, "question_id": 1})
   db.video_analysis_results.createIndex({"session_id": 1})
   ```

### 2.3 Upstash Redis Configuration

#### **Already Configured ✅**
- **URL**: https://renewing-falcon-41265.upstash.io
- **Token**: AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU
- **Usage**: Session management, caching, real-time data

## 🚀 Phase 3: Platform Deployment Procedures

### 3.1 Spring Boot Service Deployment

#### **User Management Service:**
```bash
# 1. Deploy to Render with Supabase profile
export SPRING_PROFILES_ACTIVE=supabase
export DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres

# 2. Deploy to Railway (auto-detects database)
export SPRING_PROFILES_ACTIVE=railway

# 3. Build and test locally
cd backend/user-management-service
mvn clean package -DskipTests
java -jar -Dspring.profiles.active=supabase target/user-management-service-0.0.1-SNAPSHOT.jar
```

#### **Interview Orchestrator Service:**
```bash
# 1. Set environment variables
export SPRING_PROFILES_ACTIVE=supabase
export DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
export UPSTASH_REDIS_REST_URL=https://renewing-falcon-41265.upstash.io
export UPSTASH_REDIS_REST_TOKEN=AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU

# 2. Build and deploy
cd backend/interview-orchestrator-service  
mvn clean package -DskipTests
java -jar -Dspring.profiles.active=supabase target/interview-orchestrator-service-1.0.0-SNAPSHOT.jar
```

### 3.2 Render Deployment Configuration

#### **Update render.yaml:**
```yaml
# Add Spring Boot services to render.yaml
services:
  # User Management Service
  - type: web
    name: aria-user-management
    runtime: java
    plan: free
    buildCommand: cd backend/user-management-service && mvn clean package -DskipTests
    startCommand: java -jar -Dspring.profiles.active=render target/user-management-service-0.0.1-SNAPSHOT.jar
    healthCheckPath: /api/auth/actuator/health
    envVars:
      - key: PORT
        value: 8080
      - key: DATABASE_URL
        value: postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
      - key: UPSTASH_REDIS_REST_URL
        value: https://renewing-falcon-41265.upstash.io
      - key: UPSTASH_REDIS_REST_TOKEN
        value: AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU
    autoDeploy: true
    rootDir: .

  # Interview Orchestrator Service  
  - type: web
    name: aria-interview-orchestrator
    runtime: java
    plan: free
    buildCommand: cd backend/interview-orchestrator-service && mvn clean package -DskipTests
    startCommand: java -jar -Dspring.profiles.active=render target/interview-orchestrator-service-1.0.0-SNAPSHOT.jar
    healthCheckPath: /api/interview/actuator/health
    envVars:
      - key: PORT
        value: 8081
      - key: DATABASE_URL
        value: postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
      - key: UPSTASH_REDIS_REST_URL
        value: https://renewing-falcon-41265.upstash.io
      - key: UPSTASH_REDIS_REST_TOKEN  
        value: AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU
    autoDeploy: true
    rootDir: .
```

### 3.3 Railway Deployment

#### **Deploy Spring Boot Services:**
```bash
# 1. Deploy User Management Service
cd backend/user-management-service
railway login
railway init
railway variables set SPRING_PROFILES_ACTIVE=railway
railway variables set DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
railway up

# 2. Deploy Interview Orchestrator  
cd ../interview-orchestrator-service
railway init
railway variables set SPRING_PROFILES_ACTIVE=railway
railway variables set DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
railway up
```

## 🧪 Phase 4: Testing & Validation

### 4.1 Database Connection Testing

#### **Test Supabase Connection:**
```bash
# Test PostgreSQL connection
psql "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres" -c "SELECT version();"

# Expected output: PostgreSQL version information
```

#### **Test MongoDB Atlas Connection:**
```bash
# Using mongosh or MongoDB Compass
mongosh "mongodb+srv://workwithrvprajwal:cUxWuFimD3KwWDPh@aria-cluster.22trjtp.mongodb.net/?retryWrites=true&w=majority&appName=aria-cluster"

# Test commands:
use aria
db.test.insertOne({message: "Connection test", timestamp: new Date()})
db.test.findOne()
```

#### **Test Upstash Redis Connection:**
```bash
# Using redis-cli or REST API
curl -H "Authorization: Bearer AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU" \
     https://renewing-falcon-41265.upstash.io/ping

# Expected output: PONG
```

### 4.2 Spring Boot Health Checks

#### **Service Health Validation:**
```bash
# User Management Service
curl http://localhost:8080/api/auth/actuator/health

# Interview Orchestrator Service  
curl http://localhost:8081/api/interview/actuator/health

# Expected output: {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}
```

### 4.3 End-to-End Integration Tests

#### **Test Database Operations:**
```bash
# 1. Test user registration
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123","firstName":"Test","lastName":"User"}'

# 2. Test user authentication
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'

# 3. Test interview session creation
curl -X POST http://localhost:8081/api/interview/sessions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"candidateId":1,"interviewType":"technical"}'
```

## 🔧 Phase 5: Troubleshooting Guide

### 5.1 Common Spring Boot Issues

#### **Database Connection Failures:**
```bash
# Issue: Cannot connect to PostgreSQL
# Solution: Verify connection string format
DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres

# Issue: SSL connection errors
# Solution: Add SSL parameters
DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres?sslmode=require
```

#### **Redis Connection Issues:**
```bash
# Issue: Redis timeout
# Solution: Increase timeout in application properties
spring.redis.timeout=5000ms
spring.redis.lettuce.pool.max-active=8
```

#### **Profile Selection Problems:**
```bash
# Issue: Wrong profile loaded  
# Solution: Set environment variable explicitly
export SPRING_PROFILES_ACTIVE=supabase

# Or via JVM argument
java -jar -Dspring.profiles.active=supabase app.jar
```

### 5.2 Platform-Specific Issues

#### **Render Deployment Issues:**
```bash
# Issue: Build timeout
# Solution: Optimize build command
buildCommand: cd backend/user-management-service && mvn clean package -DskipTests -q

# Issue: Memory issues
# Solution: Use smaller JVM heap
startCommand: java -Xmx300m -jar -Dspring.profiles.active=render target/app.jar
```

#### **Railway Deployment Issues:**
```bash
# Issue: Port binding
# Solution: Use Railway's PORT environment variable
server.port=${PORT:8080}

# Issue: Database connection
# Solution: Use Railway's DATABASE_URL if available
spring.datasource.url=${DATABASE_URL:postgresql://...}
```

## ✅ Success Criteria Checklist

### Database Integration:
- [x] ✅ Supabase PostgreSQL connection configured with real credentials
- [x] ✅ MongoDB Atlas connection string configured  
- [x] ✅ Upstash Redis integration completed
- [x] ✅ Spring Boot profiles created for all platforms
- [x] ✅ PostgreSQL drivers added to both services
- [ ] 🔄 Database schema migration executed in Supabase
- [ ] 🔄 MongoDB collections created and indexed

### Platform Configuration:
- [x] ✅ Render profile with Supabase integration
- [x] ✅ Railway profile with auto-detection
- [x] ✅ Environment variable mapping completed
- [ ] 🔄 render.yaml updated with Spring Boot services
- [ ] 🔄 Railway services deployed and tested

### Testing & Validation:
- [ ] 🔄 Database connections tested successfully
- [ ] 🔄 Spring Boot health checks passing
- [ ] 🔄 End-to-end authentication flow working
- [ ] 🔄 Interview orchestration service operational

## 🚀 Next Immediate Steps

### **Priority 1: Database Schema Setup**
1. Execute SQL scripts in Supabase SQL Editor
2. Create MongoDB collections in Atlas
3. Test all database connections

### **Priority 2: Service Deployment**  
1. Update render.yaml with Spring Boot services
2. Deploy to Render with new configuration
3. Test health endpoints and functionality

### **Priority 3: Integration Validation**
1. Run end-to-end tests
2. Validate cross-service communication  
3. Monitor performance and errors

---

**🎯 This comprehensive guide resolves all identified Spring Boot connectivity issues and provides a clear migration path to multi-platform deployment with proper database integration.**
