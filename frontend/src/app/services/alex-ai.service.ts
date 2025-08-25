import { Injectable } from '@angular/core';
import { Observable, BehaviorSubject, Subject } from 'rxjs';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { environment } from '../../environments/environment';

export interface AlexResponse {
  text: string;
  type: 'greeting' | 'question' | 'follow_up' | 'transition' | 'salary_info' | 'company_info' | 'wrap_up';
  audio_url?: string;
  should_continue: boolean;
  next_action: string;
  metadata?: {
    session_id?: string;
    stage?: string;
    question_id?: string;
    question_type?: string;
    difficulty?: number;
    topic?: string;
  };
}

export interface InterviewSession {
  session_id: string;
  candidate_name: string;
  position: string;
  stage: string;
  questions_asked: number;
  duration_minutes: number;
  current_question?: any;
}

export interface InterviewReport {
  interview_summary: {
    candidate_name: string;
    position: string;
    session_id: string;
    duration_minutes: number;
    completion_status: string;
    questions_asked: number;
    questions_answered: number;
  };
  technical_evaluation: {
    overall_score: number;
    score_breakdown: { [key: string]: number };
    avg_response_time_seconds: number;
    response_quality: string;
  };
  behavioral_assessment: {
    communication_clarity: string;
    engagement_level: string;
    question_asking_ability: number;
  };
  cheat_detection: {
    flags_detected: number;
    flag_types: string[];
    risk_level: string;
  };
  conversation_highlights: string[];
  recommendations: string[];
  detailed_responses: Array<{
    question: string;
    response: string;
    score: number;
    response_time: number;
    flags: string[];
  }>;
  generated_at: string;
  alex_ai_version: string;
}

@Injectable({
  providedIn: 'root'
})
export class AlexAIService {
  private alexWS$: WebSocketSubject<any> | null = null;
  private currentSessionId: string | null = null;

  // Observables for UI updates
  private alexResponseSubject = new BehaviorSubject<AlexResponse | null>(null);
  private sessionInfoSubject = new BehaviorSubject<InterviewSession | null>(null);
  private connectionStatusSubject = new BehaviorSubject<boolean>(false);

  public alexResponse$ = this.alexResponseSubject.asObservable();
  public sessionInfo$ = this.sessionInfoSubject.asObservable();
  public connectionStatus$ = this.connectionStatusSubject.asObservable();

  constructor() {}

