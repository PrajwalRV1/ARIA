import { Component, OnInit, OnDestroy, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { CheatDetectionService, CheatDetectionState, BiasAlert } from '../../services/cheat-detection.service';

@Component({
  selector: 'app-cheat-detection-alerts',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="cheat-detection-panel" *ngIf="showPanel">
      <!-- Risk Score Indicator -->
      <div class="risk-score-indicator">
        <div class="risk-circle" [ngClass]="getRiskLevelClass()">
          <span class="risk-score">{{ cheatDetectionState.riskScore }}</span>
          <span class="risk-label">RISK</span>
        </div>
        <div class="risk-info">
          <div class="risk-level">{{ getRiskLevel() | uppercase }}</div>
          <div class="alert-count">{{ getUnresolvedAlertsCount() }} alerts</div>
        </div>
      </div>

      <!-- Monitoring Status -->
      <div class="monitoring-status" [ngClass]="{ 'active': cheatDetectionState.isMonitoring }">
        <i class="fas fa-shield-alt"></i>
        <span>{{ cheatDetectionState.isMonitoring ? 'Monitoring Active' : 'Monitoring Inactive' }}</span>
        <div class="status-dot" [ngClass]="{ 'active': cheatDetectionState.isMonitoring }"></div>
      </div>

      <!-- Bias Alerts List -->
      <div class="alerts-section" *ngIf="cheatDetectionState.biasFlags.length > 0">
        <div class="alerts-header">
          <h4>Security Alerts</h4>
          <button 
            class="clear-all-btn" 
            (click)="clearAllAlerts()"
            [disabled]="getUnresolvedAlertsCount() === 0">
            Clear All
          </button>
        </div>
        
        <div class="alerts-list">
          <div 
            *ngFor="let alert of getRecentAlerts(); trackBy: trackAlert"
            class="alert-item"
            [ngClass]="[
              'severity-' + alert.severity,
              { 'resolved': alert.resolved }
            ]">
            
            <div class="alert-icon">
              <i [class]="getAlertIcon(alert.type)"></i>
            </div>
            
            <div class="alert-content">
              <div class="alert-message">{{ alert.message }}</div>
              <div class="alert-meta">
                <span class="alert-time">{{ formatTime(alert.timestamp) }}</span>
                <span class="alert-severity">{{ alert.severity | uppercase }}</span>
              </div>
              <div class="alert-details" *ngIf="alert.details">
                <span class="confidence">Confidence: {{ (alert.details.confidence * 100) | number:'1.0-0' }}%</span>
              </div>
            </div>
            
            <div class="alert-actions">
              <button 
                *ngIf="!alert.resolved"
                class="resolve-btn"
                (click)="resolveAlert(alert.id)"
                title="Mark as resolved">
                <i class="fas fa-check"></i>
              </button>
              <span *ngIf="alert.resolved" class="resolved-badge">✓</span>
            </div>
          </div>
        </div>
      </div>

      <!-- No Alerts State -->
      <div class="no-alerts" *ngIf="cheatDetectionState.biasFlags.length === 0 && cheatDetectionState.isMonitoring">
        <i class="fas fa-check-circle"></i>
        <span>No security alerts detected</span>
      </div>

      <!-- Last Update Info -->
      <div class="last-update">
        <small>Last updated: {{ formatTime(cheatDetectionState.lastUpdate) }}</small>
      </div>
    </div>

    <!-- Floating Alert Notifications -->
    <div class="floating-alerts">
      <div 
        *ngFor="let alert of getFloatingAlerts(); trackBy: trackAlert"
        class="floating-alert"
        [ngClass]="'severity-' + alert.severity"
        [@slideIn]>
        
        <div class="floating-alert-content">
          <i [class]="getAlertIcon(alert.type)"></i>
          <div class="floating-alert-text">
            <div class="floating-alert-title">{{ getAlertTitle(alert.type) }}</div>
            <div class="floating-alert-message">{{ alert.message }}</div>
          </div>
          <button class="floating-alert-close" (click)="dismissFloatingAlert(alert.id)">
            <i class="fas fa-times"></i>
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .cheat-detection-panel {
      background: #ffffff;
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.1);
      border: 1px solid #e1e8ed;
      max-width: 400px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    /* Risk Score Indicator */
    .risk-score-indicator {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 20px;
      padding: 16px;
      background: #f8fafc;
      border-radius: 8px;
    }

    .risk-circle {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      width: 60px;
      height: 60px;
      border-radius: 50%;
      font-weight: bold;
      transition: all 0.3s ease;
    }

    .risk-circle.low {
      background: #10b981;
      color: white;
    }

    .risk-circle.medium {
      background: #f59e0b;
      color: white;
    }

    .risk-circle.high {
      background: #ef4444;
      color: white;
    }

    .risk-circle.critical {
      background: #dc2626;
      color: white;
      animation: pulse 2s infinite;
    }

    @keyframes pulse {
      0% { transform: scale(1); }
      50% { transform: scale(1.05); }
      100% { transform: scale(1); }
    }

    .risk-score {
      font-size: 18px;
      line-height: 1;
    }

    .risk-label {
      font-size: 10px;
      margin-top: 2px;
    }

    .risk-info {
      flex: 1;
    }

    .risk-level {
      font-size: 16px;
      font-weight: 600;
      color: #1f2937;
    }

    .alert-count {
      font-size: 14px;
      color: #6b7280;
    }

    /* Monitoring Status */
    .monitoring-status {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      background: #f3f4f6;
      border-radius: 8px;
      margin-bottom: 20px;
      font-size: 14px;
      font-weight: 500;
      color: #6b7280;
      transition: all 0.3s ease;
    }

    .monitoring-status.active {
      background: #ecfdf5;
      color: #059669;
    }

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #d1d5db;
      margin-left: auto;
    }

    .status-dot.active {
      background: #10b981;
      animation: blink 2s infinite;
    }

    @keyframes blink {
      0%, 50% { opacity: 1; }
      51%, 100% { opacity: 0.3; }
    }

    /* Alerts Section */
    .alerts-section {
      margin-bottom: 20px;
    }

    .alerts-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    .alerts-header h4 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: #1f2937;
    }

    .clear-all-btn {
      background: #ef4444;
      color: white;
      border: none;
      padding: 6px 12px;
      border-radius: 6px;
      font-size: 12px;
      cursor: pointer;
      transition: background 0.2s ease;
    }

    .clear-all-btn:hover:not(:disabled) {
      background: #dc2626;
    }

    .clear-all-btn:disabled {
      background: #d1d5db;
      cursor: not-allowed;
    }

    /* Alerts List */
    .alerts-list {
      max-height: 300px;
      overflow-y: auto;
      gap: 8px;
      display: flex;
      flex-direction: column;
    }

    .alert-item {
      display: flex;
      gap: 12px;
      padding: 12px;
      border-radius: 8px;
      border-left: 4px solid;
      transition: all 0.3s ease;
      background: #ffffff;
    }

    .alert-item.severity-low {
      border-left-color: #10b981;
      background: #f0fdf4;
    }

    .alert-item.severity-medium {
      border-left-color: #f59e0b;
      background: #fffbeb;
    }

    .alert-item.severity-high {
      border-left-color: #ef4444;
      background: #fef2f2;
    }

    .alert-item.severity-critical {
      border-left-color: #dc2626;
      background: #fee2e2;
      animation: shake 0.5s ease-in-out;
    }

    @keyframes shake {
      0%, 100% { transform: translateX(0); }
      25% { transform: translateX(-5px); }
      75% { transform: translateX(5px); }
    }

    .alert-item.resolved {
      opacity: 0.6;
      background: #f9fafb;
    }

    .alert-icon {
      font-size: 16px;
      display: flex;
      align-items: flex-start;
      padding-top: 2px;
    }

    .alert-content {
      flex: 1;
      min-width: 0;
    }

    .alert-message {
      font-size: 14px;
      font-weight: 500;
      color: #1f2937;
      line-height: 1.4;
      margin-bottom: 6px;
    }

    .alert-meta {
      display: flex;
      gap: 12px;
      align-items: center;
      font-size: 12px;
      color: #6b7280;
      margin-bottom: 4px;
    }

    .alert-severity {
      padding: 2px 6px;
      border-radius: 4px;
      font-weight: 600;
      font-size: 10px;
    }

    .severity-low .alert-severity {
      background: #dcfce7;
      color: #166534;
    }

    .severity-medium .alert-severity {
      background: #fef3c7;
      color: #92400e;
    }

    .severity-high .alert-severity {
      background: #fecaca;
      color: #991b1b;
    }

    .severity-critical .alert-severity {
      background: #fca5a5;
      color: #7f1d1d;
    }

    .alert-details {
      font-size: 12px;
      color: #6b7280;
    }

    .alert-actions {
      display: flex;
      align-items: flex-start;
      gap: 8px;
    }

    .resolve-btn {
      background: #10b981;
      color: white;
      border: none;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-size: 10px;
      transition: background 0.2s ease;
    }

    .resolve-btn:hover {
      background: #059669;
    }

    .resolved-badge {
      color: #10b981;
      font-weight: bold;
      font-size: 16px;
    }

    /* No Alerts State */
    .no-alerts {
      text-align: center;
      padding: 40px 20px;
      color: #6b7280;
    }

    .no-alerts i {
      font-size: 48px;
      color: #10b981;
      margin-bottom: 12px;
      display: block;
    }

    /* Last Update */
    .last-update {
      text-align: center;
      padding-top: 16px;
      border-top: 1px solid #e5e7eb;
      color: #9ca3af;
    }

    /* Floating Alerts */
    .floating-alerts {
      position: fixed;
      top: 80px;
      right: 20px;
      z-index: 1000;
      display: flex;
      flex-direction: column;
      gap: 12px;
      max-width: 400px;
    }

    .floating-alert {
      background: white;
      border-radius: 8px;
      box-shadow: 0 10px 30px rgba(0,0,0,0.2);
      border-left: 4px solid;
      overflow: hidden;
      animation: slideInRight 0.3s ease-out;
    }

    @keyframes slideInRight {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }

    .floating-alert.severity-low {
      border-left-color: #10b981;
    }

    .floating-alert.severity-medium {
      border-left-color: #f59e0b;
    }

    .floating-alert.severity-high {
      border-left-color: #ef4444;
    }

    .floating-alert.severity-critical {
      border-left-color: #dc2626;
      animation: slideInRight 0.3s ease-out, shake 0.5s ease-in-out 0.3s;
    }

    .floating-alert-content {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 16px;
    }

    .floating-alert-content i {
      font-size: 20px;
      padding-top: 2px;
    }

    .floating-alert-text {
      flex: 1;
      min-width: 0;
    }

    .floating-alert-title {
      font-weight: 600;
      font-size: 14px;
      color: #1f2937;
      margin-bottom: 4px;
    }

    .floating-alert-message {
      font-size: 13px;
      color: #6b7280;
      line-height: 1.4;
    }

    .floating-alert-close {
      background: none;
      border: none;
      color: #9ca3af;
      cursor: pointer;
      padding: 4px;
      border-radius: 4px;
      transition: color 0.2s ease;
    }

    .floating-alert-close:hover {
      color: #6b7280;
      background: #f3f4f6;
    }

    /* Responsive Design */
    @media (max-width: 768px) {
      .cheat-detection-panel {
        margin: 0 16px;
        padding: 16px;
      }

      .floating-alerts {
        right: 16px;
        left: 16px;
        max-width: none;
      }

      .risk-score-indicator {
        flex-direction: column;
        text-align: center;
        gap: 12px;
      }
    }
  `],
  animations: []
})
export class CheatDetectionAlertsComponent implements OnInit, OnDestroy {
  @Input() showPanel: boolean = true;
  @Input() showFloatingAlerts: boolean = true;

  cheatDetectionState: CheatDetectionState = {
    isMonitoring: false,
    biasFlags: [],
    totalAlerts: 0,
    riskScore: 0,
    lastUpdate: new Date()
  };

  private floatingAlertsDisplayed: Set<string> = new Set();
  private subscription!: Subscription;

  constructor(private cheatDetectionService: CheatDetectionService) {}

  ngOnInit(): void {
    this.subscription = this.cheatDetectionService.cheatDetectionState$.subscribe(state => {
      this.cheatDetectionState = state;
    });
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  getRiskLevel(): string {
    return this.cheatDetectionService.getRiskLevel();
  }

  getRiskLevelClass(): string {
    return this.cheatDetectionService.getRiskLevel();
  }

  getUnresolvedAlertsCount(): number {
    return this.cheatDetectionService.getUnresolvedAlertsCount();
  }

  getRecentAlerts(): BiasAlert[] {
    return this.cheatDetectionState.biasFlags
      .slice(-10) // Show last 10 alerts
      .reverse(); // Most recent first
  }

  getFloatingAlerts(): BiasAlert[] {
    if (!this.showFloatingAlerts) return [];
    
    const alerts = this.cheatDetectionState.biasFlags
      .filter(alert => 
        !alert.resolved && 
        !this.floatingAlertsDisplayed.has(alert.id) &&
        (Date.now() - alert.timestamp.getTime()) < 30000 // Show for 30 seconds
      )
      .slice(-3); // Max 3 floating alerts
    
    // Mark alerts as displayed
    alerts.forEach(alert => this.floatingAlertsDisplayed.add(alert.id));
    
    return alerts;
  }

  resolveAlert(alertId: string): void {
    this.cheatDetectionService.resolveAlert(alertId);
  }

  clearAllAlerts(): void {
    this.cheatDetectionService.clearAllAlerts();
    this.floatingAlertsDisplayed.clear();
  }

  dismissFloatingAlert(alertId: string): void {
    this.floatingAlertsDisplayed.add(alertId);
  }

  trackAlert(index: number, alert: BiasAlert): string {
    return alert.id;
  }

  getAlertIcon(type: string): string {
    switch (type) {
      case 'bias_detected':
        return 'fas fa-balance-scale text-orange-500';
      case 'suspicious_behavior':
        return 'fas fa-exclamation-triangle text-red-500';
      case 'anomaly_detected':
        return 'fas fa-chart-line text-blue-500';
      default:
        return 'fas fa-info-circle text-gray-500';
    }
  }

  getAlertTitle(type: string): string {
    switch (type) {
      case 'bias_detected':
        return 'Bias Detection';
      case 'suspicious_behavior':
        return 'Suspicious Behavior';
      case 'anomaly_detected':
        return 'Statistical Anomaly';
      default:
        return 'Security Alert';
    }
  }

  formatTime(timestamp: Date): string {
    const now = new Date();
    const diff = now.getTime() - timestamp.getTime();
    
    if (diff < 60000) { // Less than 1 minute
      return 'Just now';
    } else if (diff < 3600000) { // Less than 1 hour
      const minutes = Math.floor(diff / 60000);
      return `${minutes}m ago`;
    } else if (diff < 86400000) { // Less than 1 day
      const hours = Math.floor(diff / 3600000);
      return `${hours}h ago`;
    } else {
      return timestamp.toLocaleDateString();
    }
  }
}
