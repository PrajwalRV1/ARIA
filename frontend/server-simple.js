const express = require('express');
const path = require('path');
const fs = require('fs');

const app = express();
const port = process.env.PORT || 4000;

// Path to the built Angular application
const distPath = path.join(__dirname, 'dist', 'frontend', 'browser');

console.log('Starting ARIA frontend server...');
console.log('Serving from:', distPath);
console.log('Environment:', process.env.NODE_ENV);

// Check if the dist directory exists
if (!fs.existsSync(distPath)) {
    console.error('Error: dist/frontend/browser directory not found!');
    console.error('Expected path:', distPath);
    process.exit(1);
}

// Serve static files from the Angular build output
app.use(express.static(distPath, {
    maxAge: '1y',
    etag: false,
    lastModified: false
}));

// API routes (if any) should go here
app.use('/api', (req, res) => {
    res.status(404).json({ error: 'API endpoint not found' });
});

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ 
        status: 'ok', 
        timestamp: new Date().toISOString(),
        service: 'ARIA Frontend',
        version: '1.0.0'
    });
});

// Catch all handler: send back Angular's index.html file for client-side routing
app.get('*', (req, res) => {
    console.log(`Serving route: ${req.path}`);
    
    // Try index.html first, then fall back to index.csr.html
    let indexFile = path.join(distPath, 'index.html');
    if (!fs.existsSync(indexFile)) {
        indexFile = path.join(distPath, 'index.csr.html');
    }
    
    if (fs.existsSync(indexFile)) {
        res.sendFile(indexFile);
    } else {
        console.error('No index file found!');
        res.status(404).send('Application not found');
    }
});

// Error handling middleware
app.use((err, req, res, next) => {
    console.error('Server error:', err);
    res.status(500).send('Internal Server Error');
});

// Start the server
app.listen(port, '0.0.0.0', () => {
    console.log(`✅ ARIA frontend server running on http://0.0.0.0:${port}`);
    console.log(`📁 Serving static files from: ${distPath}`);
    console.log(`🏥 Health check available at: http://0.0.0.0:${port}/health`);
    
    // List available files in the dist directory
    try {
        const files = fs.readdirSync(distPath);
        console.log('📋 Available files:', files.join(', '));
    } catch (e) {
        console.error('Could not list files in dist directory');
    }
});

// Graceful shutdown
process.on('SIGTERM', () => {
    console.log('Received SIGTERM, shutting down gracefully');
    process.exit(0);
});

process.on('SIGINT', () => {
    console.log('Received SIGINT, shutting down gracefully');
    process.exit(0);
});
