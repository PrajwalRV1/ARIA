import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  title = 'frontend'; // Added for test compatibility
  showExpiryWarning = false;
  secondsLeft = 0;

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.authService.initAuthTimer();

    this.authService.sessionExpiring$.subscribe((seconds) => {
      this.secondsLeft = seconds;
      this.showExpiryWarning = true;
    });

    this.authService.sessionExtended$.subscribe(() => {
      this.showExpiryWarning = false; // hide popup after refresh
    });
  }

  extendSession(): void {
    this.authService.refreshSession().subscribe({
      next: () => console.log('Session extended successfully'),
      error: (err) => {
        console.error('Failed to refresh session', err);
        this.showExpiryWarning = false;
        this.authService.logout();
      }
    });
  }

  logoutNow(): void {
    this.authService.logout();
  }
}
