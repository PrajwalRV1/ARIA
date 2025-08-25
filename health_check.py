"""
Health Check Template for Python Services
Add this to your main.py or create a separate health check endpoint
"""

from flask import Flask, jsonify
from datetime import datetime
import psutil
import os

app = Flask(__name__)

@app.route('/health', methods=['GET'])
def health_check():
    """
    Health check endpoint for monitoring services
    Returns service status, uptime, and basic metrics
    """
    try:
        # Basic health metrics
        memory_usage = psutil.virtual_memory().percent
        cpu_usage = psutil.cpu_percent(interval=1)
        disk_usage = psutil.disk_usage('/').percent
        
        health_data = {
            'status': 'healthy',
            'timestamp': datetime.utcnow().isoformat(),
            'service': os.environ.get('SERVICE_NAME', 'unknown-service'),
            'version': os.environ.get('SERVICE_VERSION', '1.0.0'),
            'environment': os.environ.get('ENVIRONMENT', 'development'),
            'port': os.environ.get('PORT', '8000'),
            'metrics': {
                'memory_usage_percent': memory_usage,
                'cpu_usage_percent': cpu_usage,
                'disk_usage_percent': disk_usage
            },
            'dependencies': check_dependencies()
        }
        
        # Determine overall health status
        if memory_usage > 90 or cpu_usage > 90 or disk_usage > 90:
            health_data['status'] = 'degraded'
        
        return jsonify(health_data), 200
        
    except Exception as e:
        return jsonify({
            'status': 'unhealthy',
            'timestamp': datetime.utcnow().isoformat(),
            'error': str(e),
            'service': os.environ.get('SERVICE_NAME', 'unknown-service')
        }), 500

def check_dependencies():
    """
    Check the health of external dependencies
    Override this function in each service
    """
    dependencies = {}
    
    # Database connection check (if applicable)
    try:
        # Add your database connection check here
        dependencies['database'] = 'healthy'
    except:
        dependencies['database'] = 'unhealthy'
    
    # Redis connection check (if applicable)
    try:
        # Add your Redis connection check here
        dependencies['redis'] = 'healthy'
    except:
        dependencies['redis'] = 'unhealthy'
    
    # External API checks (if applicable)
    try:
        # Add external service health checks here
        dependencies['external_apis'] = 'healthy'
    except:
        dependencies['external_apis'] = 'unhealthy'
    
    return dependencies

@app.route('/ready', methods=['GET'])
def readiness_check():
    """
    Readiness probe for Kubernetes/container orchestration
    """
    try:
        # Check if the service is ready to handle requests
        # Add service-specific readiness checks here
        
        return jsonify({
            'status': 'ready',
            'timestamp': datetime.utcnow().isoformat()
        }), 200
        
    except Exception as e:
        return jsonify({
            'status': 'not_ready',
            'timestamp': datetime.utcnow().isoformat(),
            'error': str(e)
        }), 503

@app.route('/live', methods=['GET'])
def liveness_check():
    """
    Liveness probe for Kubernetes/container orchestration
    """
    return jsonify({
        'status': 'alive',
        'timestamp': datetime.utcnow().isoformat()
    }), 200

if __name__ == '__main__':
    # Add this to your main application
    port = int(os.environ.get('PORT', 8000))
    app.run(host='0.0.0.0', port=port, debug=False)
