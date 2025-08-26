# Multi-stage build for Interview Orchestrator Service
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
COPY --from=build /app/target/interview-orchestrator-service-*.jar app.jar

# Create logs directory
RUN mkdir -p logs && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port (dynamic for Render)
EXPOSE ${PORT:-8081}

# Health check with dynamic port
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8081}/api/interview/actuator/health || exit 1

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

# Start the application with production profile for Render
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-production} -jar app.jar"]
