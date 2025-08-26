# 🚀 Deploy ARIA User Management Service to AWS NOW

## ✅ **READY FOR DEPLOYMENT**

Your user-management-service is now **AWS deployment-ready** with:
- ✅ AWS-optimized application configuration (`application-aws.properties`)
- ✅ Production-ready Dockerfile 
- ✅ Real database credentials configured (Supabase + Upstash Redis)
- ✅ Spring Boot JAR built successfully
- ✅ Health checks and monitoring configured

## 🎯 **RECOMMENDED: AWS App Runner (Easiest)**

### **Step 1: Build and Test the Container (2 minutes)**
```bash
# You are currently in: /Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/backend/user-management-service

# Build using the simple Dockerfile
docker build -f Dockerfile.simple -t aria-user-management:latest .

# Quick local test (optional)
docker run -d -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=aws \
  -e DATABASE_URL="postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres" \
  -e JWT_SECRET="kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN" \
  --name aria-test aria-user-management:latest

# Check if it works
sleep 30
curl http://localhost:8080/api/auth/actuator/health

# Stop test container
docker stop aria-test && docker rm aria-test
```

### **Step 2: Deploy to AWS (5 minutes)**

#### **Option A: AWS Console Deployment (Easiest)**
1. **Push to AWS ECR**:
   ```bash
   # Configure AWS CLI (if not done)
   aws configure
   
   # Get your AWS Account ID
   AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
   echo "Your AWS Account ID: $AWS_ACCOUNT_ID"
   
   # Create ECR repository
   aws ecr create-repository --repository-name aria-user-management --region us-east-1
   
   # Login to ECR
   aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com
   
   # Tag and push
   docker tag aria-user-management:latest $AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/aria-user-management:latest
   docker push $AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/aria-user-management:latest
   ```

2. **Create App Runner Service**:
   - Go to: https://console.aws.amazon.com/apprunner/
   - Click "Create service"
   - **Source**: Container registry → Amazon ECR
   - **Image URI**: `<YOUR_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/aria-user-management:latest`
   - **Service name**: `aria-user-management`
   - **Port**: `8080`
   - **Health check**: `/api/auth/actuator/health`
   
   **Environment Variables**:
   ```
   SPRING_PROFILES_ACTIVE=aws
   DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
   JWT_SECRET=kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN
   UPSTASH_REDIS_REST_TOKEN=AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU
   DB_USERNAME=postgres
   DB_PASSWORD=CoolLife@AriaDB
   ```
   
   - Click "Create & deploy"
   - Wait 5-10 minutes

#### **Option B: AWS CLI Deployment (Advanced)**
```bash
# Get your AWS Account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Create App Runner service via CLI
aws apprunner create-service \
  --service-name "aria-user-management" \
  --source-configuration "$(cat <<EOF
{
  "ImageRepository": {
    "ImageIdentifier": "$AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/aria-user-management:latest",
    "ImageConfiguration": {
      "Port": "8080",
      "RuntimeEnvironmentVariables": {
        "SPRING_PROFILES_ACTIVE": "aws",
        "DATABASE_URL": "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres",
        "JWT_SECRET": "kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN",
        "UPSTASH_REDIS_REST_TOKEN": "AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU"
      }
    },
    "ImageRepositoryType": "ECR"
  }
}
EOF
)" \
  --instance-configuration '{
    "Cpu": "0.25 vCPU",
    "Memory": "0.5 GB"
  }' \
  --health-check-configuration '{
    "Protocol": "HTTP",
    "Path": "/api/auth/actuator/health",
    "Interval": 30,
    "Timeout": 5,
    "HealthyThreshold": 2,
    "UnhealthyThreshold": 3
  }' \
  --region us-east-1
```

### **Step 3: Verify Deployment (2 minutes)**
```bash
# Get your App Runner service URL
SERVICE_URL=$(aws apprunner list-services --region us-east-1 --query "ServiceList[?ServiceName=='aria-user-management'].ServiceUrl" --output text)
echo "Your service is available at: $SERVICE_URL"

# Test health endpoint
curl https://$SERVICE_URL/api/auth/actuator/health

# Test user registration (example)
curl -X POST https://$SERVICE_URL/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123","firstName":"Test","lastName":"User"}'
```

## 🚀 **ALTERNATIVE: AWS Lambda (Serverless - Cost Effective)**

If you prefer serverless deployment:

### **Add Lambda Dependencies**
```bash
# Add to pom.xml
cat >> pom.xml <<EOF
        <!-- AWS Lambda Support -->
        <dependency>
            <groupId>com.amazonaws.serverless</groupId>
            <artifactId>aws-serverless-java-container-springboot3</artifactId>
            <version>2.0.0</version>
        </dependency>
EOF
```

### **Create Lambda Handler**
```java
# Create: src/main/java/com/company/user/LambdaHandler.java
package com.company.user;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class LambdaHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {
    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(UserManagementServiceApplication.class);
        } catch (ContainerInitializationException e) {
            throw new RuntimeException("Could not initialize Spring Boot application", e);
        }
    }

    @Override
    public AwsProxyResponse handleRequest(AwsProxyRequest input, Context context) {
        return handler.proxy(input, context);
    }
}
```

### **Deploy with SAM**
```bash
# Install AWS SAM CLI
# Then create template.yaml and deploy:
sam init
# Follow the SAM deployment guide in AWS_DEPLOYMENT_GUIDE.md
```

## 🎯 **EXPECTED RESULTS**

### **After Successful Deployment:**
✅ **Service URL**: `https://xyz.us-east-1.awsapprunner.com`  
✅ **Health Check**: `https://xyz.us-east-1.awsapprunner.com/api/auth/actuator/health`  
✅ **User Registration**: `https://xyz.us-east-1.awsapprunner.com/api/auth/register`  
✅ **User Login**: `https://xyz.us-east-1.awsapprunner.com/api/auth/login`  

### **Performance Expectations:**
- **Startup Time**: ~60 seconds
- **Response Time**: <500ms
- **Auto-scaling**: 1-10 instances
- **Cost**: ~$15-30/month

### **Database Connectivity:**
✅ **Supabase PostgreSQL**: Connected  
✅ **Upstash Redis**: Connected  
✅ **JWT Authentication**: Configured  
✅ **Health Monitoring**: Active  

## 🔧 **TROUBLESHOOTING**

### **If Build Fails:**
```bash
# Rebuild JAR
mvn clean package -DskipTests

# Rebuild Docker image
docker build -f Dockerfile.simple -t aria-user-management:latest .
```

### **If Health Check Fails:**
```bash
# Check logs in AWS Console
# Or test locally:
docker logs <container-id>
```

### **If Database Connection Fails:**
```bash
# Test Supabase connection
psql "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres" -c "SELECT 1;"
```

---

## 🚀 **EXECUTE DEPLOYMENT NOW**

**You are ready to deploy! Execute these commands:**

```bash
# Navigate to service directory (you're already here)
cd /Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/backend/user-management-service

# Build Docker image
docker build -f Dockerfile.simple -t aria-user-management:latest .

# Configure AWS (if needed)
aws configure

# Push to ECR and deploy (follow steps above)
```

**Your ARIA User Management Service will be live on AWS in 10 minutes!**

🎉 **Database connectivity issues are resolved and the service is production-ready for AWS deployment!**
