import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { SessionService } from './session.service';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';

export interface SessionRecoveryState {
  isRecovering: boolean;
  hasRecovered: boolean;
  recoveryAttempts: number;
  lastRecoveryAttempt?: Date;
}

@Injectable({
  providedIn: 'root'
})
export class SessionRecoveryService {
  private readonly MAX_RECOVERY_ATTEMPTS = 3;
  private readonly RECOVERY_TIMEOUT = 30000; // 30 seconds
  
  private recoveryStateSubject = new BehaviorSubject<SessionRecoveryState>({
    isRecovering: false,
    hasRecovered: false,
    recoveryAttempts: 0
  });

  public recoveryState$ = this.recoveryStateSubject.asObservable();

  constructor(
    private sessionService: SessionService,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    if (isPlatformBrowser(this.platformId)) {
    this.initializeRecovery();
      this.setupBeforeUnloadHandler();
      this.setupVisibilityChangeHandler();
    }
  }

  /**
   * Initialize session recovery on application start
   */
  private initializeRecovery(): void {
    // Check if there's a session to recover
    const currentSession = this.sessionService.getCurrentSession();
    if (currentSession) {
      this.attemptSessionRecovery();
    }
  }

  /**
   * Attempt to recover session after page refresh
   */
  private attemptSessionRecovery(): void {
    const currentState = this.recoveryStateSubject.value;
    
    if (currentState.isRecovering || currentState.recoveryAttempts >= this.MAX_RECOVERY_ATTEMPTS) {
      return;
    }

    this.updateRecoveryState({
      isRecovering: true,
      hasRecovered: false,
      recoveryAttempts: currentState.recoveryAttempts + 1,
      lastRecoveryAttempt: new Date()
    });

    // Set recovery timeout
    const timeoutId = setTimeout(() => {
      this.handleRecoveryTimeout();
    }, this.RECOVERY_TIMEOUT);

    // Attempt to validate session with server
    this.sessionService.validateSessionWithServer().subscribe({
      next: (isValid) => {
        clearTimeout(timeoutId);
        this.handleRecoveryResult(isValid);
      },
      error: () => {
        clearTimeout(timeoutId);
        this.handleRecoveryResult(false);
      }
    });
  }

  /**
   * Handle session recovery result
   */
  private handleRecoveryResult(isValid: boolean): void {
    const currentState = this.recoveryStateSubject.value;

    if (isValid) {
      // Session recovered successfully
      this.updateRecoveryState({
        isRecovering: false,
        hasRecovered: true,
        recoveryAttempts: currentState.recoveryAttempts,
        lastRecoveryAttempt: currentState.lastRecoveryAttempt
      });
      
      console.log('Session recovered successfully');
      this.handleSuccessfulRecovery();
    } else {
      // Session recovery failed
      if (currentState.recoveryAttempts >= this.MAX_RECOVERY_ATTEMPTS) {
        this.handleRecoveryFailure();
      } else {
        // Retry after a delay
        setTimeout(() => {
          this.attemptSessionRecovery();
        }, 2000);
      }
    }
  }

  /**
   * Handle successful session recovery
   */
  private handleSuccessfulRecovery(): void {
    // Check if we need to redirect to a specific page
    const returnUrl = this.getStoredReturnUrl();
    if (returnUrl && returnUrl !== '/login') {
      this.router.navigate([returnUrl]);
      this.clearStoredReturnUrl();
    }
  }

  /**
   * Handle session recovery failure
   */
  private handleRecoveryFailure(): void {
    this.updateRecoveryState({
      isRecovering: false,
      hasRecovered: false,
      recoveryAttempts: this.MAX_RECOVERY_ATTEMPTS,
      lastRecoveryAttempt: new Date()
    });

    console.warn('Session recovery failed after maximum attempts');
    
    // Clear invalid session
    this.sessionService.logout().subscribe();
    
    // Redirect to login page
    this.router.navigate(['/login'], {
      queryParams: { 
        sessionExpired: 'true',
        reason: 'recovery_failed'
      }
    });
  }

  /**
   * Handle recovery timeout
   */
  private handleRecoveryTimeout(): void {
    console.warn('Session recovery timed out');
    this.handleRecoveryResult(false);
  }

  /**
   * Update recovery state
   */
  private updateRecoveryState(newState: Partial<SessionRecoveryState>): void {
    const currentState = this.recoveryStateSubject.value;
    this.recoveryStateSubject.next({ ...currentState, ...newState });
  }

  /**
   * Setup beforeunload handler to save current state
   */
  private setupBeforeUnloadHandler(): void {
    window.addEventListener('beforeunload', (event) => {
      this.handleBeforeUnload();
    });
  }

