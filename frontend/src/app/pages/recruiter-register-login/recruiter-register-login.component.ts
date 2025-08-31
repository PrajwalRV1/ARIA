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
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { LoginRequest, RegisterRequest } from '../../models/auth.model';


@Component({
  selector: 'app-recruiter-register-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './recruiter-register-login.component.html',
  styleUrls: ['./recruiter-register-login.component.scss'],
})
export class RecruiterRegisterLoginComponent implements OnInit {
  mode: 'login' | 'register' = 'login';
  form: FormGroup;
  loading = false;
  showPassword = false;
  showConfirmPassword = false;
  errorMessage: string | null = null;

  
  // OTP handling
  otpSent = false;
  otpVerified = false;
  otpCooldown = 0;
  otpAttempts = 0;
  maxOtpAttempts = 3;
  otpTimerInterval?: any;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router
  ) {
    this.form = this.buildForm();
  }

  ngOnInit() {
    const routeMode = this.route.snapshot.data['mode'] as 'login' | 'register';
    if (routeMode) this.mode = routeMode;
    this.applyModeValidators();

    // Watch password & confirmPassword match - original logic
    this.form.valueChanges.subscribe(() => {
      if (
        this.mode === 'register' &&
        this.form.get('fullName')?.valid &&
        this.form.get('email')?.valid &&
        this.form.get('password')?.valid &&
        this.form.get('confirmPassword')?.valid &&
        !this.form.errors?.['passwordsMismatch'] &&
        !this.otpSent
      ) {
        console.log('[DEBUG] All fields valid, auto-triggering OTP send...');
        this.triggerSendOtp();
      }
    });
  }

  private buildForm(): FormGroup {
    return this.fb.group(
      {
        fullName: [''],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]],
        confirmPassword: [''],
        otp: [''],
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
      this.form.get('confirmPassword')?.setValidators([Validators.required, Validators.minLength(6)]);
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

  triggerSendOtp() {
    if (this.form.get('email')?.invalid) return;
    this.loading = true;
    this.errorMessage = null;

    this.authService.sendOtp({ email: this.form.value.email }).subscribe({
      next: () => {
        this.loading = false;
        this.otpSent = true;
        this.otpCooldown = 60;
        this.startOtpCooldown();
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Failed to send OTP. Please try again.';
      }
    });
  }

  verifyOtp() {
    if (this.otpAttempts >= this.maxOtpAttempts) {
      this.errorMessage = 'Maximum OTP attempts reached. Please restart registration.';
      return;
    }

    this.loading = true;
    this.errorMessage = null;
    this.otpAttempts++;

    this.authService.verifyOtp({
      email: this.form.value.email,
      otp: this.form.value.otp
    }).subscribe({
      next: () => {
        this.loading = false;
        this.otpVerified = true;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Incorrect OTP. Please try again.';
      }
    });
  }

  resendOtp() {
    if (this.otpCooldown > 0) return;
    this.triggerSendOtp();
  }

  private startOtpCooldown() {
    this.otpTimerInterval = setInterval(() => {
      this.otpCooldown--;
      if (this.otpCooldown <= 0) {
        clearInterval(this.otpTimerInterval);
      }
    }, 1000);
  }


  // onSubmit() {
  //   this.form.setValidators(this.passwordsMatchValidator());
  //   this.form.updateValueAndValidity();

  //   if (this.form.invalid) {
  //     this.markAllTouched();
  //     return;
  //   }

  //   this.loading = true;
  //   this.errorMessage = null;

  //   if (this.mode === 'login') {
  //     const payload: LoginRequest = {
  //       email: this.form.value.email,
  //       password: this.form.value.password,
  //       rememberMe: this.form.value.rememberMe,
  //     };
  //     this.authService.login(payload).subscribe({
  //       next: () => {
  //         this.loading = false;
  //         this.router.navigate(['/dashboard']);
  //       },
  //       error: (err) => {
  //         this.loading = false;
  //         this.errorMessage = err?.error?.message || 'Invalid credentials, please try again.';
  //       },
  //     });
  //   } else {
  //     const payload: RegisterRequest = {
  //       fullName: this.form.value.fullName,
  //       email: this.form.value.email,
  //       password: this.form.value.password,
  //     };
  //     this.authService.register(payload).subscribe({
  //       next: () => {
  //         this.loading = false;
  //         this.router.navigate(['/dashboard']);
  //       },
  //       error: (err) => {
  //         this.loading = false;
  //         this.errorMessage = err?.error?.message || 'Registration failed, please try again.';
  //       },
  //     });
  //   }
  // }

  // version 2
  onSubmit() {
    if (this.form.invalid) {
      this.markAllTouched();
      return;
    }
    if (this.mode === 'register' && !this.otpVerified) {
      this.errorMessage = 'Please verify OTP before creating account.';
      return;
    }

    this.loading = true;
    this.errorMessage = null;

    if (this.mode === 'login') {
      const payload: LoginRequest = {
        email: this.form.value.email,
        password: this.form.value.password,
        rememberMe: this.form.value.rememberMe,
      };
      this.authService.login(payload).subscribe({
        next: () => {
          this.loading = false;
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err?.error?.message || 'Invalid credentials, please try again.';
        },
      });
    } else {
      const payload: RegisterRequest = {
        fullName: this.form.value.fullName,
        email: this.form.value.email,
        password: this.form.value.password,
      };
      this.authService.register(payload).subscribe({
        next: () => {
          this.loading = false;
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err?.error?.message || 'Registration failed, please try again.';
        },
      });
    }
  }


  togglePasswordVisibility() { this.showPassword = !this.showPassword; }
  toggleConfirmPasswordVisibility() { this.showConfirmPassword = !this.showConfirmPassword; }



  onForgotPassword(event: Event) {
    event.preventDefault();
    const email = this.form.value.email;
    if (!email) {
      this.errorMessage = 'Enter your email to reset password.';
      return;
    }
    this.authService.forgotPassword(email).subscribe({
      next: (res) => console.log('[FORGOT PASSWORD]', res.message),
      error: (err) => console.error('[FORGOT PASSWORD ERROR]', err),
    });
  }

  onSocialLogin(provider: 'google' | 'linkedin') {
    console.log(`[SOCIAL] Login with ${provider}`);
  }

  get f() { return this.form.controls; }
  private markAllTouched() { Object.values(this.form.controls).forEach(c => c.markAsTouched()); }
}
