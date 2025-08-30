# Use official Node.js runtime as base image
FROM node:18-alpine AS builder

# Set working directory
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install all dependencies (including devDependencies for build)
RUN npm ci

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Production stage
FROM node:18-alpine AS production

# Install curl for health checks
RUN apk add --no-cache curl

# Set working directory
WORKDIR /app

# Copy built application from builder stage
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/package*.json ./

# Create proper index.html from index.csr.html for static serving
RUN cp dist/frontend/browser/index.csr.html dist/frontend/browser/index.html || echo "No index.csr.html found"

# Install only production dependencies needed for SSR
RUN npm ci --only=production && npm cache clean --force

# Create non-root user
RUN addgroup -g 1001 -S nodejs
RUN adduser -S angular -u 1001

# Change ownership of the app directory
RUN chown -R angular:nodejs /app
USER angular

# Set environment variables
ENV PORT=10000
ENV NODE_ENV=production

# Expose port
EXPOSE 10000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:10000/ || exit 1

# Start the Angular SSR server directly
CMD ["node", "dist/frontend/server/server.mjs"]
