import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, throwError, timer, of } from 'rxjs';
import { catchError, switchMap, tap, shareReplay, takeUntil, filter } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface SessionInfo {
  token: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  userType: 'recruiter' | 'candidate' | 'ai_avatar';
  sessionId?: string;
  expiresAt?: string;
  lastAccessed?: string;
  candidateName?: string;
  position?: string;
  isTemporary?: boolean;
}

export interface LoginRequest {
  userId: string;
  userType: 'recruiter' | 'candidate' | 'ai_avatar';
  sessionId?: string;
  candidateName?: string;
  recruiterName?: string;
  position?: string;
}

@Injectable({
  providedIn: 'root'
})
export class SessionService {
  private readonly SESSION_KEY = 'aria_session';
  private readonly REFRESH_THRESHOLD = 5 * 60 * 1000; // 5 minutes before expiry
  private readonly REFRESH_INTERVAL = 15 * 60 * 1000; // Check every 15 minutes

  private sessionSubject = new BehaviorSubject<SessionInfo | null>(null);
  private refreshTimer: any;
  private isRefreshing = false;

  public session$ = this.sessionSubject.asObservable();
  public isAuthenticated$ = this.session$.pipe(
    switchMap(session => session ? of(this.isTokenValid(session)) : of(false))
  );

  constructor(private http: HttpClient, @Inject(PLATFORM_ID) private platformId: Object) {
    if (isPlatformBrowser(this.platformId)) {
      this.initializeSession();
      this.startRefreshTimer();
    }
  }

  /**
   * Initialize session from local storage and validate
   */
  private initializeSession(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return; // Skip localStorage access on server
    }
    
