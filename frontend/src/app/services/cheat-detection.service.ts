import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

export interface BiasAlert {
  id: string;
  type: 'bias_detected' | 'suspicious_behavior' | 'anomaly_detected';
  severity: 'low' | 'medium' | 'high' | 'critical';
  message: string;
  timestamp: Date;
  details: any;
  resolved: boolean;
}

export interface CheatDetectionState {
  isMonitoring: boolean;
  biasFlags: BiasAlert[];
  totalAlerts: number;
  riskScore: number; // 0-100
  lastUpdate: Date;
}

/**
 * Service for managing cheat detection and bias monitoring integration
 * Connects frontend UI with backend bias detection capabilities
 */
@Injectable({
  providedIn: 'root'
})
export class CheatDetectionService {
  private analyticsSocket: WebSocket | null = null;
  private analyticsWsConnected = false;

  private cheatDetectionStateSubject = new BehaviorSubject<CheatDetectionState>({
    isMonitoring: false,
    biasFlags: [],
    totalAlerts: 0,
    riskScore: 0,
    lastUpdate: new Date()
  });

  public cheatDetectionState$ = this.cheatDetectionStateSubject.asObservable();

  constructor(private http: HttpClient) {}

  /**
   * Start monitoring cheat detection for a session
   */
  startMonitoring(sessionId: string): void {
    const currentState = this.cheatDetectionStateSubject.value;
    this.cheatDetectionStateSubject.next({
      ...currentState,
      isMonitoring: true,
      lastUpdate: new Date()
    });

    // Only simulate if a real analytics WS is not connected
    if (!this.analyticsWsConnected) {
      this.simulateBiasUpdates(sessionId);
    }
  }

  /**
   * Stop monitoring cheat detection
   */
  stopMonitoring(): void {
    const currentState = this.cheatDetectionStateSubject.value;
    this.cheatDetectionStateSubject.next({
      ...currentState,
      isMonitoring: false,
      lastUpdate: new Date()
    });

    // Close analytics socket if open
    try {
      if (this.analyticsSocket) {
        this.analyticsSocket.close();
        this.analyticsSocket = null;
        this.analyticsWsConnected = false;
      }
    } catch {}
  }

  /**
   * Add a new bias alert
   */
  addBiasAlert(alert: Omit<BiasAlert, 'id' | 'timestamp' | 'resolved'>): void {
    const currentState = this.cheatDetectionStateSubject.value;
    const newAlert: BiasAlert = {
      ...alert,
      id: this.generateAlertId(),
      timestamp: new Date(),
      resolved: false
    };

    const updatedBiasFlags = [...currentState.biasFlags, newAlert];
    
    this.cheatDetectionStateSubject.next({
      ...currentState,
      biasFlags: updatedBiasFlags,
      totalAlerts: updatedBiasFlags.length,
      riskScore: this.calculateRiskScore(updatedBiasFlags),
      lastUpdate: new Date()
    });

    // Trigger UI notification based on severity
    this.triggerUINotification(newAlert);
  }

  /**
   * Mark a bias alert as resolved
   */
  resolveAlert(alertId: string): void {
    const currentState = this.cheatDetectionStateSubject.value;
    const updatedBiasFlags = currentState.biasFlags.map(alert =>
      alert.id === alertId ? { ...alert, resolved: true } : alert
    );

    this.cheatDetectionStateSubject.next({
      ...currentState,
      biasFlags: updatedBiasFlags,
      riskScore: this.calculateRiskScore(updatedBiasFlags),
      lastUpdate: new Date()
    });
  }

  /**
   * Get bias detection analysis from backend
   */
  getBiasAnalysis(sessionId: string): Observable<any> {
    return this.http.get(`${environment.apiBaseUrl}/analytics/bias-detection/${sessionId}`);
  }

  /**
   * Get real-time cheat detection status
   */
  getCheatDetectionStatus(sessionId: string): Observable<any> {
    return this.http.get(`${environment.apiBaseUrl}/interview/sessions/${sessionId}/cheat-status`);
  }

  /**
   * Report suspicious behavior
   */
  reportSuspiciousBehavior(sessionId: string, behaviorType: string, details: any): Observable<any> {
    return this.http.post(`${environment.apiBaseUrl}/interview/sessions/${sessionId}/report-behavior`, {
      behaviorType,
      details,
      timestamp: new Date()
    });
  }

