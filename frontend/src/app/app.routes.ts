import { Routes } from '@angular/router';
import { RecruiterRegisterLoginComponent } from './pages/recruiter-register-login/recruiter-register-login.component';
import { RecruiterDashboardComponent } from './pages/recruiter-dashboard/recruiter-dashboard.component';
import { QuestionBankDashboardComponent } from './pages/question-bank-dashboard/question-bank-dashboard.component';
import { authGuard } from './guards/auth-guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: RecruiterRegisterLoginComponent, data: { mode: 'login' } },
  { path: 'register', component: RecruiterRegisterLoginComponent, data: { mode: 'register' } },
  { path: 'dashboard', component: RecruiterDashboardComponent, canActivate: [authGuard] },
  { path: 'question-bank', component: QuestionBankDashboardComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: 'login' }
];
