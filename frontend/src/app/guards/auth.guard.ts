import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { map, catchError, tap } from 'rxjs/operators';
import { SessionService } from '../services/session.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  constructor(
    private sessionService: SessionService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean> | Promise<boolean> | boolean {
    
    // Check if user is authenticated
    if (this.sessionService.isAuthenticated()) {
      // Check role-based access if specified in route data
      const requiredRole = route.data['requiredRole'];
      if (requiredRole) {
        const hasRole = this.sessionService.hasRole(requiredRole);
        if (!hasRole) {
          console.warn(`Access denied. Required role: ${requiredRole}`);
          this.router.navigate(['/access-denied']);
          return false;
        }
      }
      return true;
    }

    // Validate session with server
    return this.sessionService.validateSessionWithServer().pipe(
      map(isValid => {
        if (isValid) {
          // Check role-based access if specified
          const requiredRole = route.data['requiredRole'];
          if (requiredRole) {
            const hasRole = this.sessionService.hasRole(requiredRole);
            if (!hasRole) {
              console.warn(`Access denied. Required role: ${requiredRole}`);
              this.router.navigate(['/access-denied']);
              return false;
            }
          }
          return true;
        } else {
          // Redirect to login with return URL
          this.redirectToLogin(state.url);
          return false;
        }
      }),
      catchError(() => {
        // Error during validation, redirect to login
        this.redirectToLogin(state.url);
        return of(false);
      })
    );
  }

  private redirectToLogin(returnUrl: string): void {
    this.router.navigate(['/login'], {
      queryParams: { returnUrl }
    });
  }
}

@Injectable({
  providedIn: 'root'
})
export class RoleGuard implements CanActivate {

  constructor(
    private sessionService: SessionService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean> | Promise<boolean> | boolean {
    
    const requiredRole = route.data['requiredRole'];
    
    if (!requiredRole) {
      return true; // No role requirement
    }

    if (this.sessionService.hasRole(requiredRole)) {
      return true;
    }

    // Access denied
    console.warn(`Access denied. User role: ${this.sessionService.getUserInfo()?.userType}, Required role: ${requiredRole}`);
    this.router.navigate(['/access-denied']);
    return false;
  }
}
