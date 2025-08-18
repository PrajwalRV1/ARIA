# ARIA Platform Deployment Guide

This guide provides comprehensive instructions for deploying ARIA in various environments.

## 🎯 Deployment Overview

ARIA supports multiple deployment strategies:
- **Development**: Local development with hot reload
- **Staging**: Production-like environment for testing
- **Production**: High-availability, scalable production deployment
- **Cloud**: AWS, Azure, GCP, and Kubernetes deployments

## 📋 Prerequisites

### System Requirements
- **CPU**: 4+ cores (8+ cores for production)
- **RAM**: 8GB minimum (16GB+ for production)
- **Storage**: 50GB minimum (100GB+ for production)
- **Network**: Stable internet connection (1Gbps+ for production)

### Software Dependencies
- **Docker**: 20.10+ with Docker Compose 2.0+
- **Git**: 2.30+ for version control
- **Node.js**: 18+ (for frontend development)
- **Java**: 17+ (for backend development)
- **Python**: 3.11+ (for AI services)

### Port Requirements
Ensure these ports are available:
- `3306`: MySQL database
- `6379`: Redis cache
- `8080`: User Management Service
- `8081`: Interview Orchestrator Service
- `8001`: Adaptive Question Engine
- `8002`: Speech & Transcript Service
- `8003`: AI Analytics Service
- `4200`: Frontend Application
- `80/443`: Nginx reverse proxy
- `3478`: CoTURN server
- `9090`: Prometheus
- `3000`: Grafana
- `3100`: Loki

## 🚀 Quick Development Setup

### One-Command Deployment
```bash
# Clone the repository
git clone https://github.com/your-org/aria-platform.git
cd aria-platform

# Start development environment
./start-aria.sh development

# Verify deployment
./start-aria.sh status
```

### Manual Development Setup
```bash
# Create environment file
cp .env.example .env

# Start infrastructure services
docker-compose up -d mysql redis

# Wait for services to be ready
./scripts/wait-for-services.sh

# Start application services
docker-compose up -d user-management-service interview-orchestrator-service

# Start AI services
docker-compose up -d adaptive-engine speech-service analytics-service

# Start frontend
docker-compose up -d frontend

# Start reverse proxy
docker-compose up -d nginx
```

## 🏗️ Staging Deployment

### Environment Setup
```bash
# Create staging environment
cp .env.staging.example .env

# Configure staging-specific settings
vim .env

# Build images for staging
docker-compose -f docker-compose.staging.yml build

# Deploy to staging
docker-compose -f docker-compose.staging.yml up -d
```

### Staging Configuration
```bash
# .env file for staging
ENVIRONMENT=staging
MYSQL_HOST=staging-mysql.aria.internal
REDIS_HOST=staging-redis.aria.internal
JWT_SECRET=staging-jwt-secret-key
GOOGLE_STT_CREDENTIALS_PATH=/secrets/google-stt-staging.json
SSL_ENABLED=true
SSL_CERT_PATH=/certs/staging.crt
SSL_KEY_PATH=/certs/staging.key
```

### Health Checks
```bash
# Verify staging deployment
curl -f http://staging.aria.internal/health

# Check service health
./scripts/health-check.sh staging

# View staging logs
docker-compose -f docker-compose.staging.yml logs -f
```

## 🏭 Production Deployment

### Infrastructure Preparation
```bash
# Create production directory structure
mkdir -p /opt/aria-platform/{data,logs,certs,secrets}

# Set proper permissions
chmod 750 /opt/aria-platform
chown -R aria:aria /opt/aria-platform

# Create production user
useradd -r -s /bin/false aria
```

### SSL Certificate Setup
```bash
# Using Let's Encrypt
certbot certonly --standalone \
  -d aria.yourdomain.com \
  -d api.aria.yourdomain.com

# Copy certificates
cp /etc/letsencrypt/live/aria.yourdomain.com/fullchain.pem /opt/aria-platform/certs/
cp /etc/letsencrypt/live/aria.yourdomain.com/privkey.pem /opt/aria-platform/certs/
```

