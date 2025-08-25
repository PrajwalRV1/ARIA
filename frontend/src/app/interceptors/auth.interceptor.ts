import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap, finalize } from 'rxjs/operators';
import { SessionService } from '../services/session.service';
import { Router } from '@angular/router';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private isRefreshing = false;
  private refreshTokenInProgress = false;

  constructor(
    private sessionService: SessionService,
    private router: Router
  ) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Skip interception for session management endpoints
    if (this.shouldSkipInterception(req)) {
      return next.handle(req);
    }

    // Add auth headers to request
    const authReq = this.addAuthHeaders(req);

    return next.handle(authReq).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 && !this.isRefreshing) {
          return this.handle401Error(authReq, next);
        } else if (error.status === 403) {
          return this.handle403Error();
        }

        return throwError(() => error);
      })
    );
  }

  /**
   * Add authentication headers to the request
   */
  private addAuthHeaders(req: HttpRequest<any>): HttpRequest<any> {
    const token = this.sessionService.getToken();
    
    if (token) {
      return req.clone({
        setHeaders: {
          'Authorization': `Bearer ${token}`,
          'X-Session-Token': token
        }
      });
    }

    return req;
  }

  /**
   * Handle 401 Unauthorized errors with token refresh
   */
  private handle401Error(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.refreshTokenInProgress) {
      this.refreshTokenInProgress = true;
      this.isRefreshing = true;

      return this.sessionService.refreshToken().pipe(
        switchMap(() => {
          // Retry original request with new token
          const newAuthReq = this.addAuthHeaders(req);
          return next.handle(newAuthReq);
        }),
        catchError((refreshError) => {
          // Refresh failed, redirect to login
          this.handleAuthenticationFailure();
          return throwError(() => refreshError);
        }),
        finalize(() => {
          this.refreshTokenInProgress = false;
          this.isRefreshing = false;
        })
      );
    } else {
      // Another request is already refreshing the token
      // Wait for it to complete and retry
      return this.sessionService.session$.pipe(
        switchMap(session => {
          if (session && session.token) {
            const newAuthReq = this.addAuthHeaders(req);
            return next.handle(newAuthReq);
          } else {
            this.handleAuthenticationFailure();
            return throwError(() => new Error('Authentication failed'));
          }
        })
      );
    }
  }

  /**
   * Handle 403 Forbidden errors
   */
  private handle403Error(): Observable<never> {
    console.warn('Access denied (403). User does not have required permissions.');
    
    // Could redirect to an "access denied" page
    // this.router.navigate(['/access-denied']);
    
    return throwError(() => new HttpErrorResponse({
      status: 403,
      statusText: 'Forbidden',
      error: { message: 'You do not have permission to access this resource' }
    }));
  }

  /**
   * Handle authentication failure
   */
  private handleAuthenticationFailure(): void {
    console.warn('Authentication failed. Redirecting to login.');
    
    // Clear session
    this.sessionService.logout().subscribe();
    
    // Redirect to login page
    this.router.navigate(['/login'], {
      queryParams: { returnUrl: this.router.url }
    });
  }

  /**
   * Check if request should skip interception
   */
  private shouldSkipInterception(req: HttpRequest<any>): boolean {
    const skipUrls = [
      '/api/sessions/login',
      '/api/sessions/refresh',
      '/api/sessions/health'
      // Interview endpoints need authentication now
    ];

    return skipUrls.some(url => req.url.includes(url)) ||
           req.url.includes('/assets/') ||
           !req.url.startsWith('/api/');
  }
}
