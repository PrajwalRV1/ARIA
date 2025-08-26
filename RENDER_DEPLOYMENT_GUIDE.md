# Render Deployment Guide for ARIA Services

## 🚀 Overview

This guide will help you deploy both **user-management-service** and **interview-orchestrator-service** to Render using their web interface.

## 📋 Prerequisites

1. **GitHub Repository**: Ensure your code is pushed to a GitHub repository
2. **Render Account**: Create a free account at [render.com](https://render.com)
3. **Environment Variables**: Have your database credentials ready (see below)

## 🛠 Service Configurations

### 1. User Management Service

**Service Details:**
- **Name**: `aria-user-management`
- **Runtime**: Docker
- **Plan**: Free
- **Health Check**: `/api/auth/actuator/health`
- **Port**: 8080

**Build & Deploy:**
- **Dockerfile Path**: `backend/user-management-service/Dockerfile`
- **Build Context**: Root directory (`.`)

**Environment Variables:**
```bash
SPRING_PROFILES_ACTIVE=render
PORT=8080
DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=CoolLife@AriaDB
UPSTASH_REDIS_REST_URL=redis://renewing-falcon-41265.upstash.io:6379
UPSTASH_REDIS_REST_TOKEN=AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU
JWT_SECRET=kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN
CORS_ORIGINS=https://aria-frontend.onrender.com
```

### 2. Interview Orchestrator Service

**Service Details:**
- **Name**: `aria-interview-orchestrator`
- **Runtime**: Docker
- **Plan**: Free
- **Health Check**: `/api/interview/actuator/health`
- **Port**: 8081

**Build & Deploy:**
- **Dockerfile Path**: `backend/interview-orchestrator-service/Dockerfile`
- **Build Context**: Root directory (`.`)

**Environment Variables:**
```bash
SPRING_PROFILES_ACTIVE=supabase
PORT=8081
DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=CoolLife@AriaDB
UPSTASH_REDIS_REST_URL=redis://renewing-falcon-41265.upstash.io:6379
UPSTASH_REDIS_REST_TOKEN=AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU
JWT_SECRET=kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN
USER_MGMT_URL=https://aria-user-management.onrender.com
CORS_ORIGINS=https://aria-frontend.onrender.com
```

## 🎯 Step-by-Step Deployment

### Step 1: Deploy User Management Service

1. **Go to Render Dashboard**: https://dashboard.render.com
2. **Click "New +"** → **"Web Service"**
3. **Connect GitHub**: Connect your repository
4. **Configure Service**:
   - **Name**: `aria-user-management`
   - **Runtime**: Docker
   - **Branch**: `main`
   - **Root Directory**: Leave blank (uses repo root)
   - **Dockerfile Path**: `backend/user-management-service/Dockerfile`
   - **Plan**: Free

5. **Environment Variables**: Add all the environment variables listed above for User Management Service

6. **Health Check**: Set to `/api/auth/actuator/health`

7. **Click "Create Web Service"**

### Step 2: Deploy Interview Orchestrator Service

1. **Click "New +"** → **"Web Service"**
2. **Connect the same GitHub repository**
3. **Configure Service**:
   - **Name**: `aria-interview-orchestrator`
   - **Runtime**: Docker
   - **Branch**: `main`
   - **Root Directory**: Leave blank (uses repo root)
   - **Dockerfile Path**: `backend/interview-orchestrator-service/Dockerfile`
   - **Plan**: Free

4. **Environment Variables**: Add all the environment variables listed above for Interview Orchestrator Service

5. **Health Check**: Set to `/api/interview/actuator/health`

6. **Click "Create Web Service"**

### Step 3: Update Service URLs

After both services are deployed, update the `USER_MGMT_URL` in the Interview Orchestrator service:

1. Go to Interview Orchestrator service settings
2. Update `USER_MGMT_URL` to: `https://aria-user-management.onrender.com`
3. Save and redeploy

## 🔗 Expected Service URLs

After successful deployment:

- **User Management Service**: `https://aria-user-management.onrender.com`
- **Interview Orchestrator Service**: `https://aria-interview-orchestrator.onrender.com`

## 🩺 Health Checks

Verify your services are running:

- User Management: `https://aria-user-management.onrender.com/api/auth/actuator/health`
- Interview Orchestrator: `https://aria-interview-orchestrator.onrender.com/api/interview/actuator/health`

## 🔧 Troubleshooting

### Common Issues:

1. **Build Failures**:
   - Check Docker build logs in Render dashboard
   - Verify Maven dependencies in pom.xml
   - Ensure Java 17 is specified in Dockerfile

2. **Health Check Failures**:
   - Verify Spring Actuator is enabled
   - Check if the health endpoint is accessible
   - Ensure correct health check path

3. **Database Connection Issues**:
   - Verify DATABASE_URL format
   - Check credentials are correct
   - Ensure PostgreSQL dialect is configured

4. **Memory Issues**:
   - Free tier has 512MB RAM limit
   - Optimize JVM options in Dockerfile
   - Consider using `-Xmx400m` flag

### Debug Commands:

```bash
# Check service logs
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://api.render.com/v1/services/YOUR_SERVICE_ID/events

# Test endpoints locally
docker build -t user-mgmt-service backend/user-management-service/
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=render user-mgmt-service
```

## 📊 Monitoring

1. **Render Dashboard**: Monitor service status, logs, and metrics
2. **Health Endpoints**: Regularly check health endpoints
3. **Uptime Monitoring**: Consider using UptimeRobot for external monitoring

## 🔐 Security Notes

1. **Environment Variables**: Never commit secrets to git
2. **Database Access**: Use environment variables for all credentials
3. **CORS Configuration**: Ensure proper CORS settings
4. **SSL/TLS**: Render provides automatic HTTPS

## 🔄 Auto-Deployment

Both services are configured for automatic deployment:
- Push changes to `main` branch
- Render will automatically rebuild and deploy
- Zero-downtime deployment is enabled

## 📋 Service Summary

| Service | URL | Health Check | Context Path |
|---------|-----|--------------|--------------|
| User Management | https://aria-user-management.onrender.com | `/api/auth/actuator/health` | `/api/auth` |
| Interview Orchestrator | https://aria-interview-orchestrator.onrender.com | `/api/interview/actuator/health` | `/api/interview` |

## 🎉 Next Steps

After successful deployment:

1. Update your frontend configuration to use the new service URLs
2. Test inter-service communication
3. Set up monitoring and alerting
4. Configure custom domains if needed
5. Plan for scaling to paid tiers as needed

## 📞 Support

- **Render Documentation**: https://render.com/docs
- **Spring Boot Actuator**: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- **Docker Best Practices**: https://docs.docker.com/develop/dev-best-practices/
