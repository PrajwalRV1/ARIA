# Update Service URLs After Migration

After deploying to your new Render account, update these configurations:

## 1. Update Environment Variables in Render Dashboard

For **aria-user-management-v2-new**:
- CORS_ORIGINS: `https://your-new-frontend-url.onrender.com`

For **aria-interview-orchestrator-v2-new**:
- CORS_ORIGINS: `https://your-new-frontend-url.onrender.com`  
- USER_MGMT_URL: `https://your-new-user-mgmt-url.onrender.com`

## 2. Update Frontend API Endpoints

Check these files for hardcoded API URLs:
- `frontend/src/environments/environment.prod.ts`
- `frontend/src/app/services/*.service.ts`

Replace old onrender.com URLs with new service URLs.

## 3. Update render.yaml (for future deployments)

Update the CORS_ORIGINS and USER_MGMT_URL values in render.yaml:

```yaml
envVars:
  - key: CORS_ORIGINS
    value: "https://your-new-frontend-url.onrender.com"
  - key: USER_MGMT_URL  
    value: "https://your-new-user-mgmt-url.onrender.com"
```

## 4. Verify Health Endpoints

Test these URLs in browser:
- https://your-new-user-mgmt-url.onrender.com/api/auth/actuator/health
- https://your-new-orchestrator-url.onrender.com/api/interview/actuator/health
- https://your-new-frontend-url.onrender.com

## 5. Test Complete Flow

1. Access frontend URL
2. Try user registration/login
3. Test interview flow
4. Check all services communicate properly