  /**
   * Setup visibility change handler for tab switching
   */
  private setupVisibilityChangeHandler(): void {
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) {
        // Tab became visible, check session validity
        this.checkSessionOnVisibilityChange();
      }
    });
  }

  /**
   * Handle before unload event
   */
  private handleBeforeUnload(): void {
    // Save current route for recovery
    const currentUrl = this.router.url;
    if (currentUrl && currentUrl !== '/login') {
      this.storeReturnUrl(currentUrl);
    }

    // Save session state
    this.saveSessionState();
  }

  /**
   * Check session validity when tab becomes visible
   */
  private checkSessionOnVisibilityChange(): void {
    // Only check session if we're authenticated and not in an interview session
    if (this.sessionService.isAuthenticated()) {
      // Skip validation if we're in an interview room to avoid interrupting the session
      const currentUrl = this.router.url;
      if (currentUrl.includes('/interview-room/')) {
        console.log('Skipping session validation during interview');
        return;
      }
      
      // Validate session with server to ensure it's still valid
      this.sessionService.validateSessionWithServer().subscribe({
        next: (isValid) => {
          if (!isValid) {
            console.warn('Session expired while tab was hidden');
            this.handleSessionExpired();
          }
        },
        error: (error) => {
          // Only handle session expiration for auth-related errors (401, 403)
          if (error.status === 401 || error.status === 403) {
            console.warn('Session authentication failed');
            this.handleSessionExpired();
          } else {
            // For other errors (network issues, 500, etc.), just log and continue
            console.warn('Session validation failed (network/server issue):', error.status || 'unknown');
          }
        }
      });
    }
  }

  /**
   * Handle session expiration
   */
  private handleSessionExpired(): void {
    this.sessionService.logout().subscribe();
    this.router.navigate(['/login'], {
      queryParams: { 
        sessionExpired: 'true',
        reason: 'expired'
      }
    });
  }

  /**
   * Store return URL for after login
   */
  private storeReturnUrl(url: string): void {
    try {
      sessionStorage.setItem('aria_return_url', url);
    } catch (error) {
      console.error('Failed to store return URL:', error);
    }
  }

  /**
   * Get stored return URL
   */
  private getStoredReturnUrl(): string | null {
    try {
      return sessionStorage.getItem('aria_return_url');
    } catch (error) {
      console.error('Failed to get stored return URL:', error);
      return null;
    }
  }

  /**
   * Clear stored return URL
   */
  private clearStoredReturnUrl(): void {
    try {
      sessionStorage.removeItem('aria_return_url');
    } catch (error) {
      console.error('Failed to clear stored return URL:', error);
    }
  }

  /**
   * Save current session state
   */
  private saveSessionState(): void {
    const sessionInfo = this.sessionService.getCurrentSession();
    if (sessionInfo) {
      try {
        const sessionState = {
          timestamp: Date.now(),
          userType: sessionInfo.userType,
          userId: sessionInfo.userId,
          sessionId: sessionInfo.sessionId
        };
        sessionStorage.setItem('aria_session_state', JSON.stringify(sessionState));
      } catch (error) {
        console.error('Failed to save session state:', error);
      }
    }
  }

  /**
   * Get saved session state
   */
  public getSavedSessionState(): any {
    try {
      const stateStr = sessionStorage.getItem('aria_session_state');
      if (stateStr) {
        return JSON.parse(stateStr);
      }
    } catch (error) {
      console.error('Failed to get saved session state:', error);
    }
    return null;
  }

  /**
   * Clear saved session state
   */
  public clearSavedSessionState(): void {
    try {
      sessionStorage.removeItem('aria_session_state');
    } catch (error) {
      console.error('Failed to clear saved session state:', error);
    }
  }

  /**
   * Manual session recovery trigger
   */
  public triggerRecovery(): void {
    this.attemptSessionRecovery();
  }

  /**
   * Check if recovery is possible
   */
  public canRecover(): boolean {
    const currentState = this.recoveryStateSubject.value;
    return currentState.recoveryAttempts < this.MAX_RECOVERY_ATTEMPTS && 
           !currentState.isRecovering;
  }

  /**
   * Reset recovery state
   */
  public resetRecoveryState(): void {
    this.updateRecoveryState({
      isRecovering: false,
      hasRecovered: false,
      recoveryAttempts: 0,
      lastRecoveryAttempt: undefined
    });
  }

  /**
   * Cleanup expired sessions from storage
   */
  public cleanupExpiredSessions(): void {
    try {
      const keys = Object.keys(localStorage);
      keys.forEach(key => {
        if (key.startsWith('aria_session')) {
          try {
            const value = localStorage.getItem(key);
            if (value) {
              const sessionData = JSON.parse(value);
              if (sessionData.expiresAt) {
                const expiryTime = new Date(sessionData.expiresAt).getTime();
                if (Date.now() > expiryTime) {
                  localStorage.removeItem(key);
                  console.log(`Cleaned up expired session: ${key}`);
                }
              }
            }
          } catch (error) {
            // Invalid data, remove it
            localStorage.removeItem(key);
          }
        }
      });
    } catch (error) {
      console.error('Failed to cleanup expired sessions:', error);
    }
  }
}
