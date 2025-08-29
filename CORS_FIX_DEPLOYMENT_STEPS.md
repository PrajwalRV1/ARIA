# CORS Fix Deployment Steps

## 🔴 **CORS Issue Identified**

**Error**: `Access to XMLHttpRequest at 'https://aria-user-management-v2.onrender.com/api/auth/send-otp' from origin 'https://aria-frontend-fs01.onrender.com' has been blocked by CORS policy`

**Root Cause**: Backend CORS configuration was set to allow `aria-frontend.onrender.com` but the actual frontend domain is `aria-frontend-fs01.onrender.com`

## ✅ **Changes Made and Pushed**

### Files Updated:
1. **`backend/user-management-service/render.yaml`**:
   ```yaml
   - key: CORS_ORIGINS
     value: https://aria-frontend-fs01.onrender.com  # Updated from aria-frontend.onrender.com
   ```

2. **`render.yaml`** (Interview Orchestrator Service):
   ```yaml
   - key: CORS_ORIGINS
     value: "https://aria-frontend-fs01.onrender.com"  # Updated for consistency
   ```

3. **`frontend/src/environments/environment.ts`**:
   ```typescript
   allowedOrigins: [..., 'https://aria-frontend-fs01.onrender.com']  # Updated to match
   ```

### Git Status:
- ✅ **Committed**: Changes committed to main branch (commit: `4a32c81`)
- ✅ **Pushed**: Changes pushed to GitHub repository

## 🚨 **Required Manual Steps**

Since the user management service doesn't have `autoDeploy: true`, you need to manually trigger a deployment:

### Option 1: Render Dashboard (Recommended)
1. Go to [Render Dashboard](https://dashboard.render.com)
2. Find the `aria-user-management-v2` service
3. Click on the service name
4. Click **"Manual Deploy"** button
5. Select **"Clear build cache & deploy"** or **"Deploy latest commit"**
6. Wait for deployment to complete (5-10 minutes)

### Option 2: Alternative - Enable Auto Deploy (Future)
If you want automatic deployments, update the render.yaml:
```yaml
# In backend/user-management-service/render.yaml
autoDeploy: true  # Add this line
```

## 🔍 **Verification Steps**

After the backend redeploys:

1. **Check Service Status**:
   - Go to `https://aria-user-management-v2.onrender.com/api/auth/actuator/health`
   - Should return `{"status":"UP"}`

2. **Test CORS**:
   - Open browser DevTools on `https://aria-frontend-fs01.onrender.com`
   - Try the authentication flow (login/register/send OTP)
   - Check console for CORS errors - should be resolved

3. **Verify Environment Variables**:
   - In Render dashboard → Service → Environment
   - Confirm `CORS_ORIGINS=https://aria-frontend-fs01.onrender.com`

## 🎯 **Expected Results**

After manual deployment:
- ✅ CORS preflight requests will pass
- ✅ API calls from frontend to backend will work
- ✅ Authentication flow will be functional
- ✅ No more "Access-Control-Allow-Origin" errors

## ⏱️ **Timeline**

- **Manual Deploy**: ~5-10 minutes
- **Service Restart**: ~2-3 minutes
- **CORS Fix Active**: Immediately after restart

## 📋 **Next Actions**

1. **Immediately**: Go to Render Dashboard and manually deploy `aria-user-management-v2`
2. **After Deploy**: Test the authentication flow on your frontend
3. **Verify**: Check browser console for any remaining CORS errors
4. **Monitor**: Ensure all API endpoints are working correctly

## 🆘 **If Issues Persist**

If CORS errors continue after deployment:
1. Check that the service actually restarted with new environment variables
2. Verify the exact frontend domain in browser network tab
3. Check if there are any caching issues (try incognito mode)
4. Review backend logs for CORS configuration loading

---

**Status**: ⏳ **AWAITING MANUAL DEPLOYMENT**
**Action Required**: Manual deploy of `aria-user-management-v2` service
