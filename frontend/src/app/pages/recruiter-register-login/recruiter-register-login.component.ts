import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
  AbstractControl,
  ValidatorFn,
} from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { LoginRequest, RegisterRequest } from '../../models/auth.model';

@Component({
  selector: 'app-recruiter-register-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, HttpClientModule],
  templateUrl: './recruiter-register-login.component.html',
  styleUrls: ['./recruiter-register-login.component.scss'],
})
export class RecruiterRegisterLoginComponent implements OnInit {
  mode: 'login' | 'register' = 'login';
  form: FormGroup;
  loading = false;
  showPassword = false;
  showConfirmPassword = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private route: ActivatedRoute
  ) {
    this.form = this.buildForm();
  }

  ngOnInit() {
    // Detect mode from route data (if provided)
    const routeMode = this.route.snapshot.data['mode'] as 'login' | 'register';
    if (routeMode) {
      this.mode = routeMode;
    }
    this.applyModeValidators();
  }

  private buildForm(): FormGroup {
    return this.fb.group(
      {
        fullName: [''],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]],
        confirmPassword: [''],
        rememberMe: [false],
      },
      { validators: this.passwordsMatchValidator() }
    );
  }

  toggleMode() {
    this.mode = this.mode === 'login' ? 'register' : 'login';
    this.applyModeValidators();
  }

  private applyModeValidators() {
    if (this.mode === 'register') {
      this.form.get('fullName')?.setValidators([Validators.required]);
      this.form
        .get('confirmPassword')
        ?.setValidators([Validators.required, Validators.minLength(6)]);
    } else {
      this.form.get('fullName')?.clearValidators();
      this.form.get('confirmPassword')?.clearValidators();
    }
    this.form.get('fullName')?.updateValueAndValidity();
    this.form.get('confirmPassword')?.updateValueAndValidity();
    this.form.updateValueAndValidity();
  }

  private passwordsMatchValidator(): ValidatorFn {
    return (group: AbstractControl) => {
      const password = group.get('password')?.value;
      const confirm = group.get('confirmPassword')?.value;
      if (this.mode === 'register' && password && confirm && password !== confirm) {
        return { passwordsMismatch: true };
      }
      return null;
    };
  }

  onSubmit() {
    this.form.setValidators(this.passwordsMatchValidator());
    this.form.updateValueAndValidity();

    if (this.form.invalid) {
      this.markAllTouched();
      console.log('Form invalid', this.form.errors, this.form.value);
      return;
    }

    this.loading = true;

    if (this.mode === 'login') {
      const payload: LoginRequest = {
        email: this.form.value.email,
        password: this.form.value.password,
        rememberMe: this.form.value.rememberMe,
      };
      this.authService.login(payload).subscribe({
        next: (res) => {
          console.log('[LOGIN SUCCESS]', res);
          this.loading = false;
          // TODO: Save token & navigate
        },
        error: (err) => {
          console.error('[LOGIN ERROR]', err);
          this.loading = false;
        },
      });
    } else {
      const payload: RegisterRequest = {
        fullName: this.form.value.fullName,
        email: this.form.value.email,
        password: this.form.value.password,
      };
      this.authService.register(payload).subscribe({
        next: (res) => {
          console.log('[REGISTER SUCCESS]', res);
          this.loading = false;
          // TODO: Save token & navigate
        },
        error: (err) => {
          console.error('[REGISTER ERROR]', err);
          this.loading = false;
        },
      });
    }
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  toggleConfirmPasswordVisibility() {
    this.showConfirmPassword = !this.showConfirmPassword;
  }

  onForgotPassword(event: Event) {
    event.preventDefault();
    const email = this.form.value.email;
    if (!email) {
      console.warn('Enter your email to reset password');
      return;
    }
    this.authService.forgotPassword(email).subscribe({
      next: (res) => {
        console.log('[FORGOT PASSWORD]', res.message);
      },
      error: (err) => {
        console.error('[FORGOT PASSWORD ERROR]', err);
      },
    });
  }

  onSocialLogin(provider: 'google' | 'linkedin') {
    console.log(`[SOCIAL] Login with ${provider}`);
  }

  get f() {
    return this.form.controls;
  }

  private markAllTouched() {
    Object.values(this.form.controls).forEach((control) =>
      control.markAsTouched()
    );
  }
}
