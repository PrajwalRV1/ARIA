import { Routes } from '@angular/router';

/**
 * Lazy-loaded routes configuration for optimal performance
 * Only loads modules when they are actually needed
 */
export const lazyRoutes: Routes = [
  {
    path: '',
    redirectTo: '/login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => 
      import('./pages/recruiter-register-login/recruiter-register-login.component')
        .then(m => m.RecruiterRegisterLoginComponent),
    title: 'ARIA - Login'
  },
  {
    path: 'register',
    loadComponent: () => 
      import('./pages/recruiter-register-login/recruiter-register-login.component')
        .then(m => m.RecruiterRegisterLoginComponent),
    title: 'ARIA - Register',
    data: { mode: 'register' }
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent),
    title: 'ARIA - Dashboard',
    // Add guard for authentication
    canActivate: [() => {
      // Check if user is authenticated
      if (typeof window !== 'undefined' && localStorage.getItem('auth_token')) {
        return true;
      }
      // Redirect to login if not authenticated
      return false;
    }]
  },
  {
    path: 'candidates',
    loadChildren: () => 
      import('./pages/candidate-management/candidate.routes').then(m => m.candidateRoutes),
    title: 'ARIA - Candidate Management',
    canActivate: [() => {
      if (typeof window !== 'undefined' && localStorage.getItem('auth_token')) {
        return true;
      }
      return false;
    }]
  },
  {
    path: 'interview',
    loadChildren: () =>
      import('./pages/interview-session/interview.routes').then(m => m.interviewRoutes),
    title: 'ARIA - Interview Session',
    canActivate: [() => {
      if (typeof window !== 'undefined' && localStorage.getItem('auth_token')) {
        return true;
      }
      return false;
    }]
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./pages/analytics/analytics.component').then(m => m.AnalyticsComponent),
    title: 'ARIA - Analytics',
    canActivate: [() => {
      if (typeof window !== 'undefined' && localStorage.getItem('auth_token')) {
        return true;
      }
      return false;
    }]
  },
  // Catch-all route
  {
    path: '**',
    loadComponent: () =>
      import('./pages/not-found/not-found.component').then(m => m.NotFoundComponent),
    title: 'ARIA - Page Not Found'
  }
];

/**
 * Preloading strategy for lazy routes
 * Only preload critical routes to balance performance and user experience
 */
export const criticalRoutes = [
  'dashboard',
  'candidates'
];

/**
 * Routes that should be preloaded after the initial load
 */
export const secondaryRoutes = [
  'analytics',
  'interview'
];
