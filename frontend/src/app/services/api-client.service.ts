import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { catchError, retry, timeout } from 'rxjs/operators';
import { environment } from '../../environments/environment';

/**
 * Centralized API Client Service for ARIA Platform
 * Handles communication with all backend services including:
 * - User Management Service
 * - Interview Orchestrator Service  
 * - AI Services (Speech, Analytics, Adaptive Engine)
 * - Railway services (TTS, Voice Synthesis, etc.)
 */
@Injectable({
  providedIn: 'root'
})
export class ApiClientService {
  
  private readonly timeout = 30000; // 30 seconds
  private readonly retryCount = 2;
  
  // Service status tracking
  private servicesStatus = new BehaviorSubject<{[key: string]: 'healthy' | 'unhealthy' | 'unknown'}>({
    userManagement: 'unknown',
    interviewOrchestrator: 'unknown', 
    speechService: 'unknown',
    analyticsService: 'unknown',
    adaptiveEngine: 'unknown'
  });

  public servicesStatus$ = this.servicesStatus.asObservable();

  constructor(private http: HttpClient) {
    this.initializeHealthChecks();
  }

  /**
   * Initialize periodic health checks for all services
   */
  private initializeHealthChecks(): void {
    // Check service health every 5 minutes
    setInterval(() => {
      this.checkAllServicesHealth();
    }, 5 * 60 * 1000);
    
    // Initial health check
    this.checkAllServicesHealth();
  }

  /**
   * Check health status of all services
   */
  private async checkAllServicesHealth(): Promise<void> {
    const services = {
      userManagement: `${environment.apiBaseUrl.replace('/api', '')}/actuator/health`,
      interviewOrchestrator: `${environment.aiServices.orchestratorBaseUrl.replace('/api/interview', '')}/api/interview/actuator/health`,
      speechService: `${environment.aiServices.speechServiceBaseUrl}/health`,
      analyticsService: `${environment.aiServices.analyticsServiceBaseUrl}/health`,
      adaptiveEngine: `${environment.aiServices.adaptiveEngineBaseUrl}/health`
    };

    const currentStatus = { ...this.servicesStatus.value };

    for (const [serviceName, healthUrl] of Object.entries(services)) {
      try {
        await this.http.get(healthUrl, { 
          headers: this.getHeaders(),
          timeout: 5000 
        }).toPromise();
        currentStatus[serviceName] = 'healthy';
      } catch (error) {
        currentStatus[serviceName] = 'unhealthy';
        console.warn(`Service ${serviceName} health check failed:`, error);
      }
    }

    this.servicesStatus.next(currentStatus);
  }