    try {
      const storedSession = localStorage.getItem(this.SESSION_KEY);
      if (storedSession) {
        const session: SessionInfo = JSON.parse(storedSession);
        if (this.isTokenValid(session)) {
          this.sessionSubject.next(session);
          this.validateSessionWithServer(session.token).subscribe({
            next: (isValid) => {
              if (!isValid) {
                this.clearSession();
              }
            },
            error: () => this.clearSession()
          });
        } else {
          this.clearSession();
        }
      }
    } catch (error) {
      console.error('Failed to initialize session:', error);
      this.clearSession();
    }
  }

  /**
   * Login with user credentials and create session
   */
  login(loginRequest: LoginRequest): Observable<SessionInfo> {
    const url = `${environment.sessionServiceBaseUrl}/login`;
    
    return this.http.post<SessionInfo>(url, loginRequest).pipe(
      tap(sessionInfo => {
        this.setSession(sessionInfo);
      }),
      catchError(error => {
        console.error('Login failed:', error);
        return throwError(() => error);
      }),
      shareReplay(1)
    );
  }

  /**
   * Logout and invalidate session
   */
  logout(): Observable<any> {
    const session = this.getCurrentSession();
    if (!session) {
      this.clearSession();
      return of({ message: 'Already logged out' });
    }

    const url = `${environment.sessionServiceBaseUrl}/logout`;
    const headers = this.getAuthHeaders(session.token);

    return this.http.post(url, {}, { headers }).pipe(
      tap(() => this.clearSession()),
      catchError(error => {
        console.error('Logout failed:', error);
        this.clearSession(); // Clear session even if logout fails
        return of({ message: 'Logged out (with errors)' });
      })
    );
  }

  /**
   * Logout all sessions for current user
   */
  logoutAll(): Observable<any> {
    const session = this.getCurrentSession();
    if (!session) {
      return of({ message: 'No active session' });
    }

    const url = `${environment.sessionServiceBaseUrl}/logout-all`;
    const headers = this.getAuthHeaders(session.token);

    return this.http.post(url, {}, { headers }).pipe(
      tap(() => this.clearSession()),
      catchError(error => {
        console.error('Logout all failed:', error);
        this.clearSession();
        return of({ message: 'Logged out (with errors)' });
      })
    );
  }

  /**
   * Refresh the current session token
   */
  refreshToken(): Observable<SessionInfo> {
    const session = this.getCurrentSession();
    if (!session || this.isRefreshing) {
      return throwError(() => new Error('No session to refresh or refresh already in progress'));
    }

    this.isRefreshing = true;
    const url = `${environment.sessionServiceBaseUrl}/refresh`;

    return this.http.post<SessionInfo>(url, { token: session.token }).pipe(
      tap(newSessionInfo => {
        this.setSession(newSessionInfo);
        this.isRefreshing = false;
      }),
      catchError(error => {
        console.error('Token refresh failed:', error);
        this.isRefreshing = false;
        this.clearSession();
        return throwError(() => error);
      })
    );
  }

  /**
   * Validate session with server (enhanced with detailed error handling)
   */
  validateSessionWithServer(token?: string): Observable<any> {
    const sessionToken = token || this.getCurrentSession()?.token;
    if (!sessionToken) {
      console.warn('⚠️ No token available for validation');
      return of({ valid: false, error: 'NO_TOKEN' });
    }

    const url = `${environment.sessionServiceBaseUrl}/validate`;
    const headers = this.getAuthHeaders(sessionToken);

    console.log('🔍 Validating session with server:', {
      url,
      tokenPrefix: sessionToken.substring(0, 20) + '...',
      tokenLength: sessionToken.length
    });

    return this.http.get<any>(url, { headers }).pipe(
      tap(response => {
        console.log('✅ Server validation successful:', response);
      }),
      catchError(error => {
        console.error('❌ Server validation failed:', {
          status: error.status,
          statusText: error.statusText,
          message: error.message,
          url: error.url
        });
        
        // Return detailed error information instead of just false
        let errorType = 'UNKNOWN_ERROR';
        let errorMessage = 'Unknown validation error';
        
        if (error.status === 401) {
          errorType = 'INVALID_TOKEN';
          errorMessage = 'Token is invalid or expired';
        } else if (error.status === 403) {
          errorType = 'ACCESS_DENIED';
          errorMessage = 'Access denied - insufficient permissions';
        } else if (error.status === 404) {
          errorType = 'ENDPOINT_NOT_FOUND';
          errorMessage = 'Validation endpoint not found';
        } else if (error.status === 0 || error.name === 'HttpErrorResponse') {
          errorType = 'NETWORK_ERROR';
          errorMessage = 'Network connection failed - check if backend is running';
        } else if (error.status >= 500) {
          errorType = 'SERVER_ERROR';
          errorMessage = 'Server error occurred during validation';
        }
        
        return of({
          valid: false,
          error: errorType,
          message: errorMessage,
          status: error.status,
          details: error
        });
      })
    );
  }

  /**
   * Get current session info
   */
  getCurrentSession(): SessionInfo | null {
    return this.sessionSubject.value;
  }

  /**
   * Get authentication token
   */
  getToken(): string | null {
    return this.getCurrentSession()?.token || null;
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    const session = this.getCurrentSession();
    return session ? this.isTokenValid(session) : false;
  }

  /**
   * Check if token is valid (not expired)
   */
  private isTokenValid(session: SessionInfo): boolean {
    if (!session || !session.token) {
      return false;
    }

    // Check if token is expired based on stored expiration
    if (session.expiresAt) {
      const expirationTime = new Date(session.expiresAt).getTime();
      return Date.now() < expirationTime;
    }

    return true; // Assume valid if no expiration info
  }

  /**
   * Set session info and store in localStorage
   */
  private setSession(sessionInfo: SessionInfo): void {
    // Calculate expiration time
    const expirationTime = Date.now() + (sessionInfo.expiresIn * 1000);
    const sessionWithExpiry = {
      ...sessionInfo,
      expiresAt: new Date(expirationTime).toISOString()
    };

    try {
      if (isPlatformBrowser(this.platformId)) {
        localStorage.setItem(this.SESSION_KEY, JSON.stringify(sessionWithExpiry));
      }
      this.sessionSubject.next(sessionWithExpiry);
    } catch (error) {
      console.error('Failed to store session:', error);
    }
  }

  /**
   * Clear session from memory and storage
   */
  private clearSession(): void {
    try {
      if (isPlatformBrowser(this.platformId)) {
        localStorage.removeItem(this.SESSION_KEY);
      }
      this.sessionSubject.next(null);
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer);
      }
    } catch (error) {
      console.error('Failed to clear session:', error);
    }
  }

  /**
   * Get authorization headers for HTTP requests
   */
  private getAuthHeaders(token: string): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'X-Session-Token': token
    });
  }

  /**
   * Start automatic token refresh timer
   */
  private startRefreshTimer(): void {
    this.refreshTimer = setInterval(() => {
      const session = this.getCurrentSession();
      if (session && this.shouldRefreshToken(session)) {
        this.refreshToken().subscribe({
          next: () => console.log('Token refreshed automatically'),
          error: (error) => console.error('Auto token refresh failed:', error)
        });
      }
    }, this.REFRESH_INTERVAL);
  }

  /**
   * Check if token should be refreshed
   */
  private shouldRefreshToken(session: SessionInfo): boolean {
    if (!session.expiresAt) {
      return false;
    }

    const expirationTime = new Date(session.expiresAt).getTime();
    const timeUntilExpiry = expirationTime - Date.now();
    
    return timeUntilExpiry <= this.REFRESH_THRESHOLD && timeUntilExpiry > 0;
  }

  /**
   * Get user info from current session (enhanced for token-based access)
   */
  getUserInfo(): { userId: string; userType: string; sessionId?: string; candidateName?: string; position?: string; isTokenBased?: boolean } | null {
    const session = this.getCurrentSession();
    if (session) {
      return {
        userId: session.userId,
        userType: session.userType,
        sessionId: session.sessionId,
        candidateName: session.candidateName,
        position: session.position,
        isTokenBased: session.isTemporary || false
      };
    }
    
    // Fallback to interview token info
    const tokenInfo = this.getInterviewTokenInfo();
    if (tokenInfo) {
      return {
        userId: tokenInfo.userId,
        userType: tokenInfo.userType,
        sessionId: tokenInfo.sessionId,
        candidateName: tokenInfo.candidateName,
        position: tokenInfo.position,
        isTokenBased: true
      };
    }
    
    return null;
  }

  /**
   * Get current user information (alias for getUserInfo for backward compatibility)
   */
  getCurrentUser(): { id: string; name: string; email: string; userType: string } | null {
    const userInfo = this.getUserInfo();
    if (userInfo) {
      return {
        id: userInfo.userId,
        name: userInfo.candidateName || 'User',
        email: 'user@example.com', // Default email since we don't store email in session
        userType: userInfo.userType
      };
    }
    return null;
  }

  /**
   * Check if current user has specific role
   */
  hasRole(role: 'recruiter' | 'candidate' | 'ai_avatar'): boolean {
    const session = this.getCurrentSession();
    return session?.userType === role;
  }

  /**
   * Get session expiry information
   */
  getSessionExpiry(): { expiresAt: Date; timeRemaining: number } | null {
    const session = this.getCurrentSession();
    if (!session?.expiresAt) {
      return null;
    }

    const expiresAt = new Date(session.expiresAt);
    const timeRemaining = expiresAt.getTime() - Date.now();

    return {
      expiresAt,
      timeRemaining: Math.max(0, timeRemaining)
    };
  }

  /**
   * Validate interview token for direct access (JWT only) - Enhanced with detailed error handling
   */
  async validateInterviewToken(token: string, sessionId: string): Promise<any> {
    try {
      console.log('🔐 Validating JWT interview token:', { 
        tokenPrefix: token?.substring(0, 40) + '...', 
        sessionId,
        tokenLength: token?.length 
      });
      
      // Basic validation
      if (!token || !sessionId) {
        const error = new Error('Missing token or session ID');
        error.name = 'MISSING_CREDENTIALS';
        throw error;
      }
      
      // Validate JWT token with backend server
      const validationResponse = await this.validateSessionWithServer(token).toPromise();
      
      if (validationResponse && validationResponse.valid !== false) {
        console.log('✅ JWT token validation successful:', validationResponse);
        
        // Extract information from validation response
        const userType = validationResponse.userType || 'candidate';
        const userId = validationResponse.userId || `candidate_${sessionId}`;
        const candidateName = this.extractCandidateNameFromToken(token) || validationResponse.candidateName || 'Interview Candidate';
        
        return {
          valid: true,
          userType: userType,
          userId: userId,
          candidateName: candidateName,
          position: validationResponse.position || 'Interview Candidate',
          sessionId: sessionId,
          expiresAt: validationResponse.expiresAt || new Date(Date.now() + 4 * 60 * 60 * 1000).toISOString() // 4 hours
        };
      }
      
      // Handle validation response errors
      if (validationResponse && !validationResponse.valid) {
        const errorType = validationResponse.error || 'VALIDATION_FAILED';
        const errorMessage = validationResponse.message || 'Token validation failed';
        
        console.error('❌ Token validation failed on server:', {
          error: errorType,
          message: errorMessage,
          status: validationResponse.status
        });
        
        const error = new Error(errorMessage);
        error.name = errorType;
        (error as any).status = validationResponse.status;
        (error as any).details = validationResponse.details;
        throw error;
      }
      
      // Fallback error
      const error = new Error('JWT token validation failed - no valid response');
      error.name = 'VALIDATION_FAILED';
      throw error;
      
    } catch (error: any) {
      console.error('❌ JWT token validation failed:', {
        name: error?.name,
        message: error?.message,
        status: error?.status
      });
      
      // Re-throw with consistent error structure
      const enhancedError = new Error(error?.message || 'Token validation failed');
      enhancedError.name = error?.name || 'VALIDATION_ERROR';
      (enhancedError as any).status = error?.status;
      (enhancedError as any).details = error?.details;
      
      throw enhancedError;
    }
  }
  
  /**
   * Extract candidate name from JWT token payload (if available)
   */
  private extractCandidateNameFromToken(token: string): string | null {
    try {
      // Decode JWT payload (middle section)
      const parts = token.split('.');
      if (parts.length !== 3) {
        return null;
      }
      
      const payload = JSON.parse(atob(parts[1]));
      return payload.candidateName || null;
    } catch (error) {
      console.warn('Could not extract candidate name from token:', error);
      return null;
    }
  }

  /**
   * Set interview token information
   */
  setInterviewTokenInfo(tokenInfo: any): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem('aria_interview_token', JSON.stringify(tokenInfo));
    }
    
    // Create a temporary session for the interview
    const tempSession: SessionInfo = {
      token: tokenInfo.token,
      tokenType: 'Bearer',
      expiresIn: 86400,
      userId: tokenInfo.userId,
      userType: tokenInfo.userType,
      sessionId: tokenInfo.sessionId,
      expiresAt: tokenInfo.expiresAt,
      candidateName: tokenInfo.candidateName,
      position: tokenInfo.position,
      isTemporary: true
    };
    
    this.sessionSubject.next(tempSession);
    console.log('Interview token session created');
  }

  /**
   * Get interview token information
   */
  getInterviewTokenInfo(): any {
    if (!isPlatformBrowser(this.platformId)) {
      return null; // Return null on server to prevent localStorage errors
    }
    
    try {
      const tokenInfo = localStorage.getItem('aria_interview_token');
      return tokenInfo ? JSON.parse(tokenInfo) : null;
    } catch (error) {
      console.error('Failed to get interview token info:', error);
      return null;
    }
  }


  /**
   * Clear all session data including interview tokens
   */
  clearAllSessionData(): void {
    this.clearSession();
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem('aria_interview_token');
    }
    console.log('All session data cleared');
  }

  /**
   * Destroy service and cleanup timers
   */
  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
  }
}