  /**
   * Start Alex AI interview session
   */
  async startAlexInterview(sessionId: string, candidateInfo: {
    name: string;
    position: string;
    company?: string;
    experience_level?: string;
    technical_skills?: string[];
    domain?: string;
  }): Promise<AlexResponse> {
    try {
      console.log('🤖 Starting Alex AI interview for', candidateInfo.name);
      
      // Now using unified AI Avatar Service (Port 8005) instead of separate Alex AI service
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/api/alex/start/${sessionId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          candidate_name: candidateInfo.name,
          position: candidateInfo.position,
          company: candidateInfo.company || '',
          experience_level: candidateInfo.experience_level || 'mid',
          technical_skills: candidateInfo.technical_skills || [],
          domain: candidateInfo.domain || 'python'
        })
      });

      if (!response.ok) {
        throw new Error(`Failed to start Alex interview: ${response.statusText}`);
      }

      const result = await response.json();
      
      if (result.success) {
        this.currentSessionId = sessionId;
        const alexResponse = result.alex_response;
        
        // Update subjects
        this.alexResponseSubject.next(alexResponse);
        
        // Connect WebSocket for real-time interaction
        this.connectWebSocket(sessionId);
        
        console.log('✅ Alex AI interview started successfully');
        return alexResponse;
      } else {
        throw new Error('Alex AI start failed');
      }
    } catch (error) {
      console.error('❌ Error starting Alex AI interview:', error);
      throw error;
    }
  }

  /**
   * Send candidate response to Alex AI
   */
  async sendCandidateResponse(sessionId: string, responseText: string, audioUrl?: string): Promise<AlexResponse> {
    try {
      console.log('📤 Sending candidate response to Alex AI:', responseText.substring(0, 100));
      
      // Now using unified AI Avatar Service (Port 8005) for Alex AI functionality
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/api/alex/respond/${sessionId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          response_text: responseText,
          audio_url: audioUrl
        })
      });

      if (!response.ok) {
        throw new Error(`Failed to send response to Alex: ${response.statusText}`);
      }

      const result = await response.json();
      
      if (result.success) {
        const alexResponse = result.alex_response;
        this.alexResponseSubject.next(alexResponse);
        return alexResponse;
      } else {
        throw new Error('Alex AI response processing failed');
      }
    } catch (error) {
      console.error('❌ Error sending response to Alex AI:', error);
      throw error;
    }
  }

  /**
   * Get current session information
   */
  async getSessionInfo(sessionId: string): Promise<InterviewSession> {
    try {
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/api/alex/session/${sessionId}`);
      
      if (!response.ok) {
        throw new Error(`Failed to get session info: ${response.statusText}`);
      }

      const result = await response.json();
      
      if (result.success) {
        const sessionInfo = result.session_info;
        this.sessionInfoSubject.next(sessionInfo);
        return sessionInfo;
      } else {
        throw new Error('Failed to retrieve session information');
      }
    } catch (error) {
      console.error('❌ Error getting Alex AI session info:', error);
      throw error;
    }
  }

  /**
   * Generate comprehensive interview report (recruiter only)
   */
  async generateInterviewReport(sessionId: string): Promise<InterviewReport> {
    try {
      console.log('📊 Generating Alex AI interview report for session:', sessionId);
      
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/api/alex/report/${sessionId}`);
      
      if (!response.ok) {
        throw new Error(`Failed to generate report: ${response.statusText}`);
      }

      const result = await response.json();
      
      if (result.success) {
        console.log('✅ Alex AI report generated successfully');
        return result.report;
      } else {
        throw new Error('Report generation failed');
      }
    } catch (error) {
      console.error('❌ Error generating Alex AI report:', error);
      throw error;
    }
  }

  /**
   * Connect WebSocket for real-time communication
   */
  private connectWebSocket(sessionId: string): void {
    try {
      const wsUrl = `${environment.aiServices.alexAiServiceUrl.replace('http', 'ws')}/ws/alex/${sessionId}`;
      console.log('🔗 Connecting to Alex AI WebSocket:', wsUrl);

      this.alexWS$ = webSocket({
        url: wsUrl,
        openObserver: {
          next: () => {
            console.log('✅ Connected to Alex AI WebSocket');
            this.connectionStatusSubject.next(true);
          }
        },
        closeObserver: {
          next: () => {
            console.log('🔌 Disconnected from Alex AI WebSocket');
            this.connectionStatusSubject.next(false);
          }
        }
      });

      // Subscribe to incoming messages
      this.alexWS$.subscribe({
        next: (message) => {
          console.log('📨 Received Alex AI message:', message);
          
          if (message.type === 'alex_response') {
            this.alexResponseSubject.next(message.data);
          } else if (message.type === 'session_update') {
            this.sessionInfoSubject.next(message.data);
          }
        },
        error: (error) => {
          console.error('❌ Alex AI WebSocket error:', error);
          this.connectionStatusSubject.next(false);
        }
      });
    } catch (error) {
      console.error('❌ Failed to connect Alex AI WebSocket:', error);
    }
  }

  /**
   * Send message via WebSocket
   */
  sendWebSocketMessage(message: any): void {
    if (this.alexWS$ && this.connectionStatusSubject.value) {
      this.alexWS$.next(message);
    } else {
      console.warn('⚠️ Alex AI WebSocket not connected');
    }
  }

  /**
   * Request next question (recruiter control)
   */
  requestNextQuestion(sessionId: string): void {
    this.sendWebSocketMessage({
      type: 'request_next_question',
      session_id: sessionId
    });
  }

  /**
   * Send candidate response via WebSocket (real-time)
   */
  sendRealtimeResponse(responseText: string): void {
    if (this.currentSessionId) {
      this.sendWebSocketMessage({
        type: 'candidate_response',
        text: responseText,
        session_id: this.currentSessionId
      });
    }
  }

  /**
   * Play Alex AI speech using browser TTS or audio URL
   */
  async playAlexSpeech(alexResponse: AlexResponse): Promise<void> {
    try {
      console.log('🔊 Playing Alex speech:', alexResponse.text.substring(0, 50));
      
      // Method 1: Use audio URL if available
      if (alexResponse.audio_url) {
        return this.playAudioFromUrl(alexResponse.audio_url);
      }
      
      // Method 2: Use Web Speech API
      return this.synthesizeAndPlaySpeech(alexResponse.text);
    } catch (error) {
      console.error('❌ Failed to play Alex speech:', error);
      // Fallback: display text visually with highlight
      this.showSpeechFallback(alexResponse.text);
    }
  }

  /**
   * Play audio from URL
   */
  private async playAudioFromUrl(audioUrl: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const audio = new Audio(audioUrl);
      
      audio.onloadeddata = () => {
        audio.play().then(() => {
          console.log('🔊 Alex audio playback started');
        }).catch(error => {
          console.error('Audio playback failed:', error);
          reject(error);
        });
      };
      
      audio.onended = () => {
        console.log('🔇 Alex audio playback ended');
        resolve();
      };
      
      audio.onerror = (error) => {
        console.error('Audio loading failed:', error);
        reject(error);
      };
      
      audio.volume = 0.8;
      audio.load();
    });
  }

  /**
   * Synthesize and play speech using Web Speech API
   */
  private synthesizeAndPlaySpeech(text: string): Promise<void> {
    return new Promise((resolve, reject) => {
      if (!('speechSynthesis' in window)) {
        reject(new Error('Speech synthesis not supported'));
        return;
      }
      
      const utterance = new SpeechSynthesisUtterance(text);
      
      // Configure Alex's voice settings
      utterance.rate = 0.9;  // Slightly slower for clarity
      utterance.pitch = 1.0;
      utterance.volume = 0.8;
      
      // Try to use a professional voice
      const voices = speechSynthesis.getVoices();
      const preferredVoices = ['Alex', 'Daniel', 'Microsoft David', 'Google US English', 'en-US'];
      
      const preferredVoice = voices.find(voice => 
        preferredVoices.some(preferred => voice.name.includes(preferred)) ||
        (voice.lang.startsWith('en') && !voice.localService)
      ) || voices.find(voice => voice.lang.startsWith('en'));
      
      if (preferredVoice) {
        utterance.voice = preferredVoice;
        console.log('🎤 Using voice:', preferredVoice.name);
      }
      
      utterance.onstart = () => {
        console.log('🗣️ Alex speech synthesis started');
      };
      
      utterance.onend = () => {
        console.log('🔇 Alex speech synthesis ended');
        resolve();
      };
      
      utterance.onerror = (error) => {
        console.error('Speech synthesis error:', error);
        reject(error);
      };
      
      speechSynthesis.speak(utterance);
    });
  }

  /**
   * Show speech fallback when audio fails
   */
  private showSpeechFallback(text: string): void {
    // Create visual indicator that Alex is "speaking"
    const alexIndicator = document.querySelector('.alex-speech-indicator');
    if (alexIndicator) {
      alexIndicator.classList.add('speaking');
      alexIndicator.textContent = text;
      
      // Estimate speech duration
      const words = text.split(' ').length;
      const estimatedDuration = Math.max(2000, (words / 150) * 60 * 1000); // ~150 WPM
      
      setTimeout(() => {
        alexIndicator.classList.remove('speaking');
      }, estimatedDuration);
    }
  }

  /**
   * Stop current Alex speech
   */
  stopAlexSpeech(): void {
    if ('speechSynthesis' in window) {
      speechSynthesis.cancel();
    }
    console.log('⏹️ Alex speech stopped');
  }

  /**
   * Get Alex AI conversation state
   */
  getAlexState(): {
    isConnected: boolean;
    currentSession: string | null;
    lastResponse: AlexResponse | null;
  } {
    return {
      isConnected: this.connectionStatusSubject.value,
      currentSession: this.currentSessionId,
      lastResponse: this.alexResponseSubject.value
    };
  }

  /**
   * Disconnect from Alex AI
   */
  disconnect(): void {
    if (this.alexWS$) {
      this.alexWS$.complete();
      this.alexWS$ = null;
    }
    
    this.connectionStatusSubject.next(false);
    this.currentSessionId = null;
    this.alexResponseSubject.next(null);
    this.sessionInfoSubject.next(null);
    
    console.log('🔌 Disconnected from Alex AI');
  }

  /**
   * Health check for Alex AI service
   */
  async checkAlexHealth(): Promise<boolean> {
    try {
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/health`);
      const result = await response.json();
      return result.status === 'healthy';
    } catch (error) {
      console.error('❌ Alex AI health check failed:', error);
      return false;
    }
  }

  // ==================== STRUCTURED INTERVIEW SUPPORT ====================

  /**
   * Initialize structured interview with Alex AI
   */
  async initializeStructuredInterview(sessionId: string, candidateInfo: {
    name: string;
    position: string;
    experience: string;
    skills: string[];
  }): Promise<AlexResponse> {
    try {
      console.log('🎯 Initializing structured interview with Alex AI for', candidateInfo.name);
      
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/api/structured-interview/initialize/${sessionId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          candidate_name: candidateInfo.name,
          position: candidateInfo.position,
          experience: candidateInfo.experience,
          skills: candidateInfo.skills,
          use_structured_flow: true
        })
      });

      if (!response.ok) {
        throw new Error(`Failed to initialize structured interview: ${response.statusText}`);
      }

      const result = await response.json();
      
      if (result.success) {
        this.currentSessionId = sessionId;
        const alexResponse = result.alex_response;
        
        // Update subjects with structured interview metadata
        this.alexResponseSubject.next({
          ...alexResponse,
          metadata: {
            ...alexResponse.metadata,
            stage: 'introduction',
            is_structured: true
          }
        });
        
        // Connect WebSocket for structured flow
        this.connectWebSocket(sessionId);
        
        console.log('✅ Structured interview initialized successfully');
        return alexResponse;
      } else {
        throw new Error('Structured interview initialization failed');
      }
    } catch (error) {
      console.error('❌ Error initializing structured interview:', error);
      throw error;
    }
  }

  /**
   * Submit structured response to Alex AI
   */
  async submitStructuredResponse(sessionId: string, responseData: {
    stage: string;
    questionId: string;
    transcriptText: string;
    codeSubmission?: string;
    responseTime?: number;
  }): Promise<AlexResponse> {
    try {
      console.log('📤 Submitting structured response to Alex AI:', {
        stage: responseData.stage,
        questionId: responseData.questionId,
        hasTranscript: !!responseData.transcriptText,
        hasCode: !!responseData.codeSubmission
      });
      
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/api/structured-interview/respond/${sessionId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          current_stage: responseData.stage,
          question_id: responseData.questionId,
          response_text: responseData.transcriptText,
          code_submission: responseData.codeSubmission,
          response_time_seconds: responseData.responseTime,
          timestamp: new Date().toISOString()
        })
      });

      if (!response.ok) {
        throw new Error(`Failed to submit structured response: ${response.statusText}`);
      }

      const result = await response.json();
      
      if (result.success) {
        const alexResponse = result.alex_response;
        this.alexResponseSubject.next(alexResponse);
        return alexResponse;
      } else {
        throw new Error('Structured response processing failed');
      }
    } catch (error) {
      console.error('❌ Error submitting structured response:', error);
      throw error;
    }
  }

  /**
   * Get current interview stage information
   */
  async getStructuredInterviewStage(sessionId: string): Promise<{
    stage: string;
    progress: any;
    nextAction: string;
    questions_remaining: number;
  }> {
    try {
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/api/structured-interview/stage/${sessionId}`);
      
      if (!response.ok) {
        throw new Error(`Failed to get stage info: ${response.statusText}`);
      }

      const result = await response.json();
      
      if (result.success) {
        return result.stage_info;
      } else {
        throw new Error('Failed to retrieve stage information');
      }
    } catch (error) {
      console.error('❌ Error getting structured interview stage:', error);
      throw error;
    }
  }

  /**
   * Request stage transition from Alex AI
   */
  async requestStageTransition(sessionId: string, nextStage: string): Promise<boolean> {
    try {
      console.log(`🔄 Requesting stage transition to: ${nextStage}`);
      
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/api/structured-interview/transition/${sessionId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          next_stage: nextStage,
          timestamp: new Date().toISOString()
        })
      });

      if (!response.ok) {
        throw new Error(`Failed to request stage transition: ${response.statusText}`);
      }

      const result = await response.json();
      return result.success;
    } catch (error) {
      console.error('❌ Error requesting stage transition:', error);
      return false;
    }
  }

  /**
   * End structured interview
   */
  async endStructuredInterview(sessionId: string): Promise<boolean> {
    try {
      console.log('🎯 Ending structured interview:', sessionId);
      
      const response = await fetch(`${environment.aiServices.alexAiServiceUrl}/api/structured-interview/end/${sessionId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          timestamp: new Date().toISOString()
        })
      });

      if (!response.ok) {
        throw new Error(`Failed to end structured interview: ${response.statusText}`);
      }

      const result = await response.json();
      return result.success;
    } catch (error) {
      console.error('❌ Error ending structured interview:', error);
      return false;
    }
  }

  /**
   * Send WebSocket message for structured interview flow
   */
  sendStructuredWebSocketMessage(message: {
    type: string;
    stage?: string;
    data?: any;
  }): void {
    const structuredMessage = {
      ...message,
      session_id: this.currentSessionId,
      timestamp: new Date().toISOString(),
      is_structured: true
    };
    
    this.sendWebSocketMessage(structuredMessage);
  }
}
