# Multi-stage build for Railway deployment
# Stage 1: Build the application
FROM openjdk:17-jdk-slim AS builder

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the entire project
COPY . .

# Build the specific service
WORKDIR /app/backend/user-management-service
RUN mvn clean package -DskipTests -Dmaven.test.skip=true

# Stage 2: Runtime
FROM openjdk:17-jdk-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/backend/user-management-service/target/user-management-service-0.0.1-SNAPSHOT.jar app.jar

# Create non-root user for security
RUN useradd -m appuser && chown appuser:appuser app.jar
USER appuser

# Expose port
EXPOSE 8080

# Add health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/api/auth/actuator/health || exit 1

# Run the application with optimized JVM settings for Railway
CMD ["java", \
     "-Dserver.port=${PORT:-8080}", \
     "-Dspring.profiles.active=railway", \
     "-Xmx400m", \
     "-Xms200m", \
     "-XX:+UseG1GC", \
     "-XX:MaxGCPauseMillis=100", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-jar", "app.jar"]
