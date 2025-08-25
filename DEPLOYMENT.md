# ARIA Deployment Guide

This guide walks you through deploying the ARIA interview platform across multiple cloud providers using our distributed architecture.

## 🏗️ Architecture Overview

### Platform Assignment
- **Render**: Frontend (Angular SSR) + 5 Python backend services
- **Railway**: 4 AI/ML services  
- **Supabase**: Primary PostgreSQL databases (2 x 500MB)
- **MongoDB Atlas**: Document storage (512MB)
- **Upstash**: Redis cache (<10K commands/day)
- **Cloudflare**: DNS, SSL, CDN
- **UptimeRobot**: Service monitoring (11 monitors)

## 📋 Prerequisites

1. **GitHub Repository**: Push your code to a GitHub repo
2. **Account Setup**:
   - [Render](https://render.com) account
   - [Railway](https://railway.app) account  
   - [Supabase](https://supabase.com) account
   - [MongoDB Atlas](https://www.mongodb.com/atlas) account
   - [Upstash](https://upstash.com) account
   - [Cloudflare](https://cloudflare.com) account
   - [UptimeRobot](https://uptimerobot.com) account

3. **Local Setup**:
   ```bash
   npm install -g @railway/cli
   ```

## 🚀 Step-by-Step Deployment

### 1. Database Setup

#### Supabase (Primary Database)
1. Create new project at [supabase.com](https://supabase.com)
2. Note your project URL and API keys
3. Run database migrations (if any)
4. Update `.env` with Supabase credentials

#### MongoDB Atlas (Document Store)
1. Create cluster at [mongodb.com/atlas](https://www.mongodb.com/atlas)
2. Create database user and get connection string
3. Whitelist IP addresses (0.0.0.0/0 for cloud deployment)
4. Update `.env` with MongoDB URI

#### Upstash (Redis Cache)
1. Create database at [upstash.com](https://upstash.com)
2. Get Redis URL and token
3. Update `.env` with Upstash credentials

### 2. Render Deployment

#### Connect GitHub Repository
1. Go to [render.com](https://render.com) dashboard
2. Click "New" → "Blueprint"
3. Connect your GitHub repository
4. Render will automatically detect `render.yaml`

#### Services Deployed on Render:
- ✅ `aria-frontend` (Angular SSR)
- ✅ `aria-speech-service` (Python)
- ✅ `aria-adaptive-engine` (Python)  
- ✅ `aria-analytics-service` (Python)
- ✅ `aria-test-orchestrator` (Python)
- ✅ `aria-test-service` (Python)

#### Configure Environment Variables:
Add these to each service in Render dashboard:
```env
# Copy values from .env.example
DATABASE_URL=your-supabase-connection-string
MONGODB_URI=your-mongodb-connection-string
UPSTASH_REDIS_URL=your-redis-url
# ... other required variables
```

### 3. Railway Deployment

#### Install Railway CLI:
```bash
npm install -g @railway/cli
railway login
```

#### Deploy AI/ML Services:

**AI Avatar Service:**
```bash
cd ai-services/ai-avatar-service
railway init
railway up
```

**Mozilla TTS Service:**
```bash
cd ai-services/mozilla-tts-service  
railway init
railway up
```

**Voice Isolation Service:**
```bash
cd ai-services/voice-isolation-service
railway init
railway up
```

**Voice Synthesis Service:**
```bash
cd ai-services/voice-synthesis-service
railway init
railway up
```

#### Configure Railway Environment Variables:
For each service, set:
```bash
railway variables set PORT=8006  # Adjust port for each service
railway variables set ENVIRONMENT=production
railway variables set SERVICE_NAME=service-name
```

### 4. Cloudflare Setup

#### DNS Configuration:
1. Add your domain to Cloudflare
2. Update nameservers with your domain provider
3. Create DNS records:
   ```
   Type: CNAME, Name: @, Target: aria-frontend.onrender.com
   Type: CNAME, Name: api, Target: aria-frontend.onrender.com
   ```

#### SSL/TLS Settings:
- Set SSL/TLS encryption mode to "Full (strict)"
- Enable "Always Use HTTPS"
- Enable HTTP/3 (with QUIC)

### 5. GitHub Actions Setup

#### Add Repository Secrets:
Go to GitHub repo → Settings → Secrets and variables → Actions

Add these secrets:
```
RENDER_API_KEY=your-render-api-key
RAILWAY_TOKEN=your-railway-token
```

#### Automatic Deployment:
- Push to `main` branch triggers deployment
- GitHub Actions will test and deploy all services
- Health checks verify deployment success

### 6. Monitoring Setup

#### UptimeRobot Configuration:
1. Create account at [uptimerobot.com](https://uptimerobot.com)
2. Add HTTP(S) monitors for:
   - `https://your-domain.com`
   - `https://aria-frontend.onrender.com/health`
   - `https://aria-speech-service.onrender.com/health`
   - `https://aria-adaptive-engine.onrender.com/health`
   - `https://aria-analytics-service.onrender.com/health`
   - `https://aria-test-orchestrator.onrender.com/health`
   - `https://aria-test-service.onrender.com/health`
   - Railway service URLs (update with actual URLs)

#### Configure Alerts:
- Email notifications for downtime
- Set check interval to 5 minutes
- Configure alert policies

## 🔧 Configuration

### Environment Variables

Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
# Edit .env with your actual values
```

### Health Checks

Each Python service should include health check endpoints:
- `/health` - Detailed health information
- `/ready` - Readiness probe  
- `/live` - Liveness probe

Add the health check template to your services:
```python
# See health_check.py template
```

## 📊 Service URLs

After deployment, your services will be available at:

### Frontend:
- **Production**: `https://your-domain.com`
- **Render**: `https://aria-frontend.onrender.com`

### Backend Services (Render):
- **Speech Service**: `https://aria-speech-service.onrender.com`
- **Adaptive Engine**: `https://aria-adaptive-engine.onrender.com`
- **Analytics**: `https://aria-analytics-service.onrender.com`
- **Test Orchestrator**: `https://aria-test-orchestrator.onrender.com`
- **Test Service**: `https://aria-test-service.onrender.com`

### AI/ML Services (Railway):
- **AI Avatar**: `https://your-ai-avatar-service.railway.app`
- **Mozilla TTS**: `https://your-mozilla-tts-service.railway.app`
- **Voice Isolation**: `https://your-voice-isolation-service.railway.app`
- **Voice Synthesis**: `https://your-voice-synthesis-service.railway.app`

## 🛠️ Troubleshooting

### Common Issues:

1. **Build Failures**:
   - Check `requirements.txt` files exist
   - Verify Python/Node.js versions
   - Check build logs in platform dashboards

2. **Service Not Starting**:
   - Verify environment variables
   - Check health check endpoints
   - Review application logs

3. **Database Connection Issues**:
   - Verify connection strings
   - Check database firewall settings
   - Ensure proper credentials

4. **Railway Deployment Issues**:
   - Verify Railway CLI authentication
   - Check service build logs
   - Ensure proper `railway.json` configuration

## 📈 Scaling

### Free Tier Limits:
- **Render**: 512MB RAM, sleeps after 15min inactivity
- **Railway**: $5 monthly credit, 512MB RAM per service
- **Supabase**: 2 x 500MB databases
- **MongoDB Atlas**: 512MB storage
- **Upstash**: 10K commands/day

### Optimization Tips:
- Use connection pooling for databases
- Implement proper caching strategies
- Optimize Docker images
- Monitor resource usage
- Use health checks for faster recovery

## 🔐 Security

### Best Practices:
- Use environment variables for secrets
- Enable HTTPS everywhere
- Implement proper authentication
- Regular security updates
- Monitor for vulnerabilities

### Secrets Management:
- Store secrets in platform-specific secret managers
- Never commit secrets to git
- Use different secrets for different environments
- Rotate secrets regularly

## 📞 Support

If you encounter issues:
1. Check service logs in respective dashboards
2. Verify health check endpoints
3. Review GitHub Actions workflow logs
4. Check UptimeRobot monitoring alerts

---

**🎉 Congratulations! Your ARIA platform is now deployed and running on multiple cloud providers with full monitoring and CI/CD automation.**
