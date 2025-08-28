#!/bin/bash

echo "🚀 ARIA Backend Deployment Instructions"
echo "======================================="
echo ""
echo "The backend code has been updated with CORS fixes but needs manual deployment."
echo ""
echo "📊 Current Status:"
curl -s -o /dev/null -w "Backend Health Check: %{http_code}\\n" https://aria-user-management-v2.onrender.com/api/auth/actuator/health

echo ""
echo "🔍 Testing CORS (should show 403 - Invalid CORS request):"
curl -X OPTIONS \
  -H "Origin: https://aria-frontend-fs01.onrender.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -s -o /dev/null -w "CORS Preflight Status: %{http_code}\\n" \
  https://aria-user-management-v2.onrender.com/api/auth/login

echo ""
echo "🚨 REQUIRED ACTION:"
echo "1. Go to: https://dashboard.render.com"
echo "2. Find service: aria-user-management-v2"
echo "3. Click 'Manual Deploy' button"
echo "4. Select 'Deploy latest commit'"
echo "5. Wait 5-10 minutes"
echo ""
echo "✅ After deployment, CORS will be fixed and frontend will work!"
echo ""
echo "🧪 Test after deployment:"
echo "   - Visit: https://aria-frontend-fs01.onrender.com"
echo "   - Try login/registration"
echo "   - Should work without CORS errors"
