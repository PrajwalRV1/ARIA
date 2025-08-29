# CORS 403 Error Debug Guide

## ✅ What We've Verified:
1. **Backend services are running** - Both user-management and orchestrator are UP
2. **CORS is properly configured** - Backend allows `https://aria-frontend-tc4z.onrender.com`
3. **API endpoint works** - `/api/auth/send-otp` returns 200 OK
4. **OPTIONS preflight works** - Preflight request succeeds

## 🔍 Likely Causes:

### 1. Frontend URL Mismatch
**Issue**: You're accessing the frontend from a different URL than configured in CORS.

**Check**:
- Are you accessing: `https://aria-frontend-tc4z.onrender.com`? ✅
- Or are you using: `localhost:4200` or a different URL? ❌

**Solution**: Only access frontend via: `https://aria-frontend-tc4z.onrender.com`

### 2. Browser Cache
**Issue**: Browser might be caching old API responses or CORS failures.

**Solution**:
```bash
# Clear browser cache completely
# Or open in incognito/private mode
```

### 3. Frontend Not Updated
**Issue**: Frontend might still be using old API URLs.

**Check in Browser DevTools**:
1. Open Network tab
2. Look at the failed request
3. Check the request URL - should be: `https://aria-user-management-v2-uq1g.onrender.com/api/auth/send-otp`

## 🛠️ Quick Debug Steps:

### Step 1: Check Request URL
In browser DevTools → Network tab:
- Is the request going to: `https://aria-user-management-v2-uq1g.onrender.com/api/auth/send-otp`?
- Or is it going to an old URL?

### Step 2: Check Origin Header
In the failed request headers:
- Is Origin: `https://aria-frontend-tc4z.onrender.com`?
- Or is it something like `http://localhost:4200`?

### Step 3: Check Response Headers
In the response (even if 403):
- Look for `access-control-allow-origin` header
- Does it match your frontend URL?

## 🔧 Fixes:

### Fix 1: Access Correct Frontend URL
Make sure you're accessing:
```
https://aria-frontend-tc4z.onrender.com
```

### Fix 2: Hard Refresh
```
Ctrl/Cmd + Shift + R (hard refresh)
OR
Open in incognito/private browsing mode
```

### Fix 3: Wait for Frontend Deployment
The frontend might still be deploying with new configuration:
- Check Render dashboard for `aria-frontend` service status
- Should show "Live" status

## 📝 Test Commands:
```bash
# Test API directly (should work)
curl -X POST https://aria-user-management-v2-uq1g.onrender.com/api/auth/send-otp \
  -H "Content-Type: application/json" \
  -H "Origin: https://aria-frontend-tc4z.onrender.com" \
  -d '{"email": "test@example.com"}'

# Should return: {"message":"OTP sent successfully","success":true}
```

## 🚨 What to Check Right Now:
1. **Are you accessing the correct frontend URL?**
2. **Is the frontend service "Live" in Render dashboard?**
3. **Try opening in incognito mode**
4. **Check Network tab in DevTools for actual request details**
