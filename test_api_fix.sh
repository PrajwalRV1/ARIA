#!/bin/bash

echo "🧪 Testing API fix deployment..."
echo "================================"

# Test the candidates endpoint
echo "📋 Testing /candidates endpoint..."
response=$(curl -s -w "HTTP_STATUS:%{http_code}" \
  -H "Accept: application/json" \
  -H "Origin: https://aria-frontend-fs01.onrender.com" \
  "https://aria-user-management-v2-uq1g.onrender.com/api/auth/candidates")

http_status=$(echo "$response" | grep -o 'HTTP_STATUS:[0-9]*' | cut -d: -f2)
response_body=$(echo "$response" | sed 's/HTTP_STATUS:[0-9]*$//')

echo "🔍 HTTP Status: $http_status"

if [ "$http_status" = "200" ]; then
    echo "✅ SUCCESS: API is working!"
    echo "📄 Response: $response_body"
    
    # Check if it's an empty array (expected for new recruiter)
    if [ "$response_body" = "[]" ]; then
        echo "🎯 PERFECT: Empty candidates array returned (expected for new account)"
    else
        echo "📊 Candidates found in database"
    fi
else
    echo "❌ STILL FAILING: Status $http_status"
    echo "📄 Error Response: $response_body"
    echo ""
    echo "⚠️  Backend deployment still needed!"
fi

echo ""
echo "🔄 Next Steps:"
echo "1. If status is 500 → Backend needs redeployment" 
echo "2. If status is 200 → Fix is working!"
echo "3. Refresh your browser and clear cache"
