import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject, timer, fromEvent, merge } from 'rxjs';
import { takeUntil, throttleTime, map, filter, debounceTime } from 'rxjs/operators';

export interface PerformanceMetric {
  id: string;
  timestamp: number;
  category: 'network' | 'rendering' | 'computation' | 'memory' | 'user_interaction';
  name: string;
  value: number;
  unit: 'ms' | 'mb' | 'fps' | 'count' | 'percentage';
  severity: 'info' | 'warning' | 'error' | 'critical';
  metadata?: Record<string, any>;
  sessionId?: string;
  userId?: string;
}

export interface SystemError {
  id: string;
  timestamp: number;
  type: 'runtime' | 'network' | 'validation' | 'timeout' | 'service' | 'ui';
  severity: 'low' | 'medium' | 'high' | 'critical';
  message: string;
  stack?: string;
  context?: Record<string, any>;
  userAgent?: string;
  url?: string;
  sessionId?: string;
  userId?: string;
  resolved?: boolean;
  resolution?: string;
}

export interface PerformanceThresholds {
  networkLatency: { warning: number; error: number };
  renderTime: { warning: number; error: number };
  memoryUsage: { warning: number; error: number };
  frameRate: { warning: number; error: number };
  errorRate: { warning: number; error: number };
  responseTime: { warning: number; error: number };
}

export interface HealthCheck {
  timestamp: number;
  overallStatus: 'healthy' | 'degraded' | 'critical';
  services: {
    frontend: ServiceHealth;
    backend: ServiceHealth;
    websockets: ServiceHealth;
    database: ServiceHealth;
    ai_services: ServiceHealth;
  };
  metrics: {
    uptime: number;
    memoryUsage: number;
    networkLatency: number;
    errorRate: number;
    activeUsers: number;
    activeSessions: number;
  };
}

