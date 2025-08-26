# 🚀 AWS Deployment Guide - ARIA User Management Service

## 🎯 Deployment Options for AWS

### **Option 1: AWS Elastic Container Service (ECS) - Recommended**
- **Cost**: ~$15-30/month (Fargate)
- **Scalability**: Auto-scaling
- **Management**: Fully managed
- **Best for**: Production workloads

### **Option 2: AWS App Runner - Easiest**
- **Cost**: ~$10-25/month
- **Scalability**: Auto-scaling  
- **Management**: Fully managed
- **Best for**: Quick deployment

### **Option 3: AWS Lambda (Serverless)**
- **Cost**: Pay-per-request (~$5-15/month)
- **Scalability**: Infinite auto-scaling
- **Management**: Serverless
- **Best for**: Cost optimization

## 🚀 **Recommended: AWS App Runner Deployment**

### Step 1: Build and Push to Container Registry

#### **1.1 Build the Docker Image**
```bash
cd /Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/backend/user-management-service

# Build the Docker image
docker build -t aria-user-management:latest .

# Test locally (optional)
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=aws \
  -e DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres \
  -e JWT_SECRET=kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN \
  aria-user-management:latest
```

#### **1.2 Push to AWS ECR (Elastic Container Registry)**
```bash
# Get AWS CLI configured
aws configure

# Create ECR repository
aws ecr create-repository --repository-name aria-user-management --region us-east-1

# Get ECR login token
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <YOUR_AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Tag image for ECR
docker tag aria-user-management:latest <YOUR_AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/aria-user-management:latest

# Push to ECR
docker push <YOUR_AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/aria-user-management:latest
```

### Step 2: Deploy with AWS App Runner

#### **2.1 Create apprunner.yaml**
```yaml
# Create this file: apprunner.yaml
version: 1.0
runtime: docker
build:
  commands:
    build:
      - echo "Build completed"
run:
  runtime-version: latest
  command: sh -c "java $JAVA_OPTS -Dspring.profiles.active=aws -jar app.jar"
  network:
    port: 8080
    env:
      - name: PORT
        value: "8080"
      - name: SPRING_PROFILES_ACTIVE
        value: "aws"
      - name: DATABASE_URL
        value: "postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres"
      - name: JWT_SECRET
        value: "kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN"
      - name: UPSTASH_REDIS_REST_TOKEN
        value: "AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU"
  healthcheck:
    path: "/api/auth/actuator/health"
    interval: 30
    timeout: 5
    unhealthy-threshold: 3
    healthy-threshold: 2
```

#### **2.2 Deploy via AWS CLI**
```bash
# Create App Runner service
aws apprunner create-service \
  --service-name "aria-user-management" \
  --source-configuration '{
    "ImageRepository": {
      "ImageIdentifier": "<YOUR_AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/aria-user-management:latest",
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
  }' \
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

## 🚀 **Alternative: Quick Deploy with AWS Console**

### Option A: App Runner Console Deployment

1. **Go to AWS App Runner Console**
   - Navigate to https://console.aws.amazon.com/apprunner/
   - Click "Create service"

2. **Configure Source**
   - Choose "Container registry"
   - Select "Amazon ECR"
   - Choose your image URI: `<ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/aria-user-management:latest`

3. **Configure Service**
   - Service name: `aria-user-management`
   - Port: `8080`
   - Health check: `/api/auth/actuator/health`

4. **Environment Variables**
   ```
   SPRING_PROFILES_ACTIVE=aws
   DATABASE_URL=postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
   JWT_SECRET=kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN
   UPSTASH_REDIS_REST_TOKEN=AaExAAIncDE3NTczYWIxNDNjYjA0NzI2YWQ2NmY0ZTZjZTg5Y2IyMXAxNDEyNjU
   ```

5. **Deploy**
   - Click "Create & deploy"
   - Wait 5-10 minutes for deployment

## 🚀 **Serverless Option: AWS Lambda with Spring Boot**

### Lambda Deployment (Cost-Effective)

#### **1. Add AWS Lambda Dependencies to pom.xml**
```xml
<dependency>
    <groupId>com.amazonaws.serverless</groupId>
    <artifactId>aws-serverless-java-container-springboot3</artifactId>
    <version>2.0.0</version>
