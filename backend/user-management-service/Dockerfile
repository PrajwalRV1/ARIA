# Production Dockerfile for Render deployment - Multi-stage build v2.0
# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-alpine AS builder

# Install Maven
RUN apk add --no-cache maven

# Set working directory for build
WORKDIR /build

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the application (skip tests to speed up deployment)
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 -S appuser && \
    adduser -S appuser -u 1001

# Set working directory
WORKDIR /app

# Copy the built JAR file from builder stage
COPY --from=builder /build/target/user-management-service-0.0.1-SNAPSHOT.jar app.jar

# Create logs directory
RUN mkdir -p logs && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port 10000 (Render's expected port)
EXPOSE 10000

# Health check for the correct port and path
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:${PORT:-10000}/actuator/health || exit 1

# JVM optimization for containers and limited memory
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom"

# Start the application with neon profile as default - Force neon profile
ENV SPRING_PROFILES_ACTIVE=neon
ENTRYPOINT ["sh", "-c", "echo 'Starting with profile:' ${SPRING_PROFILES_ACTIVE} && java $JAVA_OPTS -Dserver.port=${PORT:-10000} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar app.jar"]
