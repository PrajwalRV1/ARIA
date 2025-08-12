import { Routes } from '@angular/router';
import { RecruiterRegisterLoginComponent } from './pages/recruiter-register-login/recruiter-register-login.component';
import { RecruiterDashboardComponent } from './pages/recruiter-dashboard/recruiter-dashboard.component';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    component: RecruiterRegisterLoginComponent,
    data: { mode: 'login' }
  },
  {
    path: 'register',
    component: RecruiterRegisterLoginComponent,
    data: { mode: 'register' }
  },
  {
    path: 'dashboard',
    component: RecruiterDashboardComponent
  },
  {
    path: '**',
    redirectTo: 'login'
  }
];
