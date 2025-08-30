#!/bin/bash

# Test script to verify candidate status transition fix
# This script tests the INTERVIEW_SCHEDULED → COMPLETED transition that was previously failing

set -e

# Configuration
BACKEND_URL="https://aria-user-management-v2.onrender.com/api"
AUTH_URL="$BACKEND_URL/auth"
CANDIDATE_URL="$AUTH_URL/candidates"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 Testing Candidate Status Transition Fix${NC}"
echo -e "${BLUE}=================================================${NC}"
echo ""

# Step 1: Check backend health
echo -e "${YELLOW}Step 1: Checking backend health...${NC}"
HEALTH_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_URL/health" || echo "000")

if [ "$HEALTH_RESPONSE" != "200" ]; then
    echo -e "${RED}❌ Backend is not healthy (HTTP $HEALTH_RESPONSE)${NC}"
    echo -e "${YELLOW}ℹ️ The backend may still be deploying. Please wait and retry.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Backend is healthy${NC}"
echo ""

# Step 2: Create a test candidate with INTERVIEW_SCHEDULED status
echo -e "${YELLOW}Step 2: Creating test candidate with INTERVIEW_SCHEDULED status...${NC}"

TEST_CANDIDATE_DATA='{
  "requisitionId": "TEST-REQ-001",
  "name": "Test Candidate Status Transition",
  "email": "test.status.transition@example.com",
  "phone": "+1234567890",
  "appliedRole": "Software Engineer",
  "applicationDate": "2024-08-30",
  "totalExperience": 3.0,
  "relevantExperience": 2.5,
  "interviewRound": "Technical Round",
  "status": "INTERVIEW_SCHEDULED",
  "jobDescription": "Test role for status transition validation",
  "keyResponsibilities": "Validate status transitions work correctly",
  "skills": ["Java", "Spring Boot", "Testing"],
  "source": "Automated Test",
  "notes": "Created by test script to validate INTERVIEW_SCHEDULED → COMPLETED transition",
  "tags": "test,status-transition,interview-scheduled",
  "recruiterId": "test-recruiter-001"
}'

echo "Creating candidate..."
CREATE_RESPONSE=$(curl -s -X POST "$CANDIDATE_URL" \
  -H "Content-Type: application/json" \
  -d "$TEST_CANDIDATE_DATA" \
  -w "\nHTTP_CODE:%{http_code}")

