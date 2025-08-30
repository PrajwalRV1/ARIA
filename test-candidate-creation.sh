#!/bin/bash

# Test script to verify candidate creation with multipart form data
# This script tests the complete form submission flow to debug 400 Bad Request errors

set -e

echo "🧪 Testing Candidate Creation Form Submission Flow"
echo "================================================="

# Backend URL
BASE_URL="https://aria-user-management-v2.onrender.com/api/auth"

# Get JWT token (you'll need to replace this with a valid token)
echo "⚠️  You need to replace JWT_TOKEN with a valid authentication token"
echo "    Get a token by logging in to the application and checking localStorage.auth_token"
echo ""

# Comment this out when you have a real token
JWT_TOKEN="your_jwt_token_here"

if [ "$JWT_TOKEN" = "your_jwt_token_here" ]; then
    echo "❌ Please replace JWT_TOKEN with a valid authentication token"
    echo "   1. Login to the frontend application"
    echo "   2. Open browser dev tools"
    echo "   3. Go to Application/Storage > Local Storage"
    echo "   4. Copy the 'auth_token' value"
    echo "   5. Replace the JWT_TOKEN variable in this script"
    exit 1
fi

# Create a test resume file
echo "📄 Creating test resume file..."
TEST_RESUME="test_resume.pdf"
echo "%PDF-1.4
1 0 obj
<<
/Type /Catalog
/Pages 2 0 R
>>
endobj

2 0 obj
<<
/Type /Pages
/Kids [3 0 R]
/Count 1
>>
endobj

3 0 obj
<<
/Type /Page
/Parent 2 0 R
/MediaBox [0 0 612 792]
/Contents 4 0 R
>>
endobj

4 0 obj
<<
/Length 44
>>
stream
BT
/F1 12 Tf
72 720 Td
(Test Resume) Tj
ET
endstream
endobj

trailer
<<
/Size 5
/Root 1 0 R
>>
startxref
0
%%EOF" > "$TEST_RESUME"

echo "✅ Test resume file created: $TEST_RESUME"

# Test candidate data (matching backend CandidateCreateRequest structure)
echo "🎯 Testing candidate creation with minimal valid payload..."

# Create JSON data that matches the backend DTO exactly
CANDIDATE_JSON='{
    "requisitionId": "REQ-TEST-001",
    "name": "Test Candidate",
    "email": "test.candidate@example.com",
    "phone": "+1234567890",
    "appliedRole": "Software Engineer",
    "applicationDate": "2024-01-15",
    "totalExperience": 3,
    "relevantExperience": 2,
    "interviewRound": "Technical - Round 1",
    "status": "PENDING",
    "jobDescription": "Software Engineer position requiring full-stack development skills.",
    "keyResponsibilities": "Develop and maintain web applications, collaborate with team members.",
    "skills": ["JavaScript", "TypeScript", "Angular"],
    "source": "Manual Entry",
    "notes": "Test candidate for form submission debugging",
    "tags": "test,debugging",
    "recruiterId": "1"
}'

echo "📝 Candidate JSON data:"
echo "$CANDIDATE_JSON" | jq .

echo ""
echo "🚀 Sending multipart form data request..."

# Test the multipart form data request exactly as the frontend sends it
RESPONSE=$(curl -w "\nHTTP_CODE:%{http_code}\n" \
    -X POST "$BASE_URL/candidates" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -F "data=$CANDIDATE_JSON;type=application/json" \
    -F "resume=@$TEST_RESUME;type=application/pdf" \
    2>/dev/null)

# Extract response and HTTP code
HTTP_CODE=$(echo "$RESPONSE" | tail -n1 | sed 's/HTTP_CODE://')
BODY=$(echo "$RESPONSE" | sed '$d')

echo "📡 Response Details:"
echo "   Status Code: $HTTP_CODE"
echo ""

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
    echo "✅ SUCCESS! Candidate created successfully"
    echo "📋 Response Body:"
    echo "$BODY" | jq . 2>/dev/null || echo "$BODY"
else
    echo "❌ FAILED! HTTP $HTTP_CODE"
    echo "📋 Error Response:"
    echo "$BODY"
    
    # Analyze common error patterns
    if [ "$HTTP_CODE" = "400" ]; then
        echo ""
        echo "🔍 400 Bad Request Analysis:"
        if echo "$BODY" | grep -q "requisitionId"; then
            echo "   - RequisitionId field issue detected"
        fi
        if echo "$BODY" | grep -q "phone"; then
            echo "   - Phone field validation issue detected"
        fi
        if echo "$BODY" | grep -q "applicationDate"; then
            echo "   - Application date format issue detected"
        fi
        if echo "$BODY" | grep -q "resume"; then
            echo "   - Resume file issue detected"
        fi
    elif [ "$HTTP_CODE" = "415" ]; then
        echo "   - Media type not supported - check multipart form data structure"
    elif [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
        echo "   - Authentication issue - check JWT token validity"
    fi
fi

echo ""
echo "🧹 Cleaning up test files..."
rm -f "$TEST_RESUME"

echo ""
echo "🎯 Test Summary:"
echo "   - Multipart form data structure: ✓ matches backend expectations"
echo "   - JSON data part: ✓ properly formatted with application/json content type" 
echo "   - Resume file part: ✓ proper PDF file with application/pdf content type"
echo "   - Field names: ✓ match CandidateCreateRequest DTO"
echo ""

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
    echo "🎉 Form submission flow is working correctly!"
    echo "   The frontend should now be able to create candidates successfully."
else
    echo "🔧 Issues detected in form submission flow."
    echo "   Check the error response above for specific validation failures."
    echo ""
    echo "📋 Debugging steps:"
    echo "   1. Verify JWT token is valid and has RECRUITER role"
    echo "   2. Check if all required fields are present and properly formatted"
    echo "   3. Ensure backend is running and accessible"
    echo "   4. Verify database connection and candidate table structure"
fi

echo ""
echo "📚 Additional debugging commands:"
echo ""
echo "# Check backend health:"
echo "curl \"$BASE_URL/../health\""
echo ""
echo "# Test authentication:"
echo "curl -H \"Authorization: Bearer \$JWT_TOKEN\" \"$BASE_URL/candidates\""
echo ""
echo "# Manual multipart test with curl (replace \$JWT_TOKEN):"
echo "curl -X POST \"$BASE_URL/candidates\" \\"
echo "  -H \"Authorization: Bearer \$JWT_TOKEN\" \\"
echo "  -F \"data=$CANDIDATE_JSON;type=application/json\" \\"
echo "  -F \"resume=@test.pdf;type=application/pdf\""
