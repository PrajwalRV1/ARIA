# Multi-stage build for User Management Service (Following interview-orchestrator pattern)
FROM eclipse-temurin:17-jdk-alpine AS build

# Install Maven
RUN apk add --no-cache maven

# Set working directory
WORKDIR /app

# Copy Maven files
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN mvn clean package -DskipTests -B

# Production image
FROM eclipse-temurin:17-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 -S appuser && \
    adduser -S appuser -u 1001

# Set working directory
WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/target/user-management-service-*.jar app.jar

# Create logs directory
RUN mkdir -p logs && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port (dynamic for Render)
EXPOSE ${PORT:-10000}

# Health check with dynamic port
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:${PORT:-10000}/actuator/health || exit 1

# JVM optimization for containers and limited memory
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication -Djava.security.egd=file:/dev/./urandom"

# Start the application with neon profile as default - Force neon profile
ENV SPRING_PROFILES_ACTIVE=neon
ENTRYPOINT ["sh", "-c", "echo 'Starting with profile:' ${SPRING_PROFILES_ACTIVE} && java $JAVA_OPTS -Dserver.port=${PORT:-10000} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar app.jar"]