# Extract HTTP code
HTTP_CODE=$(echo "$CREATE_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
RESPONSE_BODY=$(echo "$CREATE_RESPONSE" | sed '/HTTP_CODE:/d')

if [ "$HTTP_CODE" != "201" ]; then
    echo -e "${RED}❌ Failed to create candidate (HTTP $HTTP_CODE)${NC}"
    echo "Response: $RESPONSE_BODY"
    exit 1
fi

# Extract candidate ID
CANDIDATE_ID=$(echo "$RESPONSE_BODY" | grep -o '"id":[0-9]*' | cut -d: -f2)
if [ -z "$CANDIDATE_ID" ]; then
    echo -e "${RED}❌ Could not extract candidate ID from response${NC}"
    echo "Response: $RESPONSE_BODY"
    exit 1
fi

echo -e "${GREEN}✅ Created candidate with ID: $CANDIDATE_ID${NC}"
echo -e "${GREEN}✅ Initial status: INTERVIEW_SCHEDULED${NC}"
echo ""

# Step 3: Verify candidate was created with correct status
echo -e "${YELLOW}Step 3: Verifying candidate creation...${NC}"

GET_RESPONSE=$(curl -s "$CANDIDATE_URL/$CANDIDATE_ID" -w "\nHTTP_CODE:%{http_code}")
GET_HTTP_CODE=$(echo "$GET_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
GET_RESPONSE_BODY=$(echo "$GET_RESPONSE" | sed '/HTTP_CODE:/d')

if [ "$GET_HTTP_CODE" != "200" ]; then
    echo -e "${RED}❌ Failed to retrieve candidate (HTTP $GET_HTTP_CODE)${NC}"
    echo "Response: $GET_RESPONSE_BODY"
    exit 1
fi

CURRENT_STATUS=$(echo "$GET_RESPONSE_BODY" | grep -o '"status":"[^"]*' | cut -d: -f2 | tr -d '"')
if [ "$CURRENT_STATUS" != "INTERVIEW_SCHEDULED" ]; then
    echo -e "${RED}❌ Candidate status is '$CURRENT_STATUS', expected 'INTERVIEW_SCHEDULED'${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Candidate retrieved successfully${NC}"
echo -e "${GREEN}✅ Current status confirmed: $CURRENT_STATUS${NC}"

# Check next possible statuses
NEXT_STATUSES=$(echo "$GET_RESPONSE_BODY" | grep -o '"nextPossibleStatuses":\[[^]]*\]' | sed 's/.*:\[//' | sed 's/\]//')
echo -e "${BLUE}ℹ️ Next possible statuses: $NEXT_STATUSES${NC}"

# Verify COMPLETED is in the list
if [[ "$NEXT_STATUSES" == *"COMPLETED"* ]]; then
    echo -e "${GREEN}✅ COMPLETED is listed as a valid next status${NC}"
else
    echo -e "${RED}❌ COMPLETED is NOT listed as a valid next status${NC}"
    echo -e "${RED}   This indicates the backend fix may not be working${NC}"
fi
echo ""

# Step 4: Test the status transition from INTERVIEW_SCHEDULED to COMPLETED
echo -e "${YELLOW}Step 4: Testing INTERVIEW_SCHEDULED → COMPLETED transition...${NC}"

UPDATE_DATA='{
  "requisitionId": "TEST-REQ-001",
  "name": "Test Candidate Status Transition",
  "email": "test.status.transition@example.com",
  "phone": "+1234567890",
  "appliedRole": "Software Engineer",
  "applicationDate": "2024-08-30",
  "totalExperience": 3.0,
  "relevantExperience": 2.5,
  "interviewRound": "Technical Round - Completed",
  "status": "COMPLETED",
  "jobDescription": "Test role for status transition validation",
  "keyResponsibilities": "Validate status transitions work correctly",
  "skills": ["Java", "Spring Boot", "Testing"],
  "source": "Automated Test",
  "notes": "Updated by test script - interview completed successfully",
  "tags": "test,status-transition,completed",
  "recruiterId": "test-recruiter-001"
}'

echo "Attempting status update to COMPLETED..."
UPDATE_RESPONSE=$(curl -s -X PUT "$CANDIDATE_URL/$CANDIDATE_ID" \
  -H "Content-Type: application/json" \
  -d "$UPDATE_DATA" \
  -w "\nHTTP_CODE:%{http_code}")

# Extract HTTP code
UPDATE_HTTP_CODE=$(echo "$UPDATE_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
UPDATE_RESPONSE_BODY=$(echo "$UPDATE_RESPONSE" | sed '/HTTP_CODE:/d')

echo ""
if [ "$UPDATE_HTTP_CODE" == "200" ]; then
    echo -e "${GREEN}✅ SUCCESS! Status transition worked (HTTP $UPDATE_HTTP_CODE)${NC}"
    
    # Verify the status was actually updated
    UPDATED_STATUS=$(echo "$UPDATE_RESPONSE_BODY" | grep -o '"status":"[^"]*' | cut -d: -f2 | tr -d '"')
    if [ "$UPDATED_STATUS" == "COMPLETED" ]; then
        echo -e "${GREEN}✅ Status confirmed as COMPLETED${NC}"
    else
        echo -e "${YELLOW}⚠️ Status update returned success but status is '$UPDATED_STATUS'${NC}"
    fi
else
    echo -e "${RED}❌ FAILED! Status transition failed (HTTP $UPDATE_HTTP_CODE)${NC}"
    echo -e "${RED}Response: $UPDATE_RESPONSE_BODY${NC}"
    
    # Check if this is the old validation error
    if [[ "$UPDATE_RESPONSE_BODY" == *"Invalid status transition"* ]]; then
        echo -e "${RED}   This is the old validation error - the fix may not be deployed yet${NC}"
    fi
fi

echo ""

# Step 5: Final verification - retrieve candidate again
echo -e "${YELLOW}Step 5: Final verification...${NC}"

FINAL_GET_RESPONSE=$(curl -s "$CANDIDATE_URL/$CANDIDATE_ID" -w "\nHTTP_CODE:%{http_code}")
FINAL_HTTP_CODE=$(echo "$FINAL_GET_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
FINAL_RESPONSE_BODY=$(echo "$FINAL_GET_RESPONSE" | sed '/HTTP_CODE:/d')

if [ "$FINAL_HTTP_CODE" == "200" ]; then
    FINAL_STATUS=$(echo "$FINAL_RESPONSE_BODY" | grep -o '"status":"[^"]*' | cut -d: -f2 | tr -d '"')
    echo -e "${BLUE}Final candidate status: $FINAL_STATUS${NC}"
    
    if [ "$FINAL_STATUS" == "COMPLETED" ]; then
        echo -e "${GREEN}✅ FINAL VERIFICATION PASSED${NC}"
        echo -e "${GREEN}✅ Candidate status successfully updated to COMPLETED${NC}"
    else
        echo -e "${RED}❌ FINAL VERIFICATION FAILED${NC}"
        echo -e "${RED}   Expected: COMPLETED, Actual: $FINAL_STATUS${NC}"
    fi
else
    echo -e "${RED}❌ Failed to retrieve candidate for final verification${NC}"
fi

echo ""

# Step 6: Cleanup - delete test candidate
echo -e "${YELLOW}Step 6: Cleaning up test candidate...${NC}"

DELETE_RESPONSE=$(curl -s -X DELETE "$CANDIDATE_URL/$CANDIDATE_ID" -w "\nHTTP_CODE:%{http_code}")
DELETE_HTTP_CODE=$(echo "$DELETE_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)

if [ "$DELETE_HTTP_CODE" == "204" ] || [ "$DELETE_HTTP_CODE" == "200" ]; then
    echo -e "${GREEN}✅ Test candidate cleaned up${NC}"
else
    echo -e "${YELLOW}⚠️ Failed to delete test candidate (HTTP $DELETE_HTTP_CODE)${NC}"
    echo -e "${YELLOW}   You may need to manually delete candidate ID: $CANDIDATE_ID${NC}"
fi

echo ""
echo -e "${BLUE}=================================================${NC}"
echo -e "${BLUE}🏁 Test Complete${NC}"
echo ""

# Summary
if [ "$UPDATE_HTTP_CODE" == "200" ] && [ "$FINAL_STATUS" == "COMPLETED" ]; then
    echo -e "${GREEN}🎉 SUCCESS: INTERVIEW_SCHEDULED → COMPLETED transition is working!${NC}"
    echo -e "${GREEN}   The status transition fix has been successfully deployed.${NC}"
    exit 0
else
    echo -e "${RED}❌ FAILURE: INTERVIEW_SCHEDULED → COMPLETED transition is not working${NC}"
    echo -e "${RED}   The fix may need more time to deploy or there may be an issue.${NC}"
    exit 1
fi
