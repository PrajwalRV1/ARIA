#!/bin/bash

# ARIA Frontend HTTPS Startup Script
# This script starts the Angular development server with HTTPS enabled

echo "🚀 Starting ARIA Frontend with HTTPS..."
echo "📍 URL: https://localhost:4200"
echo "🔒 SSL: Enabled"
echo ""

# Check if Angular CLI is installed
if ! command -v ng &> /dev/null
then
    echo "❌ Angular CLI is not installed. Please install it first:"
    echo "npm install -g @angular/cli"
    exit 1
fi

# Check if node_modules exists
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

# Start the development server with HTTPS
echo "🔧 Starting development server..."
ng serve --ssl --port 4200 --host localhost --open

echo "✅ Server started successfully!"
echo "🌐 Open https://localhost:4200 in your browser"
echo "⚠️  You may see a security warning - click 'Advanced' and 'Proceed to localhost'"