export interface ServiceHealth {
  status: 'healthy' | 'degraded' | 'critical' | 'unknown';
  responseTime: number;
  lastCheck: number;
  errorCount: number;
  details?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PerformanceMonitoringService implements OnDestroy {

  // Configuration
  private readonly config = {
    metricsBufferSize: 1000,
    errorBufferSize: 500,
    alertThrottleMs: 30000, // 30 seconds
    healthCheckIntervalMs: 60000, // 1 minute
    telemetryEndpoint: 'wss://localhost:8001/ws/telemetry',
    enableRealTimeReporting: true,
    enableAutoRecovery: true,
    debugMode: false
  };

  // Performance thresholds
  private thresholds: PerformanceThresholds = {
    networkLatency: { warning: 500, error: 2000 },
    renderTime: { warning: 16, error: 33 },
    memoryUsage: { warning: 70, error: 85 },
    frameRate: { warning: 30, error: 20 },
    errorRate: { warning: 1, error: 5 },
    responseTime: { warning: 1000, error: 3000 }
  };

  // Observables
  private readonly metrics$ = new BehaviorSubject<PerformanceMetric[]>([]);
  private readonly errors$ = new BehaviorSubject<SystemError[]>([]);
  private readonly healthStatus$ = new BehaviorSubject<HealthCheck | null>(null);
  private readonly alerts$ = new Subject<{ type: 'metric' | 'error'; data: any }>();

  // State
  private metricsBuffer: PerformanceMetric[] = [];
  private errorsBuffer: SystemError[] = [];
  private destroy$ = new Subject<void>();
  private telemetryWS: WebSocket | null = null;
  private performanceObserver: PerformanceObserver | null = null;
  private startTime = performance.now();
  private lastHealthCheck = 0;

  // Error handling
  private errorRecoveryStrategies = new Map<string, () => Promise<void>>();
  private serviceCircuitBreakers = new Map<string, { failures: number; lastFailure: number; isOpen: boolean }>();

  constructor() {
    this.initializeMonitoring();
    this.setupErrorHandling();
    this.startHealthChecks();
    this.connectTelemetry();
    console.log('📊 Performance Monitoring Service initialized');
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnectTelemetry();
    if (this.performanceObserver) {
      this.performanceObserver.disconnect();
    }
    console.log('🛑 Performance Monitoring Service destroyed');
  }

  // Public API

  /**
   * Record a custom performance metric
   */
  public recordMetric(
    category: PerformanceMetric['category'],
    name: string,
    value: number,
    unit: PerformanceMetric['unit'],
    metadata?: Record<string, any>
  ): void {
    const metric: PerformanceMetric = {
      id: `metric_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      timestamp: Date.now(),
      category,
      name,
      value,
      unit,
      severity: this.determineSeverity(category, name, value),
      metadata,
      sessionId: this.getSessionId(),
      userId: this.getUserId()
    };

    this.addMetric(metric);

    if (this.config.debugMode) {
      console.log(`📈 Metric recorded: ${name} = ${value}${unit}`, metric);
    }
  }

  /**
   * Record a system error
   */
  public recordError(
    type: SystemError['type'],
    severity: SystemError['severity'],
    message: string,
    error?: Error,
    context?: Record<string, any>
  ): string {
    const systemError: SystemError = {
      id: `error_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      timestamp: Date.now(),
      type,
      severity,
      message,
      stack: error?.stack,
      context,
      userAgent: navigator.userAgent,
      url: window.location.href,
      sessionId: this.getSessionId(),
      userId: this.getUserId(),
      resolved: false
    };

    this.addError(systemError);
    this.triggerErrorRecovery(systemError);

    if (this.config.debugMode || severity === 'critical') {
      console.error(`❌ System error recorded: ${message}`, systemError);
    }

    return systemError.id;
  }

  /**
   * Mark an error as resolved
   */
  public resolveError(errorId: string, resolution: string): void {
    const errorIndex = this.errorsBuffer.findIndex(err => err.id === errorId);
    if (errorIndex >= 0) {
      this.errorsBuffer[errorIndex].resolved = true;
      this.errorsBuffer[errorIndex].resolution = resolution;
      this.errors$.next([...this.errorsBuffer]);
      console.log(`✅ Error ${errorId} resolved: ${resolution}`);
    }
  }

  /**
   * Get performance metrics
   */
  public getMetrics(): Observable<PerformanceMetric[]> {
    return this.metrics$.asObservable();
  }

  /**
   * Get system errors
   */
  public getErrors(): Observable<SystemError[]> {
    return this.errors$.asObservable();
  }

  /**
   * Get health status
   */
  public getHealthStatus(): Observable<HealthCheck | null> {
    return this.healthStatus$.asObservable();
  }

  /**
   * Get alerts
   */
  public getAlerts(): Observable<{ type: 'metric' | 'error'; data: any }> {
    return this.alerts$.asObservable();
  }

  /**
   * Start performance profiling for a specific operation
   */
  public startProfiling(operationName: string): () => void {
    const startTime = performance.now();
    const startMemory = (performance as any).memory?.usedJSHeapSize || 0;

    return () => {
      const endTime = performance.now();
      const endMemory = (performance as any).memory?.usedJSHeapSize || 0;
      const duration = endTime - startTime;
      const memoryDelta = endMemory - startMemory;

      this.recordMetric('computation', `${operationName}_duration`, duration, 'ms');
      this.recordMetric('memory', `${operationName}_memory_delta`, memoryDelta / 1024 / 1024, 'mb');
    };
  }

  /**
   * Configure performance thresholds
   */
  public configureThresholds(thresholds: Partial<PerformanceThresholds>): void {
    this.thresholds = { ...this.thresholds, ...thresholds };
    console.log('⚙️ Performance thresholds updated:', this.thresholds);
  }

  /**
   * Register error recovery strategy
   */
  public registerErrorRecovery(errorPattern: string, recoveryFn: () => Promise<void>): void {
    this.errorRecoveryStrategies.set(errorPattern, recoveryFn);
    console.log(`🔧 Error recovery strategy registered for: ${errorPattern}`);
  }

  /**
   * Force a health check
   */
  public async performHealthCheck(): Promise<HealthCheck> {
    return await this.executeHealthCheck();
  }

  /**
   * Get performance summary
   */
  public getPerformanceSummary(): any {
    const recentMetrics = this.metricsBuffer.slice(-100);
    const recentErrors = this.errorsBuffer.slice(-50);

    const summary = {
      uptime: Date.now() - this.startTime,
      totalMetrics: this.metricsBuffer.length,
      totalErrors: this.errorsBuffer.length,
      errorRate: this.calculateErrorRate(),
      averageResponseTime: this.calculateAverageResponseTime(recentMetrics),
      memoryUsage: this.getCurrentMemoryUsage(),
      criticalErrors: recentErrors.filter(e => e.severity === 'critical' && !e.resolved).length,
      healthStatus: this.healthStatus$.value?.overallStatus || 'unknown'
    };

    return summary;
  }

  // Private Implementation

  private initializeMonitoring(): void {
    // Setup performance observer for navigation and resource timing
    if ('PerformanceObserver' in window) {
      this.performanceObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          this.processPerformanceEntry(entry);
        }
      });

