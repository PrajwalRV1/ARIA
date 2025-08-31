import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
  duration?: number;
  timestamp: Date;
  persistent?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private toastsSubject = new BehaviorSubject<Toast[]>([]);
  public toasts$: Observable<Toast[]> = this.toastsSubject.asObservable();

  private defaultDurations = {
    success: 4000,
    error: 8000,
    warning: 6000,
    info: 5000
  };

  constructor() {}

  // Show success notification
  showSuccess(title: string, message?: string, duration?: number): void {
    this.addToast({
      type: 'success',
      title,
      message: message || '',
      duration: duration || this.defaultDurations.success
    });
  }

  // Show error notification
  showError(title: string, message?: string, persistent = false): void {
    this.addToast({
      type: 'error',
      title,
      message: message || '',
      duration: persistent ? undefined : this.defaultDurations.error,
      persistent
    });
  }

  // Show warning notification
  showWarning(title: string, message?: string, duration?: number): void {
    this.addToast({
      type: 'warning',
      title,
      message: message || '',
      duration: duration || this.defaultDurations.warning
    });
  }

  // Show info notification
  showInfo(title: string, message?: string, duration?: number): void {
    this.addToast({
      type: 'info',
      title,
      message: message || '',
      duration: duration || this.defaultDurations.info
    });
  }

  // Add toast to the list
  private addToast(toast: Omit<Toast, 'id' | 'timestamp'>): void {
    const newToast: Toast = {
      id: this.generateId(),
      timestamp: new Date(),
      ...toast
    };

    const currentToasts = this.toastsSubject.value;
    this.toastsSubject.next([...currentToasts, newToast]);

    // Auto remove toast after duration (if not persistent)
    if (!toast.persistent && toast.duration) {
      setTimeout(() => {
        this.removeToast(newToast.id);
      }, toast.duration);
    }
  }

  // Remove a specific toast
  removeToast(id: string): void {
    const currentToasts = this.toastsSubject.value;
    this.toastsSubject.next(currentToasts.filter(toast => toast.id !== id));
  }

  // Clear all toasts
  clearAll(): void {
    this.toastsSubject.next([]);
  }

  // Clear toasts of a specific type
  clearByType(type: Toast['type']): void {
    const currentToasts = this.toastsSubject.value;
    this.toastsSubject.next(currentToasts.filter(toast => toast.type !== type));
  }

  // Generate unique ID for toasts
  private generateId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
  }

  // Get current toast count
  getToastCount(): number {
    return this.toastsSubject.value.length;
  }

  // Check if there are any error toasts
  hasErrorToasts(): boolean {
    return this.toastsSubject.value.some(toast => toast.type === 'error');
  }
}