### Production Environment
```bash
# Create production environment file
cat > /opt/aria-platform/.env << EOF
ENVIRONMENT=production
MYSQL_HOST=prod-mysql.aria.internal
MYSQL_PASSWORD=secure-mysql-password
REDIS_HOST=prod-redis.aria.internal
REDIS_PASSWORD=secure-redis-password
JWT_SECRET=production-jwt-secret-key
ENCRYPTION_KEY=production-encryption-key
GOOGLE_STT_CREDENTIALS_PATH=/secrets/google-stt-prod.json
SSL_ENABLED=true
SSL_CERT_PATH=/certs/fullchain.pem
SSL_KEY_PATH=/certs/privkey.pem
PROMETHEUS_ENABLED=true
GRAFANA_ENABLED=true
LOKI_ENABLED=true
BACKUP_ENABLED=true
EOF
```

### Production Deployment
```bash
# Navigate to deployment directory
cd /opt/aria-platform

# Clone production repository
git clone --branch production https://github.com/your-org/aria-platform.git .

# Build production images
docker-compose -f docker-compose.prod.yml build

# Deploy production services
docker-compose -f docker-compose.prod.yml up -d

# Verify deployment
./scripts/production-health-check.sh
```

### High Availability Setup
```bash
# Scale critical services
docker-compose -f docker-compose.prod.yml up -d --scale interview-orchestrator-service=3
docker-compose -f docker-compose.prod.yml up -d --scale adaptive-engine=3
docker-compose -f docker-compose.prod.yml up -d --scale speech-service=2

# Configure load balancing
docker-compose -f docker-compose.prod.yml up -d nginx-lb
```

## ☁️ Cloud Deployments

### AWS Deployment
```bash
# Install AWS CLI and configure
aws configure

# Create VPC and subnets
aws cloudformation create-stack \
  --stack-name aria-infrastructure \
  --template-body file://aws/infrastructure.yml

# Deploy application
aws ecs create-service \
  --service-name aria-platform \
  --task-definition aria-platform:1 \
  --desired-count 3

# Set up RDS for database
aws rds create-db-instance \
  --db-instance-identifier aria-mysql \
  --db-instance-class db.t3.medium \
  --engine mysql \
  --master-username aria \
  --master-user-password secure-password
```

### Kubernetes Deployment
```bash
# Apply Kubernetes manifests
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/secrets.yml
kubectl apply -f k8s/configmaps.yml
kubectl apply -f k8s/services.yml
kubectl apply -f k8s/deployments.yml
kubectl apply -f k8s/ingress.yml

# Verify deployment
kubectl get pods -n aria-platform
kubectl get services -n aria-platform

# Scale services
kubectl scale deployment interview-orchestrator --replicas=3 -n aria-platform
```

### Docker Swarm Deployment
```bash
# Initialize swarm
docker swarm init

# Create secrets
echo "secure-mysql-password" | docker secret create mysql-password -
echo "secure-redis-password" | docker secret create redis-password -

# Deploy stack
docker stack deploy -c docker-compose.swarm.yml aria

# Scale services
docker service scale aria_interview-orchestrator=3
docker service scale aria_adaptive-engine=3
```

## 🔧 Configuration Management

### Environment Variables
```bash
# Core Application Settings
ENVIRONMENT=production
LOG_LEVEL=INFO
DEBUG=false
TIMEZONE=UTC

# Database Configuration
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=aria
MYSQL_USERNAME=aria
MYSQL_PASSWORD=secure-password

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=secure-password
REDIS_DATABASE=0

# JWT Configuration
JWT_SECRET=your-jwt-secret-key
JWT_EXPIRATION=3600
JWT_REFRESH_EXPIRATION=604800

# Google Speech-to-Text
GOOGLE_STT_CREDENTIALS_PATH=/secrets/google-stt.json
GOOGLE_STT_LANGUAGE=en-US

# SSL Configuration
SSL_ENABLED=true
SSL_CERT_PATH=/certs/cert.pem
SSL_KEY_PATH=/certs/key.pem

# Monitoring
PROMETHEUS_ENABLED=true
GRAFANA_ENABLED=true
LOKI_ENABLED=true
```

