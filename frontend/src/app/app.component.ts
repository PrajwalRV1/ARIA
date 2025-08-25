import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { AuthService } from './services/auth.service';
import { InterviewService } from './services/interview.service';

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

  constructor(
    private authService: AuthService,
    private router: Router,
    private interviewService: InterviewService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    this.authService.initAuthTimer();

    this.authService.sessionExpiring$.subscribe((seconds) => {
      this.secondsLeft = seconds;
      this.showExpiryWarning = true;
    });

    this.authService.sessionExtended$.subscribe(() => {
      this.showExpiryWarning = false; // hide popup after refresh
    });

    // Set up global function for notification buttons
    this.setupGlobalJoinFunction();
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

  /**
   * Set up global function for joining interviews from notification buttons
   */
  private setupGlobalJoinFunction(): void {
    // Only set up the global function in the browser (not during SSR)
    if (isPlatformBrowser(this.platformId)) {
      // Make the function available globally for notification buttons
      (window as any).joinInterviewFromNotification = (sessionId: string) => {
        console.log('🚀 Joining interview from notification:', sessionId);
        
        try {
          // Use the interview service to get the join URL
          const joinUrl = this.interviewService.joinInterview(sessionId);
          console.log('✅ Navigating to interview room:', joinUrl);
          
          // Navigate to the interview room
          this.router.navigateByUrl(joinUrl);
          
        } catch (error) {
          console.error('❌ Error joining interview:', error);
          // Fallback: navigate directly to interview room
          this.router.navigate(['/interview-room', sessionId]);
        }
      };
    }
  }
}
