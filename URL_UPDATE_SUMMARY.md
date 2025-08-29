# Service URL Update Summary

## Updated Configuration Files:
1. `render.yaml` - Backend service environment variables
2. `frontend/src/environments/environment.prod.ts` - Production frontend config
3. `frontend/src/environments/environment.render.ts` - Render-specific frontend config

## New Service URLs (Render Platform):

### Frontend Service:
- **Old**: https://aria-frontend-fs01.onrender.com
- **New**: https://aria-frontend-tc4z.onrender.com

### User Management Service:
- **Old**: https://aria-user-management-v2.onrender.com  
- **New**: https://aria-user-management-v2-uq1g.onrender.com

### Interview Orchestrator Service:
- **Old**: https://aria-interview-orchestrator-v2.onrender.com
- **New**: https://aria-interview-orchestrator-v2-sppr.onrender.com

### Speech Service:
- **Old**: https://aria-speech-service.onrender.com
- **New**: https://aria-speech-service-l4cl.onrender.com

### Adaptive Engine Service:
- **Old**: https://aria-adaptive-engine.onrender.com
- **New**: https://aria-adaptive-engine-ntsr.onrender.com

### Analytics Service:
- **Old**: https://aria-analytics-service.onrender.com
- **New**: https://aria-analytics-service-betb.onrender.com

## New Service URLs (Railway Platform):

### AI Avatar Service:
- **New**: https://ai-avatar-service-production.up.railway.app

### Mozilla TTS Service:
- **New**: https://mozilla-tts-service-production.up.railway.app

### Voice Synthesis Service:
- **New**: https://voice-synthesis-service-production.up.railway.app

### Voice Isolation Service:
- **New**: https://voice-isolation-service-production.up.railway.app

## Changes Made:

### In render.yaml:
- Updated CORS_ORIGINS for both Spring Boot services
- Updated USER_MGMT_URL in interview orchestrator service

### In Frontend Environment Files:
- Updated all API base URLs
- Updated all WebSocket URLs  
- Updated all Railway service URLs
- Updated security allowed origins

## Next Steps:
1. Commit and push these changes
2. All services will automatically redeploy with new URLs
3. Test the complete system functionality