### Secrets Management
```bash
# Using Docker Secrets
echo "mysql-password" | docker secret create mysql_password -
echo "redis-password" | docker secret create redis_password -
echo "jwt-secret" | docker secret create jwt_secret -

# Using Kubernetes Secrets
kubectl create secret generic aria-secrets \
  --from-literal=mysql-password=secure-password \
  --from-literal=redis-password=secure-password \
  --from-literal=jwt-secret=jwt-secret-key \
  -n aria-platform

# Using HashiCorp Vault
vault kv put secret/aria/mysql password=secure-password
vault kv put secret/aria/redis password=secure-password
vault kv put secret/aria/jwt secret=jwt-secret-key
```

## 📊 Monitoring Setup

### Prometheus Configuration
```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'aria-services'
    static_configs:
      - targets: ['user-management:8080', 'interview-orchestrator:8081']
  
  - job_name: 'aria-ai-services'
    static_configs:
      - targets: ['adaptive-engine:8001', 'speech-service:8002', 'analytics-service:8003']
```

### Grafana Dashboards
```bash
# Import ARIA dashboards
curl -X POST \
  http://admin:admin@localhost:3000/api/dashboards/db \
  -H 'Content-Type: application/json' \
  -d @monitoring/grafana/aria-overview.json

# Set up alerts
curl -X POST \
  http://admin:admin@localhost:3000/api/alert-notifications \
  -H 'Content-Type: application/json' \
  -d @monitoring/grafana/alert-config.json
```

### Log Aggregation
```yaml
# loki-config.yml
auth_enabled: false

server:
  http_listen_port: 3100

ingester:
  lifecycler:
    address: 127.0.0.1
    ring:
      kvstore:
        store: inmemory
      replication_factor: 1

schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h
```

## 🔒 Security Hardening

### Container Security
```bash
# Run containers as non-root
docker run --user 1000:1000 aria/service

# Limit container resources
docker run --memory=512m --cpus=0.5 aria/service

# Use read-only filesystem
docker run --read-only --tmpfs /tmp aria/service

# Drop capabilities
docker run --cap-drop=ALL --cap-add=NET_BIND_SERVICE aria/service
```

### Network Security
```bash
# Create isolated networks
docker network create aria-frontend
docker network create aria-backend
docker network create aria-database

# Configure firewall
ufw allow 80/tcp
ufw allow 443/tcp
ufw deny 3306/tcp
ufw deny 6379/tcp
ufw enable
```

### Data Encryption
```bash
# Encrypt data at rest
cryptsetup luksFormat /dev/sdb
cryptsetup luksOpen /dev/sdb aria-data
mkfs.ext4 /dev/mapper/aria-data
mount /dev/mapper/aria-data /opt/aria-platform/data

# Configure MySQL encryption
echo "innodb_encrypt_tables=ON" >> /etc/mysql/mysql.conf.d/encryption.cnf
echo "innodb_encrypt_log=ON" >> /etc/mysql/mysql.conf.d/encryption.cnf
```

## 🔄 Backup and Recovery

### Database Backup
```bash
# Automated MySQL backup
#!/bin/bash
BACKUP_DIR="/opt/aria-platform/backups"
DATE=$(date +%Y%m%d_%H%M%S)

mysqldump --single-transaction --routines --triggers \
  -u aria -p$MYSQL_PASSWORD aria > $BACKUP_DIR/aria_$DATE.sql

# Compress backup
gzip $BACKUP_DIR/aria_$DATE.sql

# Upload to S3
aws s3 cp $BACKUP_DIR/aria_$DATE.sql.gz s3://aria-backups/
```

### Application Backup
```bash
# Backup application data
tar -czf /opt/aria-platform/backups/app_data_$(date +%Y%m%d).tar.gz \
  /opt/aria-platform/data/ \
  /opt/aria-platform/logs/ \
  /opt/aria-platform/.env

# Backup configuration
cp -r /opt/aria-platform/{docker-compose.yml,.env,certs} \
  /opt/aria-platform/backups/config_$(date +%Y%m%d)/
```