  /**
   * Get unresolved alerts count
   */
  connect(sessionId: string): void {
    try {
      const url = `${environment.aiServices.orchestratorWsUrl}/analytics/${sessionId}`;
      this.analyticsSocket = new WebSocket(url);

      this.analyticsSocket.onopen = () => {
        this.analyticsWsConnected = true;
        // Wire basic browser context signals for proctoring
        const send = (type: string, data: any) => {
          try { this.analyticsSocket?.send(JSON.stringify({ type, data, timestamp: new Date().toISOString() })); } catch {}
        };
        document.addEventListener('visibilitychange', () => send('visibility_change', { visible: !document.hidden }));
        window.addEventListener('focus', () => send('focus_change', { focused: true }));
        window.addEventListener('blur', () => send('focus_change', { focused: false }));
      };

      this.analyticsSocket.onmessage = (evt) => {
        try {
          const msg = JSON.parse(evt.data);
          // Future: route bias_alert or suspicious events to addBiasAlert
          if (msg.type === 'bias_alert' || msg.type === 'suspicious_behavior') {
            this.addBiasAlert({
              type: msg.type,
              severity: msg.severity || 'medium',
              message: msg.message || 'Proctoring event detected',
              details: msg.details || {},
            });
          }
        } catch {}
      };

      this.analyticsSocket.onclose = () => {
        this.analyticsWsConnected = false;
      };
      this.analyticsSocket.onerror = () => {
        this.analyticsWsConnected = false;
      };
    } catch (e) {
      console.warn('Failed to connect analytics WS', e);
    }
  }

  getUnresolvedAlertsCount(): number {
    const currentState = this.cheatDetectionStateSubject.value;
    return currentState.biasFlags.filter(alert => !alert.resolved).length;
  }

  /**
   * Get current risk level
   */
  getRiskLevel(): 'low' | 'medium' | 'high' | 'critical' {
    const riskScore = this.cheatDetectionStateSubject.value.riskScore;
    if (riskScore >= 80) return 'critical';
    if (riskScore >= 60) return 'high';
    if (riskScore >= 30) return 'medium';
    return 'low';
  }

  /**
   * Clear all alerts
   */
  clearAllAlerts(): void {
    const currentState = this.cheatDetectionStateSubject.value;
    this.cheatDetectionStateSubject.next({
      ...currentState,
      biasFlags: [],
      totalAlerts: 0,
      riskScore: 0,
      lastUpdate: new Date()
    });
  }

  // Private helper methods

  private generateAlertId(): string {
    return 'alert_' + Math.random().toString(36).substr(2, 9);
  }

  private calculateRiskScore(alerts: BiasAlert[]): number {
    const unresolvedAlerts = alerts.filter(alert => !alert.resolved);
    
    if (unresolvedAlerts.length === 0) return 0;

    const severityWeights = {
      low: 10,
      medium: 25,
      high: 50,
      critical: 100
    };

    const totalScore = unresolvedAlerts.reduce((sum, alert) => {
      return sum + severityWeights[alert.severity];
    }, 0);

    return Math.min(100, totalScore);
  }

  private triggerUINotification(alert: BiasAlert): void {
    // This would integrate with your notification system
    console.warn(`🚨 Bias Alert [${alert.severity.toUpperCase()}]: ${alert.message}`);
    
    // Trigger browser notification for critical alerts
    if (alert.severity === 'critical' && 'Notification' in window) {
      if (Notification.permission === 'granted') {
        new Notification(`ARIA: Critical Bias Alert`, {
          body: alert.message,
          icon: '/assets/icons/warning.png'
        });
      }
    }
  }

  private simulateBiasUpdates(sessionId: string): void {
    // This simulates receiving bias detection updates from the backend
    // In real implementation, this would be WebSocket or Server-Sent Events

    setTimeout(() => {
      // Stop simulation if real analytics WS is connected or monitoring stopped
      if (!this.cheatDetectionStateSubject.value.isMonitoring || this.analyticsWsConnected) {
        return;
      }

      // Simulate random bias alerts for demonstration
      const alertTypes = [
        { type: 'bias_detected' as const, message: 'Potential gender bias detected in evaluation' },
        { type: 'suspicious_behavior' as const, message: 'Unusual response pattern detected' },
        { type: 'anomaly_detected' as const, message: 'Statistical anomaly in candidate performance' }
      ];

      const severities: ('low' | 'medium' | 'high' | 'critical')[] = ['low', 'medium', 'high', 'critical'];
      
      if (Math.random() < 0.3) { // 30% chance of bias alert
        const randomAlert = alertTypes[Math.floor(Math.random() * alertTypes.length)];
        const randomSeverity = severities[Math.floor(Math.random() * severities.length)];

        this.addBiasAlert({
          type: randomAlert.type,
          severity: randomSeverity,
          message: randomAlert.message,
          details: {
            sessionId: sessionId,
            detectionModel: 'ARIA-BiasDetector-v1.2',
            confidence: Math.random() * 0.4 + 0.6 // 60-100% confidence
          }
        });
      }

      // Continue monitoring
      this.simulateBiasUpdates(sessionId);
    }, Math.random() * 10000 + 5000); // Random interval between 5-15 seconds
  }
}
