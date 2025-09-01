import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, BehaviorSubject, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { catchError, map } from 'rxjs/operators';

// Interview related interfaces
export interface InterviewScheduleRequest {
  candidateId: number;
  candidateName: string;
  candidateEmail: string;
  recruiterId: number;
  recruiterName: string;
  recruiterEmail: string;
  scheduledStartTime: string; // ISO date string
  jobRole: string;
  experienceLevel: string;
  requiredTechnologies: string[];
  customQuestionPool?: number[];
  minQuestions?: number;
  maxQuestions?: number;
  interviewType?: 'ADAPTIVE_AI' | 'STRUCTURED' | 'MIXED';
  languagePreference?: string;
  enableBiasDetection?: boolean;
  enableCodeChallenges?: boolean;
  enableVideoAnalytics?: boolean;
}

export interface InterviewSessionResponse {
  sessionId: string;
  candidateId: number;
  recruiterId: number;
  status: string;
  scheduledStartTime: string;
  actualStartTime?: string;
  endTime?: string;
  durationMinutes?: number;
  meetingLink: string;
  webrtcRoomId: string;
  iceServers?: string[];
  theta?: number;
  standardError?: number;
  currentQuestionCount?: number;
  minQuestions?: number;
  maxQuestions?: number;
  currentQuestionId?: number;
  jobRole: string;
  experienceLevel: string;
  requiredTechnologies: string[];
  interviewType: string;
  languagePreference: string;
  biasScore?: number;
  engagementScore?: number;
  technicalScore?: number;
  communicationScore?: number;
  aiMetrics?: { [key: string]: number };
  canStart: boolean;
  canTerminateEarly: boolean;
  isMaxQuestionsReached: boolean;
  nextAction: string;
  createdAt: string;
  updatedAt: string;
}

export interface Question {
  id: number;
  text: string;
  type: 'technical' | 'behavioral' | 'coding';
  difficulty: 'Easy' | 'Medium' | 'Hard';
  estimatedSeconds: number;
  coding_required: boolean;
  programming_language?: string;
  followUpQuestions?: string[];
  category?: string;
  constraints?: string;
  examples?: string;
  hints?: string;
}

export interface ResponseSubmission {
  questionId: number;
  transcriptText: string;
  codeSubmission?: string;
  chatMessages?: any[];
  responseStartTime?: Date;
  responseEndTime?: Date;
  spacebarPressed?: boolean;
}

export interface NextQuestionResult {
  nextQuestion?: Question;
  shouldEnd: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class InterviewService {
  private readonly API_BASE = environment.aiServices.orchestratorBaseUrl; // Use environment configuration
  private readonly INTERVIEW_API = this.API_BASE; // Fixed: Remove duplicate /interview since orchestratorBaseUrl already includes it
  
  // AI Avatar Service for structured interview flow
  private readonly AI_AVATAR_API = environment.aiServices.aiAvatarServiceUrl; // Use environment configuration
  
  // State management
  private currentSessionSubject = new BehaviorSubject<InterviewSessionResponse | null>(null);
  public currentSession$ = this.currentSessionSubject.asObservable();

  constructor(private http: HttpClient, @Inject(PLATFORM_ID) private platformId: Object) { }

  /**
   * Get HTTP headers with JWT authorization
   */
  private getAuthHeaders(): HttpHeaders {
    let headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });
    
    if (isPlatformBrowser(this.platformId)) {
      const token = localStorage.getItem('auth_token');
      if (token) {
        headers = headers.set('Authorization', `Bearer ${token}`);
      }
    }
    