### Disaster Recovery
```bash
# Restore database
gunzip < aria_backup.sql.gz | mysql -u aria -p aria

# Restore application
docker-compose down
tar -xzf app_data_backup.tar.gz -C /
docker-compose up -d

# Verify recovery
./scripts/health-check.sh
```

## 🚦 Health Checks and Monitoring

### Application Health Checks
```bash
# Health check endpoints
curl -f http://localhost:8080/actuator/health  # User Management
curl -f http://localhost:8081/actuator/health  # Interview Orchestrator
curl -f http://localhost:8001/health           # Adaptive Engine
curl -f http://localhost:8002/health           # Speech Service
curl -f http://localhost:8003/health           # Analytics Service
```

### Automated Monitoring
```bash
#!/bin/bash
# monitoring-script.sh

# Check service health
for service in user-management interview-orchestrator adaptive-engine speech-service analytics-service; do
  if ! docker ps | grep -q $service; then
    echo "CRITICAL: $service is not running"
    # Restart service
    docker-compose restart $service
  fi
done

# Check disk space
if [ $(df /opt/aria-platform | tail -1 | awk '{print $5}' | sed 's/%//') -gt 85 ]; then
  echo "WARNING: Disk space low"
fi

# Check memory usage
if [ $(free | grep Mem | awk '{print ($3/$2) * 100.0}' | cut -d. -f1) -gt 85 ]; then
  echo "WARNING: Memory usage high"
fi
```

## 🔧 Troubleshooting

### Common Issues
```bash
# Service won't start
docker-compose logs service-name

# Database connection issues
mysql -h localhost -u aria -p -e "SELECT 1"

# Redis connection issues
redis-cli -h localhost ping

# Port conflicts
netstat -tulpn | grep :8080

# Memory issues
free -h
docker stats
```

### Debug Commands
```bash
# Enter container for debugging
docker exec -it aria_service_name /bin/bash

# View container logs
docker logs -f aria_service_name

# Check network connectivity
docker exec aria_service_name ping google.com

# Verify service discovery
docker exec aria_service_name nslookup mysql
```

## 📈 Performance Optimization

### Database Optimization
```sql
-- MySQL optimization
SET innodb_buffer_pool_size = 1G;
SET query_cache_size = 256M;
SET tmp_table_size = 256M;
SET max_heap_table_size = 256M;

-- Add indexes
CREATE INDEX idx_interview_session_candidate ON interview_sessions(candidate_id);
CREATE INDEX idx_interview_response_session ON interview_responses(session_id);
```

### Application Tuning
```bash
# JVM tuning for Spring Boot services
export JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"

# Python optimization for FastAPI services
export PYTHONOPTIMIZE=1
export UVICORN_WORKERS=4
```

### Caching Strategy
```bash
# Redis configuration optimization
echo "maxmemory 2gb" >> /etc/redis/redis.conf
echo "maxmemory-policy allkeys-lru" >> /etc/redis/redis.conf
echo "save 900 1" >> /etc/redis/redis.conf
```

## 📞 Support and Maintenance

### Regular Maintenance
```bash
# Weekly maintenance script
#!/bin/bash

# Update system packages
apt update && apt upgrade -y

# Restart services
docker-compose restart

# Clean up old logs
find /opt/aria-platform/logs -name "*.log" -mtime +30 -delete

# Clean up old backups
find /opt/aria-platform/backups -name "*.gz" -mtime +90 -delete

# Update Docker images
docker-compose pull
```

### Emergency Contacts
- **On-call Engineer**: +1-555-0123
- **Database Admin**: +1-555-0124
- **DevOps Team**: devops@aria-platform.com
- **Security Team**: security@aria-platform.com

---

**Last Updated**: January 15, 2024  
**Version**: 1.0.0  
**Maintained by**: ARIA DevOps Team

For deployment questions, please contact the DevOps team or create an issue in the GitHub repository.
