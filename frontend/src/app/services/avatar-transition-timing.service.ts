import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject, timer, combineLatest } from 'rxjs';
import { takeUntil, switchMap, tap, map, filter } from 'rxjs/operators';

export interface TransitionConfig {
  minDelay: number;           // Minimum delay before transition (ms)
  maxDelay: number;           // Maximum delay before transition (ms)
  targetDelay: number;        // Target optimal delay (ms)
  adaptiveThreshold: number;  // Threshold for adaptive adjustments (ms)
  enableAdaptive: boolean;    // Enable adaptive timing adjustments
  enablePreloading: boolean;  // Enable content preloading
  debugMode: boolean;         // Enable debug logging
}

export interface TransitionMetrics {
  requestId: string;
  startTime: number;
  endTime: number;
  actualDelay: number;
  targetDelay: number;
  scoringLatency: number;
  networkLatency: number;
  renderLatency: number;
  totalLatency: number;
  success: boolean;
  errorMessage?: string;
}

export interface TransitionContext {
  sessionId: string;
  stageId: string;
  questionId: string;
  responseId: string;
  candidateResponseLength: number;
  scoringComplexity: 'low' | 'medium' | 'high';
  networkCondition: 'fast' | 'normal' | 'slow';
  priority: 'high' | 'normal' | 'low';
}

export interface TransitionState {
  isActive: boolean;
  currentPhase: 'idle' | 'analyzing' | 'waiting' | 'transitioning' | 'complete';
  remainingDelay: number;
  progress: number; // 0-100
  estimatedCompletion: number;
  context?: TransitionContext;
}

@Injectable({
  providedIn: 'root'
})
export class AvatarTransitionTimingService implements OnDestroy {
  
  // Configuration with intelligent defaults
  private config: TransitionConfig = {
    minDelay: 1500,        // 1.5 seconds minimum
    maxDelay: 4000,        // 4 seconds maximum  
    targetDelay: 2500,     // 2.5 seconds target
    adaptiveThreshold: 300, // 300ms threshold for adaptation
    enableAdaptive: true,
    enablePreloading: true,
    debugMode: false
  };
  
  // State management
  private readonly transitionState$ = new BehaviorSubject<TransitionState>({
    isActive: false,
    currentPhase: 'idle',
    remainingDelay: 0,
    progress: 0,
    estimatedCompletion: 0
  });
  
  // Metrics tracking
  private readonly metrics$ = new BehaviorSubject<TransitionMetrics[]>([]);
  private metricsBuffer: TransitionMetrics[] = [];
  
  // Performance history for adaptive learning
  private performanceHistory: TransitionMetrics[] = [];
  private readonly historyLimit = 50;
  
  // WebSocket connections
  private scoringWS: WebSocket | null = null;
  private avatarWS: WebSocket | null = null;
  
  // Lifecycle
  private destroy$ = new Subject<void>();
  private currentTransitionId: string | null = null;
  
  // Timing utilities
  private timingAdjustments: Map<string, number> = new Map();
  private networkLatencyHistory: number[] = [];
  private scoringLatencyHistory: number[] = [];
  