      this.performanceObserver.observe({ entryTypes: ['navigation', 'resource', 'measure', 'paint'] });
    }

    // Monitor frame rate
    this.monitorFrameRate();

    // Monitor memory usage
    this.monitorMemoryUsage();

    // Monitor network performance
    this.monitorNetworkPerformance();
  }

  private setupErrorHandling(): void {
    // Global error handler
    window.addEventListener('error', (event) => {
      this.recordError(
        'runtime',
        'high',
        event.message,
        event.error,
        {
          filename: event.filename,
          lineno: event.lineno,
          colno: event.colno
        }
      );
    });

    // Unhandled promise rejection handler
    window.addEventListener('unhandledrejection', (event) => {
      this.recordError(
        'runtime',
        'high',
        `Unhandled promise rejection: ${event.reason}`,
        undefined,
        { reason: event.reason }
      );
    });

    // Network error monitoring
    this.monitorNetworkErrors();
  }

  private processPerformanceEntry(entry: PerformanceEntry): void {
    switch (entry.entryType) {
      case 'navigation':
        const navEntry = entry as PerformanceNavigationTiming;
        this.recordMetric('network', 'page_load_time', navEntry.loadEventEnd - navEntry.navigationStart, 'ms');
        this.recordMetric('network', 'dom_content_loaded', navEntry.domContentLoadedEventEnd - navEntry.navigationStart, 'ms');
        break;

      case 'resource':
        const resourceEntry = entry as PerformanceResourceTiming;
        this.recordMetric('network', 'resource_load_time', resourceEntry.responseEnd - resourceEntry.requestStart, 'ms', {
          resource: resourceEntry.name
        });
        break;

      case 'paint':
        this.recordMetric('rendering', entry.name.replace('-', '_'), entry.startTime, 'ms');
        break;

      case 'measure':
        this.recordMetric('computation', entry.name, entry.duration, 'ms');
        break;
    }
  }

  private monitorFrameRate(): void {
    let lastTime = 0;
    let frameCount = 0;
    
    const measureFPS = (currentTime: number) => {
      frameCount++;
      
      if (currentTime - lastTime >= 1000) {
        const fps = Math.round((frameCount * 1000) / (currentTime - lastTime));
        this.recordMetric('rendering', 'frame_rate', fps, 'fps');
        
        frameCount = 0;
        lastTime = currentTime;
      }
      
      requestAnimationFrame(measureFPS);
    };
    
    requestAnimationFrame(measureFPS);
  }

  private monitorMemoryUsage(): void {
    timer(0, 5000) // Every 5 seconds
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if ((performance as any).memory) {
          const memory = (performance as any).memory;
          const usagePercent = (memory.usedJSHeapSize / memory.jsHeapSizeLimit) * 100;
          
          this.recordMetric('memory', 'heap_used', memory.usedJSHeapSize / 1024 / 1024, 'mb');
          this.recordMetric('memory', 'heap_usage_percent', usagePercent, 'percentage');
        }
      });
  }

  private monitorNetworkPerformance(): void {
    // Monitor connection quality
    if ('connection' in navigator) {
      const connection = (navigator as any).connection;
      
      this.recordMetric('network', 'connection_downlink', connection.downlink || 0, 'mb');
      this.recordMetric('network', 'connection_rtt', connection.rtt || 0, 'ms');
      
      connection.addEventListener('change', () => {
        this.recordMetric('network', 'connection_downlink', connection.downlink || 0, 'mb');
        this.recordMetric('network', 'connection_rtt', connection.rtt || 0, 'ms');
      });
    }
  }

  private monitorNetworkErrors(): void {
    // Monitor fetch failures
    const originalFetch = window.fetch;
    window.fetch = async (...args) => {
      const startTime = performance.now();
      try {
        const response = await originalFetch(...args);
        const duration = performance.now() - startTime;
        
        this.recordMetric('network', 'fetch_response_time', duration, 'ms', {
          url: args[0].toString(),
          status: response.status
        });
        
        if (!response.ok) {
          this.recordError(
            'network',
            response.status >= 500 ? 'high' : 'medium',
            `HTTP ${response.status}: ${response.statusText}`,
            undefined,
            { url: args[0].toString(), status: response.status }
          );
        }
        
        return response;
      } catch (error) {
        const duration = performance.now() - startTime;
        
        this.recordError(
          'network',
          'high',
          `Network request failed: ${error}`,
          error instanceof Error ? error : undefined,
          { url: args[0].toString(), duration }
        );
        
        throw error;
      }
    };
  }

  private startHealthChecks(): void {
    timer(0, this.config.healthCheckIntervalMs)
      .pipe(takeUntil(this.destroy$))
      .subscribe(async () => {
        try {
          const healthCheck = await this.executeHealthCheck();
          this.healthStatus$.next(healthCheck);
        } catch (error) {
          console.error('Health check failed:', error);
        }
      });
  }

  private async executeHealthCheck(): Promise<HealthCheck> {
    const timestamp = Date.now();
    
    // Check individual services
    const services = {
      frontend: await this.checkFrontendHealth(),
      backend: await this.checkServiceHealth('/api/health'),
      websockets: await this.checkWebSocketHealth(),
      database: await this.checkServiceHealth('/api/health/database'),
      ai_services: await this.checkServiceHealth('/api/health/ai-services')
    };

    // Calculate overall status
    const serviceStatuses = Object.values(services).map(s => s.status);
    const overallStatus = serviceStatuses.includes('critical') ? 'critical' :
                         serviceStatuses.includes('degraded') ? 'degraded' : 'healthy';

    const healthCheck: HealthCheck = {
      timestamp,
      overallStatus,
      services,
      metrics: {
        uptime: timestamp - this.startTime,
        memoryUsage: this.getCurrentMemoryUsage(),
        networkLatency: this.getAverageNetric('network', 'fetch_response_time') || 0,
        errorRate: this.calculateErrorRate(),
        activeUsers: this.getActiveUserCount(),
        activeSessions: this.getActiveSessionCount()
      }
    };

    this.lastHealthCheck = timestamp;
    return healthCheck;
  }

  private async checkFrontendHealth(): Promise<ServiceHealth> {
    const startTime = performance.now();
    
    try {
      // Test DOM manipulation
      const testDiv = document.createElement('div');
      document.body.appendChild(testDiv);
      document.body.removeChild(testDiv);
      
      const responseTime = performance.now() - startTime;
      
      return {
        status: 'healthy',
        responseTime,
        lastCheck: Date.now(),
        errorCount: 0
      };
      
    } catch (error) {
      return {
        status: 'critical',
        responseTime: performance.now() - startTime,
        lastCheck: Date.now(),
        errorCount: 1,
        details: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  private async checkServiceHealth(endpoint: string): Promise<ServiceHealth> {
    const startTime = performance.now();
    
    try {
      const response = await fetch(endpoint, { 
        method: 'GET',
        timeout: 5000 
      } as any);
      
      const responseTime = performance.now() - startTime;
      
      if (response.ok) {
        return {
          status: 'healthy',
          responseTime,
          lastCheck: Date.now(),
          errorCount: 0
        };
      } else {
        return {
          status: response.status >= 500 ? 'critical' : 'degraded',
          responseTime,
          lastCheck: Date.now(),
          errorCount: 1,
          details: `HTTP ${response.status}`
        };
      }
      
    } catch (error) {
      return {
        status: 'critical',
        responseTime: performance.now() - startTime,
        lastCheck: Date.now(),
        errorCount: 1,
        details: error instanceof Error ? error.message : 'Network error'
      };
    }
  }

  private async checkWebSocketHealth(): Promise<ServiceHealth> {
    return new Promise((resolve) => {
      const startTime = performance.now();
      
      try {
        const testWS = new WebSocket('wss://localhost:8001/ws/health-check');
        
        const timeout = setTimeout(() => {
          testWS.close();
          resolve({
            status: 'critical',
            responseTime: performance.now() - startTime,
            lastCheck: Date.now(),
            errorCount: 1,
            details: 'WebSocket connection timeout'
          });
        }, 5000);
        
        testWS.onopen = () => {
          clearTimeout(timeout);
          testWS.close();
          resolve({
            status: 'healthy',
            responseTime: performance.now() - startTime,
            lastCheck: Date.now(),
            errorCount: 0
          });
        };
        
        testWS.onerror = () => {
          clearTimeout(timeout);
          resolve({
            status: 'critical',
            responseTime: performance.now() - startTime,
            lastCheck: Date.now(),
            errorCount: 1,
            details: 'WebSocket connection failed'
          });
        };
        
      } catch (error) {
        resolve({
          status: 'critical',
          responseTime: performance.now() - startTime,
          lastCheck: Date.now(),
          errorCount: 1,
          details: error instanceof Error ? error.message : 'WebSocket error'
        });
      }
    });
  }

  private connectTelemetry(): void {
    if (!this.config.enableRealTimeReporting) return;
    
    try {
      this.telemetryWS = new WebSocket(this.config.telemetryEndpoint);
      
      this.telemetryWS.onopen = () => {
        console.log('📡 Connected to telemetry service');
      };
      
      this.telemetryWS.onerror = (error) => {
        console.warn('📡 Telemetry connection error:', error);
      };
      
      this.telemetryWS.onclose = () => {
        console.log('📡 Telemetry disconnected, attempting reconnection...');
        setTimeout(() => this.connectTelemetry(), 5000);
      };
      
    } catch (error) {
      console.warn('Failed to connect telemetry:', error);
    }
  }

  private disconnectTelemetry(): void {
    if (this.telemetryWS) {
      this.telemetryWS.close();
      this.telemetryWS = null;
    }
  }

  private addMetric(metric: PerformanceMetric): void {
    this.metricsBuffer.push(metric);
    
    // Maintain buffer size
    if (this.metricsBuffer.length > this.config.metricsBufferSize) {
      this.metricsBuffer.shift();
    }
    
    this.metrics$.next([...this.metricsBuffer]);
    this.sendTelemetry('metric', metric);
    
    // Check for alerts
    if (metric.severity === 'warning' || metric.severity === 'error') {
      this.alerts$.next({ type: 'metric', data: metric });
    }
  }

  private addError(error: SystemError): void {
    this.errorsBuffer.push(error);
    
    // Maintain buffer size
    if (this.errorsBuffer.length > this.config.errorBufferSize) {
      this.errorsBuffer.shift();
    }
    
    this.errors$.next([...this.errorsBuffer]);
    this.sendTelemetry('error', error);
    
    // Always alert on errors
    this.alerts$.next({ type: 'error', data: error });
  }

  private sendTelemetry(type: string, data: any): void {
    if (this.telemetryWS && this.telemetryWS.readyState === WebSocket.OPEN) {
      try {
        this.telemetryWS.send(JSON.stringify({ type, data, timestamp: Date.now() }));
      } catch (error) {
        console.warn('Failed to send telemetry:', error);
      }
    }
  }

  private determineSeverity(category: string, name: string, value: number): PerformanceMetric['severity'] {
    const thresholdKey = this.getThresholdKey(category, name);
    if (!thresholdKey || !this.thresholds[thresholdKey as keyof PerformanceThresholds]) {
      return 'info';
    }
    
    const threshold = this.thresholds[thresholdKey as keyof PerformanceThresholds];
    
    if (value >= threshold.error) return 'error';
    if (value >= threshold.warning) return 'warning';
    return 'info';
  }

  private getThresholdKey(category: string, name: string): string | null {
    const keyMap: Record<string, string> = {
      'network_fetch_response_time': 'responseTime',
      'network_connection_rtt': 'networkLatency',
      'rendering_frame_rate': 'frameRate',
      'memory_heap_usage_percent': 'memoryUsage'
    };
    
    return keyMap[`${category}_${name}`] || null;
  }

  private async triggerErrorRecovery(error: SystemError): Promise<void> {
    if (!this.config.enableAutoRecovery) return;
    
    for (const [pattern, recoveryFn] of this.errorRecoveryStrategies) {
      if (error.message.includes(pattern) || error.type.includes(pattern)) {
        try {
          console.log(`🔧 Attempting error recovery for: ${pattern}`);
          await recoveryFn();
          this.resolveError(error.id, `Auto-recovery successful using strategy: ${pattern}`);
          break;
        } catch (recoveryError) {
          console.error(`❌ Error recovery failed for ${pattern}:`, recoveryError);
        }
      }
    }
  }

  private calculateErrorRate(): number {
    const recentErrors = this.errorsBuffer.filter(e => Date.now() - e.timestamp < 300000); // Last 5 minutes
    const recentMetrics = this.metricsBuffer.filter(m => Date.now() - m.timestamp < 300000);
    
    if (recentMetrics.length === 0) return 0;
    return (recentErrors.length / recentMetrics.length) * 100;
  }

  private calculateAverageResponseTime(metrics: PerformanceMetric[]): number {
    const responseTimeMetrics = metrics.filter(m => m.name.includes('response_time') || m.name.includes('load_time'));
    if (responseTimeMetrics.length === 0) return 0;
    
    return responseTimeMetrics.reduce((sum, m) => sum + m.value, 0) / responseTimeMetrics.length;
  }

  private getCurrentMemoryUsage(): number {
    if ((performance as any).memory) {
      const memory = (performance as any).memory;
      return (memory.usedJSHeapSize / memory.jsHeapSizeLimit) * 100;
    }
    return 0;
  }

  private getAverageMetric(category: string, name: string): number | null {
    const relevantMetrics = this.metricsBuffer
      .filter(m => m.category === category && m.name === name && Date.now() - m.timestamp < 300000)
      .slice(-10);
    
    if (relevantMetrics.length === 0) return null;
    return relevantMetrics.reduce((sum, m) => sum + m.value, 0) / relevantMetrics.length;
  }

  private getActiveUserCount(): number {
    // This would typically come from user session tracking
    return 1; // Placeholder
  }

  private getActiveSessionCount(): number {
    // This would typically come from session management
    return 1; // Placeholder
  }

  private getSessionId(): string {
    return localStorage.getItem('sessionId') || 'unknown';
  }

  private getUserId(): string {
    return localStorage.getItem('userId') || 'anonymous';
  }
}
