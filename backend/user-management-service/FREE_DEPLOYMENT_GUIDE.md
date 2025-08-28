# 🚀 Free Deployment Guide for User Management Service

This guide covers completely FREE alternatives to deploy your Spring Boot user-management-service.

## 🏆 Top Recommended Platforms

### 1. Railway.app (BEST OPTION)
**Free Tier:** $5 worth of resources monthly (very generous)
**Perfect for:** Spring Boot + PostgreSQL + Redis

#### Setup Steps:
1. Visit [railway.app](https://railway.app)
2. Sign up with GitHub
3. Create new project → "Deploy from GitHub repo"
4. Select your repository → `backend/user-management-service`
5. Railway will auto-detect Spring Boot and build

#### Environment Variables to Set:
```bash
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=your-jwt-secret
MAIL_USERNAME=your-email
MAIL_PASSWORD=your-password
CORS_ORIGINS=https://aria-frontend.onrender.com
```

#### Add Database:
1. In Railway dashboard → "New Service" → PostgreSQL
2. DATABASE_URL will be auto-provided

#### Add Redis:
1. In Railway dashboard → "New Service" → Redis  
2. REDIS_URL will be auto-provided

---

### 2. Fly.io (Great Alternative)
**Free Tier:** 3 shared-CPU VMs, 160GB bandwidth/month
**Perfect for:** Docker-based deployment

#### Setup Steps:
1. Install Fly CLI: `curl -L https://fly.io/install.sh | sh`
2. Create account: `fly auth signup`
3. Navigate to service directory: `cd backend/user-management-service`
4. Launch app: `fly launch --no-deploy`
5. Edit the generated `fly.toml` (already provided)
6. Deploy: `fly deploy`

#### Add Database:
```bash
fly postgres create --name aria-db
fly postgres attach --app aria-user-management aria-db
```

#### Add Redis:
```bash
fly redis create --name aria-redis
fly redis attach --app aria-user-management aria-redis
```

---

### 3. Other Free Options

#### Cyclic.sh
- **Pros:** Unlimited free tier, easy deployment
- **Cons:** Primarily MongoDB (would need database migration)
- **Setup:** Connect GitHub repo, auto-deploys

#### Deta Space  
- **Pros:** Completely free, backed by VC
- **Cons:** Less mature, limited database options
- **Setup:** CLI-based deployment with Deta CLI

---

## 🛠 Quick Start with Railway (Recommended)

### Step 1: Prepare Repository
The configuration files are already created:
- ✅ `railway.json` - Railway deployment config
- ✅ `application-railway.properties` - Railway-specific settings

### Step 2: Deploy
1. **Create Railway Account:** [railway.app](https://railway.app)
2. **New Project:** "Deploy from GitHub repo"  
3. **Select Repo:** Choose your ARIA repository
4. **Select Service:** `backend/user-management-service`
5. **Auto-Deploy:** Railway detects Spring Boot automatically

### Step 3: Add Services
```bash
# In Railway Dashboard:
1. Click "+ New Service"
2. Select "Database" → "PostgreSQL" 
3. Click "+ New Service"  
4. Select "Database" → "Redis"
```

### Step 4: Environment Variables
In Railway dashboard, set these variables:
```
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN
MAIL_USERNAME=workwithrvprajwal@gmail.com  
MAIL_PASSWORD=vclhowpwtmopdqdz
CORS_ORIGINS=https://aria-frontend.onrender.com
```

### Step 5: Test Deployment
```bash
curl https://your-app.railway.app/api/auth/actuator/health
```

---

## 💰 Cost Comparison

| Platform | Free Tier | Database | Redis | Custom Domain | Sleep Mode |
|----------|-----------|----------|--------|---------------|------------|
| **Railway** | $5/month credits | ✅ PostgreSQL | ✅ Redis | ✅ Yes | ❌ No |
| **Fly.io** | 3 VMs, 160GB/month | ✅ PostgreSQL | ✅ Redis | ✅ Yes | ✅ Auto-sleep |
| **Cyclic** | Unlimited | ❌ MongoDB only | ❌ No | ✅ Yes | ❌ No |
| **Deta** | Unlimited | ❌ Deta Base | ❌ No | ✅ Yes | ❌ No |

---

## 🔧 Configuration Files Included

### Railway Configuration
- `railway.json` - Deployment settings
- `application-railway.properties` - Railway-specific config

### Fly.io Configuration  
- `fly.toml` - Fly deployment config
- `application-flyio.properties` - Fly.io-specific config

---

## 🚦 Next Steps

1. **Choose Platform:** Railway is recommended for easiest setup
2. **Test Deployment:** Start with Railway's free tier
3. **Configure Database:** Add PostgreSQL addon
4. **Add Redis:** Add Redis addon for caching
5. **Set Environment Variables:** Configure secrets properly
6. **Custom Domain:** Point your domain to the deployment

The user-management-service is now ready to deploy on completely free platforms! 🎉