</dependency>
```

#### **2. Create Lambda Handler**
```java
// Create: src/main/java/com/company/user/LambdaHandler.java
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
            e.printStackTrace();
            throw new RuntimeException("Could not initialize Spring Boot application", e);
        }
    }

    @Override
    public AwsProxyResponse handleRequest(AwsProxyRequest input, Context context) {
        return handler.proxy(input, context);
    }
}
```

#### **3. Deploy with AWS SAM**
```yaml
# Create: template.yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31

Resources:
  UserManagementFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: target/user-management-service-0.0.1-SNAPSHOT.jar
      Handler: com.company.user.LambdaHandler::handleRequest
      Runtime: java17
      MemorySize: 1024
      Timeout: 30
      Environment:
        Variables:
          SPRING_PROFILES_ACTIVE: aws
          DATABASE_URL: postgresql://postgres:CoolLife@AriaDB@db.deqfzxsmuydhrepyiagq.supabase.co:5432/postgres
          JWT_SECRET: kV7pL9zR3mX1tU5qW8bC2yJ6fH4nE0sDmA3gK9xZ2vF8uQ4tY7rP1wE6iO5sL0hN
      Events:
        ApiGateway:
          Type: Api
          Properties:
            Path: /{proxy+}
            Method: ANY

Outputs:
  ApiGatewayEndpoint:
    Description: "API Gateway endpoint URL"
    Value: !Sub "https://${ServerlessRestApi}.execute-api.${AWS::Region}.amazonaws.com/Prod/"
```

```bash
# Deploy with SAM
sam build
sam deploy --guided
```

## 🔧 **Current Status & Next Steps**

### **Ready for Deployment:**
✅ AWS-optimized application.properties created  
✅ Production-ready Dockerfile created  
✅ Multi-deployment options available  
✅ Database connectivity configured (Supabase + Upstash Redis)  
✅ Health checks and monitoring configured  

### **Choose Your Deployment Path:**

#### **🎯 Recommended for Production: AWS App Runner**
```bash
# Quick deployment commands:
cd /Users/prajwalramannavenkatesh/Desktop/private_repo/ARIA/backend/user-management-service
docker build -t aria-user-management .
# Follow App Runner steps above
```

#### **💰 Cost-Effective: AWS Lambda**
```bash
# Add Lambda dependencies to pom.xml
# Create LambdaHandler.java
# Deploy with SAM
```

#### **🏗️ Enterprise: ECS Fargate**
```bash
# Use ECS for advanced orchestration
# Better for microservices architecture
```

## 🎯 **Expected Results After Deployment**

### **Service Endpoints:**
- **Health Check**: `https://your-app.region.awsapprunner.com/api/auth/actuator/health`
- **User Registration**: `https://your-app.region.awsapprunner.com/api/auth/register`
- **User Login**: `https://your-app.region.awsapprunner.com/api/auth/login`
- **Metrics**: `https://your-app.region.awsapprunner.com/api/auth/actuator/metrics`

### **Performance Targets:**
- **Startup Time**: < 60 seconds
- **Response Time**: < 500ms for auth operations  
- **Availability**: 99.9%
- **Auto-scaling**: 1-10 instances based on load

### **Cost Estimation:**
- **App Runner**: $15-30/month
- **Lambda**: $5-15/month (pay per request)
- **ECS Fargate**: $20-40/month

---

## 🚀 **Ready to Deploy!**

**The user-management-service is now AWS-ready with:**
- ✅ AWS-optimized configuration
- ✅ Production Dockerfile
- ✅ Multiple deployment options
- ✅ Database connectivity (Supabase + Redis)
- ✅ Health monitoring & metrics

**Choose your preferred deployment method above and execute the deployment commands!**
