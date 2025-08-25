import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { catchError, map, of } from 'rxjs';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  if (isPlatformBrowser(platformId)) {
    if (authService.isAuthenticated()) {
      return true;
    }

    const refreshToken = localStorage.getItem('refresh_token');
    if (refreshToken) {
      return authService.refreshSession().pipe(
        map(() => true),
        catchError(() => {
          authService.logout();
          router.navigate(['/login']);
          return of(false);
        })
      );
    }

    router.navigate(['/login']);
    return false;
  }
  return false;
};
