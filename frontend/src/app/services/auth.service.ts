// src/app/services/auth.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subscription, timer, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Router } from '@angular/router';
import {
  LoginRequest,
  RegisterRequest,
  AuthResponse,
} from '../models/auth.model';
import { environment } from '../../environments/environment';
import { SendOtpRequest, VerifyOtpRequest, OtpResponse } from '../models/auth.model';


@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private baseUrl = `${environment.apiBaseUrl}/auth`;
  private tokenExpiryTimer?: Subscription;
  private tokenWarningTimer?: Subscription;

  sessionExpiring$ = new Subject<number>(); // emits seconds left
  sessionExtended$ = new Subject<void>(); // emits after refresh success

  constructor(private http: HttpClient, private router: Router) {}

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, payload)
      .pipe(tap((res) => this.storeTokens(res)));
  }

  register(payload: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, payload)
      .pipe(tap((res) => this.storeTokens(res)));
  }

  forgotPassword(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${this.baseUrl}/forgot-password`,
      { email }
    );
  }

  refreshSession(): Observable<AuthResponse> {
    const refreshToken = localStorage.getItem('refresh_token');
    if (!refreshToken) {
      this.forceLogout();
      throw new Error('No refresh token available');
    }

    return this.http
      .post<AuthResponse>(`${this.baseUrl}/refresh-token`, { refreshToken })
      .pipe(
        tap((res) => {
          this.storeTokens(res);
          this.sessionExtended$.next(); // notify popup/UI
        })
      );
  }

  isAuthenticated(): boolean {
    const token = localStorage.getItem('auth_token');
    if (!token) return false;

    const payload = this.decodeToken(token);
    if (!payload?.exp) {
      this.forceLogout();
      return false;
    }

    if (Date.now() >= payload.exp * 1000) {
      return false;
    }
    return true;
  }

  logout(): void {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('refresh_token');
    this.clearTimers();
  }

  private storeTokens(res: AuthResponse): void {
    localStorage.setItem('auth_token', res.token);
    if (res.refreshToken) {
      localStorage.setItem('refresh_token', res.refreshToken);
    }
    this.startTokenTimer(res.token);
  }

  private forceLogout(): void {
    this.logout();
    this.router.navigate(['/login']);
  }

  private decodeToken(token: string): any {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch {
      return null;
    }
  }

  private startTokenTimer(token: string): void {
    const payload = this.decodeToken(token);
    if (!payload?.exp) return;

    const expiryTime = payload.exp * 1000;
    const timeout = expiryTime - Date.now();

    if (timeout <= 0) {
      this.forceLogout();
      return;
    }

    this.clearTimers();

    // Auto logout
    this.tokenExpiryTimer = timer(timeout).subscribe(() => this.forceLogout());

    // Warning 5 min before expiry
    const warningTime = timeout - 300_000;
    if (warningTime > 0) {
      this.tokenWarningTimer = timer(warningTime).subscribe(() => {
        this.sessionExpiring$.next(300);
      });
    }
  }

  private clearTimers(): void {
    this.tokenExpiryTimer?.unsubscribe();
    this.tokenWarningTimer?.unsubscribe();
    this.tokenExpiryTimer = undefined;
    this.tokenWarningTimer = undefined;
  }

  initAuthTimer(): void {
    const token = localStorage.getItem('auth_token');
    if (token) {
      this.startTokenTimer(token);
    }
  }

  // Add these methods inside AuthService
  sendOtp(payload: SendOtpRequest): Observable<OtpResponse> {
    // TODO: Replace with actual backend endpoint from environment
    return this.http.post<OtpResponse>(
      `${environment.apiBaseUrl}/auth/send-otp`, // <-- replace when backend ready
      payload
    );
  }

  verifyOtp(payload: VerifyOtpRequest): Observable<OtpResponse> {
    // TODO: Replace with actual backend endpoint from environment
    return this.http.post<OtpResponse>(
      `${environment.apiBaseUrl}/auth/verify-otp`, // <-- replace when backend ready
      payload
    );
  }
}