    return headers;
  }

  /**
   * Schedule a new interview session
   */
  scheduleInterview(request: InterviewScheduleRequest): Observable<InterviewSessionResponse> {
    console.log('Scheduling interview with request:', request);
    
    return this.http.post<InterviewSessionResponse>(`${this.INTERVIEW_API}/schedule`, request, { headers: this.getAuthHeaders() })
      .pipe(
        map(response => {
          console.log('Interview scheduled successfully:', response);
          this.currentSessionSubject.next(response);
          return response;
        }),
        catchError(error => {
          console.error('Error scheduling interview:', error);
          console.error('Error details:', {
            status: error.status,
            statusText: error.statusText,
            error: error.error,
            url: error.url
          });
          
          // Provide specific error messages based on error type
          let errorMessage = 'Failed to schedule interview. Please try again.';
          
          if (error.status === 0) {
            errorMessage = 'Cannot connect to interview service. Please check if the service is running.';
          } else if (error.status === 400) {
            // Enhanced 400 error handling for validation errors
            if (error.error?.message) {
              if (error.error.message.includes('scheduledStartTime')) {
                errorMessage = 'Interview must be scheduled for a future date and time. Please select a later time.';
              } else {
                errorMessage = `Validation Error: ${error.error.message}`;
              }
            } else {
              errorMessage = 'Invalid interview request data. Please check all required fields.';
            }
          } else if (error.status === 500) {
            errorMessage = error.error?.message || 'Server error occurred while scheduling interview. Please try again later.';
          } else if (error.error && error.error.message) {
            errorMessage = error.error.message;
          }
          
          return throwError(() => new Error(errorMessage));
        })
      );
  }

  /**
   * Get interview session by ID
   */
  getInterviewSession(sessionId: string): Promise<InterviewSessionResponse> {
    return this.http.get<InterviewSessionResponse>(`${this.INTERVIEW_API}/session/${sessionId}`, { headers: this.getAuthHeaders() })
      .pipe(
        map(response => {
          this.currentSessionSubject.next(response);
          return response;
        }),
        catchError(error => {
          console.error('Error fetching interview session:', error);
          return throwError(() => new Error('Failed to load interview session.'));
        })
      ).toPromise() as Promise<InterviewSessionResponse>;
  }

  /**
   * Start an interview session
   */
  startInterview(sessionId: string): Promise<void> {
    return this.http.post<InterviewSessionResponse>(`${this.INTERVIEW_API}/session/${sessionId}/start`, {}, { headers: this.getAuthHeaders() })
      .pipe(
        map(response => {
          this.currentSessionSubject.next(response);
          console.log('Interview started:', response);
        }),
        catchError(error => {
          console.error('Error starting interview:', error);
          return throwError(() => new Error('Failed to start interview.'));
        })
      ).toPromise() as Promise<void>;
  }

  /**
   * End an interview session
   */
  endInterview(sessionId: string): Promise<void> {
    return this.http.post<InterviewSessionResponse>(`${this.INTERVIEW_API}/session/${sessionId}/end`, {}, { headers: this.getAuthHeaders() })
      .pipe(
        map(response => {
          this.currentSessionSubject.next(response);
          console.log('Interview ended:', response);
        }),
        catchError(error => {
          console.error('Error ending interview:', error);
          return throwError(() => new Error('Failed to end interview.'));
        })
      ).toPromise() as Promise<void>;
  }

  /**
   * Get current question for session
   */
  getCurrentQuestion(sessionId: string): Promise<Question> {
    return this.http.get<Question>(`${this.INTERVIEW_API}/session/${sessionId}/current-question`, { headers: this.getAuthHeaders() })
      .pipe(
        catchError(error => {
          console.error('Error fetching current question:', error);
          return throwError(() => new Error('Failed to load current question.'));
        })
      ).toPromise() as Promise<Question>;
  }

  /**
   * Submit response and get next question
   */
  submitResponse(sessionId: string, response: ResponseSubmission): Promise<NextQuestionResult> {
    // Format dates to strings that the backend can parse
    const formattedResponse = {
      ...response,
      responseStartTime: response.responseStartTime ? this.formatDateForBackend(response.responseStartTime) : undefined,
      responseEndTime: response.responseEndTime ? this.formatDateForBackend(response.responseEndTime) : undefined
    };
    
    console.log('📤 Sending formatted response:', {
      questionId: formattedResponse.questionId,
      transcriptLength: formattedResponse.transcriptText?.length || 0,
      hasCode: !!formattedResponse.codeSubmission,
      responseStartTime: formattedResponse.responseStartTime,
      responseEndTime: formattedResponse.responseEndTime
    });
    
    return this.http.post<NextQuestionResult>(`${this.INTERVIEW_API}/session/${sessionId}/response`, formattedResponse, { headers: this.getAuthHeaders() })
      .pipe(
        catchError(error => {
          console.error('Error submitting response:', error);
          console.error('Response payload that failed:', formattedResponse);
          return throwError(() => new Error('Failed to submit response.'));
        })
      ).toPromise() as Promise<NextQuestionResult>;
  }
  
  /**
   * Format date for backend compatibility
   */
  private formatDateForBackend(date: Date | string): string {
    if (typeof date === 'string') {
      return date;
    }
    
    // Format as ISO string without milliseconds for backend compatibility
    return date.toISOString().split('.')[0]; // Removes milliseconds, keeps format like: 2025-08-22T14:30:00
  }

  /**
   * Create a Jitsi Meet meeting room
   */
  createMeetingRoom(sessionId: string, candidateName: string): Observable<{roomUrl: string, roomName: string}> {
    const request = {
      sessionId,
      candidateName,
      roomConfig: {
        // Jitsi Meet room configuration
        enableWelcomePage: false,
        enableChat: true,
        enableScreenshare: true,
        enableRecording: false, // Free Jitsi Meet doesn't support recording
        maxParticipants: 5 // Candidate, Recruiter, AI Avatar, etc.
      }
    };
    
    return this.http.post<{roomUrl: string, roomName: string}>(`${this.INTERVIEW_API}/meeting/create`, request)
      .pipe(
        catchError(error => {
          console.error('Error creating meeting room:', error);
          return throwError(() => new Error('Failed to create meeting room.'));
        })
      );
  }

  /**
   * Share meeting link with participants
   */
  shareMeetingLink(sessionId: string, meetingLink: string, participants: {email: string, role: string, name?: string, token?: string, interviewUrl?: string, monitorUrl?: string}[]): Observable<void> {
    const request = {
      sessionId,
      meetingLink,
      participants
    };
    
    return this.http.post<void>(`${this.INTERVIEW_API}/meeting/share`, request)
      .pipe(
        catchError(error => {
          console.error('Error sharing meeting link:', error);
          return throwError(() => new Error('Failed to share meeting link.'));
        })
      );
  }

  /**
   * Get interview results/analytics
   */
  getInterviewResults(sessionId: string): Observable<any> {
    return this.http.get<any>(`${this.INTERVIEW_API}/session/${sessionId}/results`)
      .pipe(
        catchError(error => {
          console.error('Error fetching interview results:', error);
          return throwError(() => new Error('Failed to load interview results.'));
        })
      );
  }

  /**
   * Get current session from state
   */
  getCurrentSession(): InterviewSessionResponse | null {
    return this.currentSessionSubject.value;
  }

  /**
   * Clear current session
   */
  clearCurrentSession(): void {
    this.currentSessionSubject.next(null);
  }

  /**
   * Join interview - Navigate to custom interview room instead of Jitsi Meet directly
   * This ensures participants use our Angular interview room component
   */
  joinInterview(sessionId: string, token?: string): string {
    const baseUrl = '/interview-room';
    const params = new URLSearchParams();
    
    if (token) {
      params.append('token', token);
    }
    
    if (sessionId) {
      params.append('sessionId', sessionId);
    }
    
    const queryString = params.toString();
    return queryString ? `${baseUrl}?${queryString}` : baseUrl;
  }

  /**
   * Get Direct Meeting URL for Jitsi Meet (fallback/testing only)
   * This should NOT be used for regular interview flow
   */
  getDirectMeetingUrl(sessionId: string): Observable<{meetingUrl: string}> {
    return this.http.get<{meetingUrl: string}>(`${this.INTERVIEW_API}/session/${sessionId}/meeting-url`, { headers: this.getAuthHeaders() })
      .pipe(
        catchError(error => {
          console.error('Error fetching meeting URL:', error);
          return throwError(() => new Error('Failed to get meeting URL.'));
        })
      );
  }

  // ==================== AI AVATAR SERVICE METHODS ====================

  /**
   * Create AI Avatar for structured interview flow
   */
  createAIAvatar(sessionId: string, candidateProfile: any, jobRole: string): Promise<any> {
    const request = {
      session_id: sessionId,
      candidate_profile: candidateProfile,
      job_role: jobRole,
      experience_level: 3, // Convert string to number
      required_technologies: candidateProfile.technical_skills || [],
      meeting_link: `aria-interview-${sessionId}`
    };

    console.log('🤖 Creating AI Avatar for structured interview:', request);

    return this.http.post<any>(`${this.AI_AVATAR_API}/avatar/create`, request, { 
      headers: this.getAuthHeaders() 
    })
    .pipe(
      map(response => {
        console.log('✅ AI Avatar created successfully:', response);
        return response;
      }),
      catchError(error => {
        console.error('❌ Error creating AI Avatar:', error);
        return throwError(() => new Error('Failed to create AI interviewer.'));
      })
    ).toPromise() as Promise<any>;
  }

  /**
   * Start structured interview flow with AI Avatar
   */
  startStructuredInterview(sessionId: string, candidateInfo: any): Promise<any> {
    const request = {
      candidate_name: candidateInfo.name,
      position: candidateInfo.position,
      company: candidateInfo.company || 'Our Company',
      experience_level: candidateInfo.experience_level || 'mid',
      technical_skills: candidateInfo.technical_skills || [],
      domain: candidateInfo.domain || 'general'
    };

    console.log('🚀 Starting structured interview flow:', request);

    return this.http.post<any>(`${this.AI_AVATAR_API}/api/alex/start/${sessionId}`, request)
    .pipe(
      map(response => {
        console.log('✅ Structured interview started successfully:', response);
        return response;
      }),
      catchError(error => {
        console.error('❌ Error starting structured interview:', error);
        return throwError(() => new Error('Failed to start structured interview.'));
      })
    ).toPromise() as Promise<any>;
  }

  /**
   * Get AI Avatar status and current stage
   */
  getAIAvatarStatus(sessionId: string): Promise<any> {
    return this.http.get<any>(`${this.AI_AVATAR_API}/avatar/${sessionId}/status`, { 
      headers: this.getAuthHeaders() 
    })
    .pipe(
      map(response => {
        console.log('📊 AI Avatar status:', response);
        return response;
      }),
      catchError(error => {
        console.error('❌ Error getting AI Avatar status:', error);
        return throwError(() => new Error('Failed to get interviewer status.'));
      })
    ).toPromise() as Promise<any>;
  }

  /**
   * Check if AI Avatar service is healthy
   */
  checkAIAvatarHealth(): Promise<boolean> {
    return this.http.get<any>(`${this.AI_AVATAR_API}/health`)
    .pipe(
      map(response => {
        console.log('💚 AI Avatar service health:', response);
        return response.status === 'healthy';
      }),
      catchError(error => {
        console.error('❌ AI Avatar health check failed:', error);
        return throwError(() => false);
      })
    ).toPromise() as Promise<boolean>;
  }

  /**
   * Stop AI Avatar for session
   */
  stopAIAvatar(sessionId: string): Promise<any> {
    return this.http.delete<any>(`${this.AI_AVATAR_API}/avatar/${sessionId}`, { 
      headers: this.getAuthHeaders() 
    })
    .pipe(
      map(response => {
        console.log('🛑 AI Avatar stopped:', response);
        return response;
      }),
      catchError(error => {
        console.error('❌ Error stopping AI Avatar:', error);
        return throwError(() => new Error('Failed to stop AI interviewer.'));
      })
    ).toPromise() as Promise<any>;
  }
}