  constructor() {
    this.initializeConnections();
    this.startPerformanceMonitoring();
    console.log('🎯 Avatar Transition Timing Service initialized');
  }
  
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnectWebSockets();
    console.log('🛑 Avatar Transition Timing Service destroyed');
  }
  
  // Public API
  
  /**
   * Configure the transition timing parameters
   */
  public configure(config: Partial<TransitionConfig>): void {
    this.config = { ...this.config, ...config };
    
    if (this.config.debugMode) {
      console.log('⚙️ Avatar transition config updated:', this.config);
    }
  }
  
  /**
   * Get current configuration
   */
  public getConfig(): TransitionConfig {
    return { ...this.config };
  }
  
  /**
   * Get current transition state
   */
  public getTransitionState(): Observable<TransitionState> {
    return this.transitionState$.asObservable();
  }
  
  /**
   * Get performance metrics
   */
  public getMetrics(): Observable<TransitionMetrics[]> {
    return this.metrics$.asObservable();
  }
  
  /**
   * Start a new avatar transition with guaranteed timing
   */
  public async startTransition(context: TransitionContext): Promise<void> {
    const requestId = `transition_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    this.currentTransitionId = requestId;
    
    const startTime = performance.now();
    
    try {
      // Update state to analyzing phase
      this.updateTransitionState({
        isActive: true,
        currentPhase: 'analyzing',
        remainingDelay: 0,
        progress: 0,
        estimatedCompletion: startTime + this.config.targetDelay,
        context
      });
      
      if (this.config.debugMode) {
        console.log(`🎯 Starting avatar transition ${requestId}:`, context);
      }
      
      // Calculate optimal delay based on context and history
      const optimalDelay = this.calculateOptimalDelay(context);
      
      // Start parallel operations
      const promises = [
        this.waitForScoringComplete(context),
        this.preloadAvatarContent(context),
        this.monitorNetworkConditions()
      ];
      
      // Wait for scoring completion
      const [scoringResult, preloadResult, networkResult] = await Promise.allSettled(promises);
      
      const scoringLatency = performance.now() - startTime;
      
      // Calculate remaining delay after scoring
      const remainingDelay = Math.max(0, optimalDelay - scoringLatency);
      
      if (this.config.debugMode) {
        console.log(`📊 Scoring completed in ${scoringLatency.toFixed(2)}ms, remaining delay: ${remainingDelay.toFixed(2)}ms`);
      }
      
      // Update state to waiting phase
      this.updateTransitionState({
        isActive: true,
        currentPhase: 'waiting',
        remainingDelay,
        progress: 50,
        estimatedCompletion: performance.now() + remainingDelay,
        context
      });
      
      // Execute guaranteed delay
      if (remainingDelay > 0) {
        await this.executeGuaranteedDelay(remainingDelay, requestId);
      }
      
      // Update state to transitioning
      this.updateTransitionState({
        isActive: true,
        currentPhase: 'transitioning',
        remainingDelay: 0,
        progress: 90,
        estimatedCompletion: performance.now() + 500,
        context
      });
      
      // Execute the actual avatar transition
      await this.executeAvatarTransition(context);
      
      const endTime = performance.now();
      const totalLatency = endTime - startTime;
      
      // Record successful metrics
      const metrics: TransitionMetrics = {
        requestId,
        startTime,
        endTime,
        actualDelay: totalLatency,
        targetDelay: optimalDelay,
        scoringLatency,
        networkLatency: this.getAverageNetworkLatency(),
        renderLatency: 500, // Estimated render time
        totalLatency,
        success: true
      };
      
      this.recordMetrics(metrics);
      
      // Update state to complete
      this.updateTransitionState({
        isActive: false,
        currentPhase: 'complete',
        remainingDelay: 0,
        progress: 100,
        estimatedCompletion: endTime,
        context
      });
      
      if (this.config.debugMode) {
        console.log(`✅ Avatar transition completed successfully in ${totalLatency.toFixed(2)}ms`);
      }
      
    } catch (error) {
      const endTime = performance.now();
      const totalLatency = endTime - startTime;
      
      console.error('❌ Avatar transition failed:', error);
      
      // Record error metrics
      const metrics: TransitionMetrics = {
        requestId,
        startTime,
        endTime,
        actualDelay: totalLatency,
        targetDelay: this.config.targetDelay,
        scoringLatency: 0,
        networkLatency: this.getAverageNetworkLatency(),
        renderLatency: 0,
        totalLatency,
        success: false,
        errorMessage: error instanceof Error ? error.message : 'Unknown error'
      };
      
      this.recordMetrics(metrics);
      
      // Update state to idle with error
      this.updateTransitionState({
        isActive: false,
        currentPhase: 'idle',
        remainingDelay: 0,
        progress: 0,
        estimatedCompletion: 0,
        context
      });
      
      throw error;
    } finally {
      this.currentTransitionId = null;
    }
  }
  
  /**
   * Cancel current transition if active
   */
  public cancelTransition(): void {
    if (this.transitionState$.value.isActive) {
      this.updateTransitionState({
        isActive: false,
        currentPhase: 'idle',
        remainingDelay: 0,
        progress: 0,
        estimatedCompletion: 0
      });
      
      this.currentTransitionId = null;
      console.log('🛑 Avatar transition cancelled');
    }
  }
  
  /**
   * Force immediate transition (emergency override)
   */
  public async forceImmediateTransition(context: TransitionContext): Promise<void> {
    console.warn('⚡ Forcing immediate avatar transition');
    
    this.cancelTransition();
    
    try {
      await this.executeAvatarTransition(context);
      console.log('⚡ Immediate transition completed');
    } catch (error) {
      console.error('❌ Immediate transition failed:', error);
      throw error;
    }
  }
  
  // Private Implementation
  
  private initializeConnections(): void {
    this.connectToScoringService();
    this.connectToAvatarService();
  }
  
  private connectToScoringService(): void {
    try {
      const wsUrl = 'wss://localhost:8003/ws/scoring-updates';
      this.scoringWS = new WebSocket(wsUrl);
      
      this.scoringWS.onopen = () => {
        console.log('✅ Connected to scoring service for timing coordination');
      };
      
      this.scoringWS.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.handleScoringUpdate(data);
        } catch (error) {
          console.error('Failed to parse scoring update:', error);
        }
      };
      
      this.scoringWS.onerror = (error) => {
        console.error('Scoring WebSocket error:', error);
      };
      
      this.scoringWS.onclose = () => {
        console.log('🔌 Scoring WebSocket disconnected, attempting reconnection...');
        setTimeout(() => this.connectToScoringService(), 5000);
      };
      
    } catch (error) {
      console.error('Failed to connect to scoring service:', error);
    }
  }
  
  private connectToAvatarService(): void {
    try {
      const wsUrl = 'wss://localhost:8005/ws/avatar-control';
      this.avatarWS = new WebSocket(wsUrl);
      
      this.avatarWS.onopen = () => {
        console.log('✅ Connected to avatar service for transition control');
      };
      
      this.avatarWS.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.handleAvatarUpdate(data);
        } catch (error) {
          console.error('Failed to parse avatar update:', error);
        }
      };
      
      this.avatarWS.onerror = (error) => {
        console.error('Avatar WebSocket error:', error);
      };
      
      this.avatarWS.onclose = () => {
        console.log('🔌 Avatar WebSocket disconnected, attempting reconnection...');
        setTimeout(() => this.connectToAvatarService(), 5000);
      };
      
    } catch (error) {
      console.error('Failed to connect to avatar service:', error);
    }
  }
  
  private calculateOptimalDelay(context: TransitionContext): number {
    let baseDelay = this.config.targetDelay;
    
    // Adjust based on response complexity
    switch (context.scoringComplexity) {
      case 'high':
        baseDelay += 500;
        break;
      case 'medium':
        baseDelay += 200;
        break;
      case 'low':
        baseDelay -= 200;
        break;
    }
    
    // Adjust based on network conditions
    switch (context.networkCondition) {
      case 'slow':
        baseDelay += 800;
        break;
      case 'normal':
        baseDelay += 200;
        break;
      case 'fast':
        // No adjustment needed
        break;
    }
    
    // Adjust based on response length
    if (context.candidateResponseLength > 500) {
      baseDelay += Math.min(1000, context.candidateResponseLength * 2);
    }
    
    // Apply adaptive adjustments based on history
    if (this.config.enableAdaptive) {
      const adjustment = this.getAdaptiveAdjustment(context);
      baseDelay += adjustment;
    }
    
    // Ensure within bounds
    return Math.max(this.config.minDelay, Math.min(this.config.maxDelay, baseDelay));
  }
  
  private getAdaptiveAdjustment(context: TransitionContext): number {
    const recentMetrics = this.performanceHistory.slice(-10);
    if (recentMetrics.length === 0) return 0;
    
    // Calculate average deviation from target
    const avgDeviation = recentMetrics.reduce((sum, metric) => {
      return sum + (metric.actualDelay - metric.targetDelay);
    }, 0) / recentMetrics.length;
    
    // Adjust to compensate for systematic bias
    return -avgDeviation * 0.5; // 50% compensation factor
  }
  
  private async waitForScoringComplete(context: TransitionContext): Promise<void> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('Scoring timeout exceeded'));
      }, this.config.maxDelay);
      
      // Send scoring request
      if (this.scoringWS && this.scoringWS.readyState === WebSocket.OPEN) {
        this.scoringWS.send(JSON.stringify({
          type: 'request_scoring',
          context,
          priority: context.priority,
          requestId: this.currentTransitionId
        }));
      }
      
      // Listen for completion
      const checkCompletion = () => {
        if (this.scoringWS && this.scoringWS.readyState === WebSocket.OPEN) {
          this.scoringWS.onmessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.type === 'scoring_complete' && data.requestId === this.currentTransitionId) {
              clearTimeout(timeout);
              resolve();
            }
          };
        } else {
          // Fallback: simulate scoring completion
          setTimeout(() => {
            clearTimeout(timeout);
            resolve();
          }, 1500);
        }
      };
      
      checkCompletion();
    });
  }
  
  private async preloadAvatarContent(context: TransitionContext): Promise<void> {
    if (!this.config.enablePreloading) return;
    
    return new Promise((resolve) => {
      if (this.avatarWS && this.avatarWS.readyState === WebSocket.OPEN) {
        this.avatarWS.send(JSON.stringify({
          type: 'preload_content',
          context,
          requestId: this.currentTransitionId
        }));
      }
      
      // Don't wait for preloading - it's optional
      setTimeout(resolve, 100);
    });
  }
  
  private async monitorNetworkConditions(): Promise<void> {
    const startTime = performance.now();
    
    try {
      // Simple network ping
      await fetch('/api/ping', { method: 'HEAD' });
      const latency = performance.now() - startTime;
      
      this.networkLatencyHistory.push(latency);
      if (this.networkLatencyHistory.length > 10) {
        this.networkLatencyHistory.shift();
      }
      
    } catch (error) {
      console.warn('Network monitoring failed:', error);
    }
  }
  
  private async executeGuaranteedDelay(delay: number, requestId: string): Promise<void> {
    return new Promise((resolve) => {
      const interval = 100; // Update every 100ms
      const startTime = Date.now();
      
      const updateProgress = () => {
        const elapsed = Date.now() - startTime;
        const progress = Math.min(100, (elapsed / delay) * 100);
        const remaining = Math.max(0, delay - elapsed);
        
        // Only update if this is still the current transition
        if (this.currentTransitionId === requestId) {
          const currentState = this.transitionState$.value;
          this.updateTransitionState({
            ...currentState,
            remainingDelay: remaining,
            progress: 50 + (progress * 0.4) // Progress from 50% to 90%
          });
        }
        
        if (elapsed >= delay) {
          resolve();
        } else {
          setTimeout(updateProgress, interval);
        }
      };
      
      updateProgress();
    });
  }
  
  private async executeAvatarTransition(context: TransitionContext): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.avatarWS && this.avatarWS.readyState === WebSocket.OPEN) {
        const timeout = setTimeout(() => {
          reject(new Error('Avatar transition timeout'));
        }, 2000);
        
        this.avatarWS.send(JSON.stringify({
          type: 'execute_transition',
          context,
          requestId: this.currentTransitionId
        }));
        
        const originalHandler = this.avatarWS.onmessage;
        this.avatarWS.onmessage = (event) => {
          const data = JSON.parse(event.data);
          if (data.type === 'transition_complete' && data.requestId === this.currentTransitionId) {
            clearTimeout(timeout);
            this.avatarWS!.onmessage = originalHandler;
            resolve();
          }
        };
        
      } else {
        // Fallback: simulate transition
        setTimeout(resolve, 500);
      }
    });
  }
  
  private updateTransitionState(state: Partial<TransitionState>): void {
    const currentState = this.transitionState$.value;
    this.transitionState$.next({ ...currentState, ...state });
  }
  
  private recordMetrics(metrics: TransitionMetrics): void {
    this.metricsBuffer.push(metrics);
    this.performanceHistory.push(metrics);
    
    // Maintain history limit
    if (this.performanceHistory.length > this.historyLimit) {
      this.performanceHistory.shift();
    }
    
    // Update metrics observable
    this.metrics$.next([...this.metricsBuffer]);
    
    // Log performance if enabled
    if (this.config.debugMode) {
      console.log('📊 Transition metrics recorded:', {
        requestId: metrics.requestId,
        actualDelay: `${metrics.actualDelay.toFixed(2)}ms`,
        targetDelay: `${metrics.targetDelay}ms`,
        deviation: `${(metrics.actualDelay - metrics.targetDelay).toFixed(2)}ms`,
        success: metrics.success
      });
    }
  }
  
  private getAverageNetworkLatency(): number {
    if (this.networkLatencyHistory.length === 0) return 0;
    return this.networkLatencyHistory.reduce((sum, latency) => sum + latency, 0) / this.networkLatencyHistory.length;
  }
  
  private handleScoringUpdate(data: any): void {
    if (this.config.debugMode) {
      console.log('📊 Scoring update received:', data);
    }
  }
  
  private handleAvatarUpdate(data: any): void {
    if (this.config.debugMode) {
      console.log('🤖 Avatar update received:', data);
    }
  }
  
  private startPerformanceMonitoring(): void {
    timer(0, 30000) // Every 30 seconds
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.generatePerformanceReport();
      });
  }
  
  private generatePerformanceReport(): void {
    const recentMetrics = this.performanceHistory.slice(-20);
    if (recentMetrics.length === 0) return;
    
    const successRate = (recentMetrics.filter(m => m.success).length / recentMetrics.length) * 100;
    const avgDelay = recentMetrics.reduce((sum, m) => sum + m.actualDelay, 0) / recentMetrics.length;
    const avgDeviation = recentMetrics.reduce((sum, m) => sum + Math.abs(m.actualDelay - m.targetDelay), 0) / recentMetrics.length;
    
    if (this.config.debugMode || successRate < 95 || avgDeviation > this.config.adaptiveThreshold) {
      console.log('📈 Avatar Transition Performance Report:', {
        successRate: `${successRate.toFixed(1)}%`,
        averageDelay: `${avgDelay.toFixed(2)}ms`,
        averageDeviation: `${avgDeviation.toFixed(2)}ms`,
        sampleSize: recentMetrics.length
      });
    }
  }
  
  private disconnectWebSockets(): void {
    if (this.scoringWS) {
      this.scoringWS.close();
      this.scoringWS = null;
    }
    
    if (this.avatarWS) {
      this.avatarWS.close();
      this.avatarWS = null;
    }
  }
  
  // Public utility methods
  
  /**
   * Get current performance statistics
   */
  public getPerformanceStats(): any {
    const recentMetrics = this.performanceHistory.slice(-20);
    if (recentMetrics.length === 0) {
      return {
        successRate: 0,
        averageDelay: 0,
        averageDeviation: 0,
        sampleSize: 0
      };
    }
    
    const successRate = (recentMetrics.filter(m => m.success).length / recentMetrics.length) * 100;
    const avgDelay = recentMetrics.reduce((sum, m) => sum + m.actualDelay, 0) / recentMetrics.length;
    const avgDeviation = recentMetrics.reduce((sum, m) => sum + Math.abs(m.actualDelay - m.targetDelay), 0) / recentMetrics.length;
    
    return {
      successRate,
      averageDelay,
      averageDeviation,
      sampleSize: recentMetrics.length,
      isWithinTarget: avgDeviation <= this.config.adaptiveThreshold
    };
  }
  
  /**
   * Reset performance history and metrics
   */
  public resetMetrics(): void {
    this.performanceHistory = [];
    this.metricsBuffer = [];
    this.metrics$.next([]);
    this.networkLatencyHistory = [];
    console.log('🔄 Avatar transition metrics reset');
  }
}