  /**
   * Get standard HTTP headers with CORS support
   */
  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      // Add any authentication headers here if needed
    });
  }

  /**
   * Handle HTTP errors with retry logic
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'An unknown error occurred';
    
    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorMessage = `Client Error: ${error.error.message}`;
    } else {
      // Server-side error
      errorMessage = `Server Error: ${error.status} - ${error.message}`;
      
      // Handle specific CORS errors
      if (error.status === 0) {
        errorMessage = 'CORS Error: Unable to connect to service. Please check if the service is running and CORS is configured.';
      }
    }

    console.error('API Error:', errorMessage, error);
    return throwError(errorMessage);
  }

  // ========================================
  // USER MANAGEMENT SERVICE METHODS
  // ========================================

  /**
   * Send OTP for authentication
   */
  sendOtp(email: string): Observable<any> {
    return this.http.post(
      `${environment.apiBaseUrl}/auth/send-otp`,
      { email },
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * Verify OTP
   */
  verifyOtp(email: string, otp: string): Observable<any> {
    return this.http.post(
      `${environment.apiBaseUrl}/auth/verify-otp`,
      { email, otp },
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * User login
   */
  login(credentials: any): Observable<any> {
    return this.http.post(
      `${environment.apiBaseUrl}/auth/login`,
      credentials,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * User registration
   */
  register(userData: any): Observable<any> {
    return this.http.post(
      `${environment.apiBaseUrl}/auth/register`,
      userData,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  // ========================================
  // INTERVIEW ORCHESTRATOR SERVICE METHODS
  // ========================================

  /**
   * Schedule an interview
   */
  scheduleInterview(interviewData: any): Observable<any> {
    return this.http.post(
      `${environment.aiServices.orchestratorBaseUrl}/schedule`,
      interviewData,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * Start an interview session
   */
  startInterview(sessionId: string): Observable<any> {
    return this.http.post(
      `${environment.aiServices.orchestratorBaseUrl}/session/${sessionId}/start`,
      {},
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * Get interview session details
   */
  getInterviewSession(sessionId: string): Observable<any> {
    return this.http.get(
      `${environment.aiServices.orchestratorBaseUrl}/session/${sessionId}`,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  // ========================================
  // SPEECH SERVICE METHODS
  // ========================================

  /**
   * Submit audio for transcription
   */
  transcribeAudio(audioData: any): Observable<any> {
    return this.http.post(
      `${environment.aiServices.speechServiceBaseUrl}/transcript`,
      audioData,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * Get transcript for a session
   */
  getTranscript(sessionId: string): Observable<any> {
    return this.http.get(
      `${environment.aiServices.speechServiceBaseUrl}/transcript/${sessionId}`,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  // ========================================
  // ADAPTIVE ENGINE SERVICE METHODS
  // ========================================

  /**
   * Get next question from adaptive engine
   */
  getNextQuestion(questionRequest: any): Observable<any> {
    return this.http.post(
      `${environment.aiServices.adaptiveEngineBaseUrl}/next-question`,
      questionRequest,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * Update candidate theta based on response
   */
  updateTheta(thetaRequest: any): Observable<any> {
    return this.http.post(
      `${environment.aiServices.adaptiveEngineBaseUrl}/update-theta`,
      thetaRequest,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * Get job-aware next question
   */
  getJobAwareQuestion(jobRequest: any): Observable<any> {
    return this.http.post(
      `${environment.aiServices.adaptiveEngineBaseUrl}/next-question-job-aware`,
      jobRequest,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  // ========================================
  // ANALYTICS SERVICE METHODS
  // ========================================

  /**
   * Analyze video data
   */
  analyzeVideo(videoAnalysisRequest: any): Observable<any> {
    return this.http.post(
      `${environment.aiServices.analyticsServiceBaseUrl}/analyze/video`,
      videoAnalysisRequest,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(60000), // Longer timeout for video analysis
      retry(1), // Fewer retries for heavy operations
      catchError(this.handleError)
    );
  }

  /**
   * Analyze text data
   */
  analyzeText(textAnalysisRequest: any): Observable<any> {
    return this.http.post(
      `${environment.aiServices.analyticsServiceBaseUrl}/analyze/text`,
      textAnalysisRequest,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * Detect bias in content
   */
  detectBias(biasRequest: any): Observable<any> {
    return this.http.post(
      `${environment.aiServices.analyticsServiceBaseUrl}/analyze/bias`,
      biasRequest,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  /**
   * Get session analytics
   */
  getSessionAnalytics(sessionId: string): Observable<any> {
    return this.http.get(
      `${environment.aiServices.analyticsServiceBaseUrl}/analytics/session/${sessionId}`,
      { headers: this.getHeaders() }
    ).pipe(
      timeout(this.timeout),
      retry(this.retryCount),
      catchError(this.handleError)
    );
  }

  // ========================================
  // UTILITY METHODS
  // ========================================

  /**
   * Test CORS configuration for a service
   */
  testCors(serviceUrl: string): Observable<any> {
    return this.http.get(`${serviceUrl}/health`, { 
      headers: this.getHeaders() 
    }).pipe(
      timeout(5000),
      catchError((error) => {
        console.error(`CORS test failed for ${serviceUrl}:`, error);
        return throwError(error);
      })
    );
  }

  /**
   * Get current service status
   */
  getServicesStatus(): {[key: string]: 'healthy' | 'unhealthy' | 'unknown'} {
    return this.servicesStatus.value;
  }

  /**
   * Check if a specific service is healthy
   */
  isServiceHealthy(serviceName: string): boolean {
    return this.servicesStatus.value[serviceName] === 'healthy';
  }

  /**
   * Get service URLs for debugging
   */
  getServiceUrls(): {[key: string]: string} {
    return {
      userManagement: environment.apiBaseUrl,
      interviewOrchestrator: environment.aiServices.orchestratorBaseUrl,
      speechService: environment.aiServices.speechServiceBaseUrl,
      analyticsService: environment.aiServices.analyticsServiceBaseUrl,
      adaptiveEngine: environment.aiServices.adaptiveEngineBaseUrl,
      alexAi: environment.aiServices.alexAiServiceUrl,
      mozillaTts: environment.aiServices.mozillaTtsServiceBaseUrl,
      voiceSynthesis: environment.aiServices.voiceSynthesisBaseUrl,
      voiceIsolation: environment.aiServices.voiceIsolationBaseUrl
    };
  }
}
