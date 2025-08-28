#!/bin/bash

echo "🧪 Testing CORS Fix Status"
echo "=========================="
echo ""

# Test basic health check
echo "1. Backend Health Check:"
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" https://aria-user-management-v2.onrender.com/api/auth/actuator/health)
if [ "$HEALTH_STATUS" = "200" ]; then
    echo "   ✅ Backend is running (HTTP $HEALTH_STATUS)"
else
    echo "   ❌ Backend issue (HTTP $HEALTH_STATUS)"
fi

echo ""

# Test CORS preflight
echo "2. CORS Preflight Test:"
CORS_STATUS=$(curl -X OPTIONS \
  -H "Origin: https://aria-frontend-fs01.onrender.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -s -o /dev/null -w "%{http_code}" \
  https://aria-user-management-v2.onrender.com/api/auth/login)

if [ "$CORS_STATUS" = "200" ]; then
    echo "   ✅ CORS is working (HTTP $CORS_STATUS)"
    echo "   🎉 Frontend should now work properly!"
elif [ "$CORS_STATUS" = "403" ]; then
    echo "   ❌ CORS still blocked (HTTP $CORS_STATUS)"
    echo "   ⏳ Backend deployment may still be in progress"
    echo "   📝 Or manual deployment is still required"
else
    echo "   ⚠️  Unexpected status (HTTP $CORS_STATUS)"
fi

echo ""

# Check for CORS headers in response
echo "3. Detailed CORS Response:"
curl -X OPTIONS \
  -H "Origin: https://aria-frontend-fs01.onrender.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -s -I https://aria-user-management-v2.onrender.com/api/auth/login | grep -i "access-control"

echo ""
echo "📋 Status Summary:"
if [ "$CORS_STATUS" = "200" ]; then
    echo "   ✅ CORS fix deployed successfully"
    echo "   ✅ Frontend integration should work"
    echo "   🌐 Test at: https://aria-frontend-fs01.onrender.com"
else
    echo "   ❌ CORS fix not yet active"
    echo "   🚨 Manual deployment required at: https://dashboard.render.com"
    echo "   📖 Service name: aria-user-management-v2"
fi
