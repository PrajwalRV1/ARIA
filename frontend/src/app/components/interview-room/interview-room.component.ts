import { Component, OnInit, OnDestroy, ElementRef, ViewChild, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { InterviewService, InterviewSessionResponse, Question } from '../../services/interview.service';
import { WebRTCService } from '../../services/webrtc.service';
import { TranscriptService } from '../../services/transcript.service';
import { SessionService } from '../../services/session.service';
import { SessionRecoveryService } from '../../services/session-recovery.service';
import { AlexAIService, AlexResponse, InterviewReport } from '../../services/alex-ai.service';
import { environment } from '../../../environments/environment';
import { CheatDetectionService, CheatDetectionState } from '../../services/cheat-detection.service';
import { CheatDetectionAlertsComponent } from '../cheat-detection-alerts/cheat-detection-alerts.component';
import { EnhancedTranscriptComponent, TranscriptEntry, TranscriptError, ErrorCorrectionRequest } from '../enhanced-transcript/enhanced-transcript.component';
import { Subscription } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-interview-room',
  standalone: true,
  imports: [CommonModule, FormsModule, CheatDetectionAlertsComponent, EnhancedTranscriptComponent],
  templateUrl: './interview-room.component.html',
  styleUrls: ['./interview-room.component.scss']
})
export class InterviewRoomComponent implements OnInit, OnDestroy {
  @ViewChild('localVideo', { static: false }) localVideo!: ElementRef<HTMLVideoElement>;
  @ViewChild('remoteVideo', { static: false }) remoteVideo!: ElementRef<HTMLVideoElement>;
  @ViewChild('codeEditor', { static: false }) codeEditor!: ElementRef<HTMLTextAreaElement>;
  @ViewChild('jitsiContainer', { static: false }) jitsiContainer!: ElementRef<HTMLDivElement>;

  // Interview session data
  sessionId: string = '';
  session: InterviewSessionResponse | null = null;
  currentQuestion: Question | null = null;
  
  // UI state
  isLoading: boolean = true;
  isInterviewStarted: boolean = false;
  isRecording: boolean = false;
  isConnected: boolean = false;
  candidateName: string = '';
  timer: string = '00:00:00';
  
  // Cheat Detection
  cheatDetectionState: CheatDetectionState = {
    isMonitoring: false,
    biasFlags: [],
    totalAlerts: 0,
    riskScore: 0,
    lastUpdate: new Date()
  };
  
  // UI Stability controls
  isSubmittingResponse: boolean = false;
  lastSubmissionTime: number = 0;
  submissionCooldown: number = 2000; // 2 seconds cooldown between submissions
  isProcessingAction: boolean = false;
  
  // Transcript and communication
  liveTranscript: string = '';
  interviewerQuestion: string = '';
  candidateResponse: string = '';
  currentTask: string = '';
  
  // Code editor
  codeContent: string = '// Write your code here...';
  currentLanguage: string = 'javascript';
  
  // Media and WebRTC
  private localStream: MediaStream | null = null;
  private timerInterval: any;
  private timerSeconds: number = 0;
  private subscriptions: Subscription[] = [];
  
  // Meeting integration
  meetingLink: string = '';
  roomId: string = '';
  
  // Media controls state
  isMicrophoneEnabled: boolean = true;
  isCameraEnabled: boolean = true;
  isAudioEnabled: boolean = true;
  isVideoEnabled: boolean = true;
  
  // AI Avatar State
  aiAvatarState = {
    status: 'ready' as 'initializing' | 'ready' | 'speaking' | 'listening' | 'analyzing' | 'thinking',
    currentAction: 'Waiting for your response',
    isVisible: true,
    isSpeaking: false,
    isAnalyzing: false,
    confidence: 0.85,
    lastInteraction: new Date()
  };
  
  // Structured Interview Flow State
  structuredInterviewActive: boolean = false;
  currentInterviewStage: string = '';
  stageProgress: any = null;
  useStructuredFlow: boolean = true; // Enable structured flow by default
  
  aiIsInteracting = false;
  aiIsSpeaking = false;
  showAiVideo = false;
  aiStatus = 'Ready';
  voiceBars = Array.from({length: 5}, () => ({ height: Math.random() * 100 }));
  
  // WebSocket connections for AI integration
  private orchestratorWS: WebSocket | null = null;
  private speechWS: WebSocket | null = null;
  private voiceSynthesisWS: WebSocket | null = null;
  private analyticsWS: WebSocket | null = null;
  
  // Speech synthesis
  private speechSynthesis: SpeechSynthesis | null = null;
  private currentSpeechUtterance: SpeechSynthesisUtterance | null = null;
  private currentTypingInterval: any = null;
  private selectedFemaleVoice: SpeechSynthesisVoice | null = null;
  private voicesLoaded: boolean = false;
  private voiceSelectionAttempts: number = 0;
  private maxVoiceSelectionAttempts: number = 5;
  private aiSpeechTimeout: any = null;
  private speechRetryCount: number = 0;
  private maxSpeechRetries: number = 3;
  private speechQueue: Array<{text: string, audioUrl?: string}> = [];
  private isProcessingSpeech: boolean = false;
  private currentSpeechId: string | null = null;
  
  // Speech recognition for candidate voice
  private speechRecognition: any = null;
  private isSpeechRecognitionActive: boolean = false;
  private speechRecognitionTimeout: any = null;
  private interimTranscript: string = '';
  private finalTranscript: string = '';
  private isListeningToCandidate: boolean = false;
  
  // Conversation history for transcript display
  conversationHistory: Array<{
    id: string;
    speaker: 'ai' | 'candidate';
    message: string;
    timestamp: Date;
    isTyping?: boolean;
    cleaned?: boolean;
  }> = [];
  
  // Speech cleaning configuration
  private fillerWords = [
    'aaaa', 'aaa', 'aa', 'ah', 'ahh', 'ahhh',
    'hmmm', 'hmm', 'hm', 'mm', 'mmm',
    'uhh', 'uh', 'uhhh', 'um', 'umm', 'ummm',
    'err', 'er', 'errr', 'ehh', 'eh',
    'well', 'like', 'you know', 'basically',
    'actually', 'literally', 'I mean',
    'so', 'okay', 'alright', 'right'
  ];
  
  private currentCandidateMessageId: string | null = null;
  private candidateTypingTimeout: any = null;

  /**
   * Clean speech transcript by removing filler words and noise
   */
  private cleanSpeechTranscript(text: string): string {
    if (!text || typeof text !== 'string') {
      return '';
    }

    let cleanedText = text.toLowerCase().trim();
    
    // Remove filler words and noise patterns
    this.fillerWords.forEach(filler => {
      const regex = new RegExp(`\\b${filler.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi');
      cleanedText = cleanedText.replace(regex, ' ');
    });
    
    // Remove excessive repeated characters (aaaa -> a, hmmm -> hmm)
    cleanedText = cleanedText.replace(/([a-z])\1{3,}/gi, '$1$1');
    
    // Remove standalone noise patterns
    cleanedText = cleanedText.replace(/\b(ah+|eh+|oh+|uh+|um+|mm+)\b/gi, ' ');
    
    // Clean up extra whitespace
    cleanedText = cleanedText.replace(/\s+/g, ' ').trim();
    
    // Restore proper capitalization
    if (cleanedText.length > 0) {
      cleanedText = cleanedText.charAt(0).toUpperCase() + cleanedText.slice(1);
    }
    
    return cleanedText;
  }

  /**
   * Add message to conversation history with enhanced features
   */
  private addToConversationHistory(
    speaker: 'ai' | 'candidate',
    message: string,
    timestamp: Date,
    options?: {
      isTyping?: boolean;
      cleaned?: boolean;
      replace?: boolean;
      messageId?: string;
    }
  ): void {
    const messageId = options?.messageId || `${speaker}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    const conversationMessage = {
      id: messageId,
      speaker,
      message: message.trim(),
      timestamp,
      isTyping: options?.isTyping || false,
      cleaned: options?.cleaned || false
    };
    
    // Replace existing message if requested (for typing updates)
    if (options?.replace && options?.messageId) {
      const existingIndex = this.conversationHistory.findIndex(msg => msg.id === options.messageId);
      if (existingIndex >= 0) {
        this.conversationHistory[existingIndex] = conversationMessage;
        return;
      }
    }
    
    // Add new message
    this.conversationHistory.push(conversationMessage);
    
    // Keep conversation history manageable (last 50 messages)
    if (this.conversationHistory.length > 50) {
      this.conversationHistory = this.conversationHistory.slice(-50);
    }
    
    console.log('📝 Added to conversation history:', {
      speaker,
      messageLength: message.length,
      isTyping: options?.isTyping,
      cleaned: options?.cleaned,
      totalMessages: this.conversationHistory.length
    });
  }

  /**
   * Update or finalize candidate message in conversation history
   */
  private updateCandidateConversationMessage(message: string, isFinal: boolean): void {
    const cleanedMessage = this.cleanSpeechTranscript(message);
    
    // Skip empty or very short cleaned messages
    if (!cleanedMessage || cleanedMessage.length < 3) {
      return;
    }
    
    if (!isFinal) {
      // Update typing message
      if (this.currentCandidateMessageId) {
        this.addToConversationHistory('candidate', cleanedMessage, new Date(), {
          isTyping: true,
          cleaned: true,
          replace: true,
          messageId: this.currentCandidateMessageId
        });
      } else {
        // Create new typing message
        this.currentCandidateMessageId = `candidate_typing_${Date.now()}`;
        this.addToConversationHistory('candidate', cleanedMessage, new Date(), {
          isTyping: true,
          cleaned: true,
          messageId: this.currentCandidateMessageId
        });
      }
      
      // Clear typing timeout
      if (this.candidateTypingTimeout) {
        clearTimeout(this.candidateTypingTimeout);
      }
      
      // Set timeout to finalize message if no updates
      this.candidateTypingTimeout = setTimeout(() => {
        this.finalizeCandidateMessage(cleanedMessage);
      }, 3000);
      
    } else {
      // Finalize message
      this.finalizeCandidateMessage(cleanedMessage);
    }
  }
  
  /**
   * Finalize candidate message in conversation history
   */
  private finalizeCandidateMessage(message: string): void {
    if (this.currentCandidateMessageId) {
      this.addToConversationHistory('candidate', message, new Date(), {
        isTyping: false,
        cleaned: true,
        replace: true,
        messageId: this.currentCandidateMessageId
      });
      
      this.currentCandidateMessageId = null;
    } else {
      // Add as new final message
      this.addToConversationHistory('candidate', message, new Date(), {
        isTyping: false,
        cleaned: true
      });
    }
    
    // Clear typing timeout
    if (this.candidateTypingTimeout) {
      clearTimeout(this.candidateTypingTimeout);
      this.candidateTypingTimeout = null;
    }
  }

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private interviewService: InterviewService,
    private webrtcService: WebRTCService,
    private transcriptService: TranscriptService,
    private sessionService: SessionService,
    private sessionRecoveryService: SessionRecoveryService,
    private alexAIService: AlexAIService,
    private cheatDetectionService: CheatDetectionService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  /**
   * Check if current user is a recruiter
   */
  public isRecruiter(): boolean {
    const userInfo = this.sessionService.getUserInfo();
    return userInfo?.userType === 'recruiter';
  }

  /**
   * Check if current user is a candidate
   */
  public isCandidate(): boolean {
    const userInfo = this.sessionService.getUserInfo();
    return userInfo?.userType === 'candidate';
  }

  /**
   * Get current user role for display
   */
  public getCurrentUserRole(): string {
    const userInfo = this.sessionService.getUserInfo();
    return userInfo?.userType || 'unknown';
  }

  ngOnInit(): void {
    // Only proceed with initialization in browser environment
    if (!isPlatformBrowser(this.platformId)) {
      // During SSR, just set loading state
      this.isLoading = true;
      return;
    }
    
    // Get session ID and token from URL parameters
    this.sessionId = this.route.snapshot.params['sessionId'] || 
                     this.route.snapshot.queryParams['session'] || 
                     this.route.snapshot.queryParams['sessionId'];
    
    const urlToken = this.route.snapshot.queryParams['token'];
    const userRole = this.route.snapshot.queryParams['role'] || 'candidate';
    
    // Initialize speech recognition for candidate voice input
    this.initializeSpeechRecognition();
    
    if (!this.sessionId) {
      console.error('No session ID provided, redirecting to dashboard');
      this.router.navigate(['/dashboard']);
      return;
    }

    // Handle token-based authentication (for candidates with email links)
    if (urlToken) {
      console.log('🔑 Token-based authentication detected');
      this.authenticateWithToken(urlToken, userRole);
      return;
    }

    // Check for existing authentication (for recruiters or logged-in users)
    const currentUser = this.sessionService.getCurrentUser();
    let hasLocalStorage = false;
    let hasInterviewToken = false;
    let localStorageDebug = 'N/A (SSR)';
    
    // Only access localStorage in browser environment
    if (isPlatformBrowser(this.platformId)) {
      hasLocalStorage = !!localStorage.getItem('aria_session');
      hasInterviewToken = !!localStorage.getItem('aria_interview_token');
      localStorageDebug = localStorage.getItem('aria_session')?.substring(0, 50) + '...' || 'none';
    }
    
    console.log('🔐 Authentication check:', {
      currentUser: currentUser,
      hasAriaSession: hasLocalStorage,
      hasInterviewToken: hasInterviewToken,
      sessionId: this.sessionId,
      localStorage: localStorageDebug
    });
    
    // Allow access if:
    // 1. User is already logged in (recruiters)
    // 2. User has interview token stored (candidates from email links)
    // 3. User has any session data
    if (!currentUser && !hasLocalStorage && !hasInterviewToken) {
      console.error('❌ No authentication found, redirecting to login');
      
      // Show helpful message to candidate
      this.showAuthenticationGuidance();
      
      // Redirect after showing guidance
      setTimeout(() => {
        this.router.navigate(['/login'], {
          queryParams: { 
            returnUrl: `/interview-room/${this.sessionId}`,
            reason: 'authentication_required'
          }
        });
      }, 5000);
      return;
    }
    
    console.log('✅ Authentication check passed - user appears to be logged in');
    console.log('🏁 Proceeding with interview room initialization...');

    // Validate session access to this interview
    const userInfo = this.sessionService.getUserInfo();
    if (userInfo?.sessionId && userInfo.sessionId !== this.sessionId) {
      console.warn('User session does not match interview session');
      // Allow access but log the mismatch
    }
    
    console.log('🎯 Interview Room initialized with session:', this.sessionId);
    console.log('👤 User info:', userInfo);
    
    // Subscribe to session changes
    const sessionSub = this.sessionService.session$.subscribe(session => {
      if (!session) {
        console.warn('Session expired during interview, redirecting to login');
        this.handleSessionExpired();
      }
    });
    this.subscriptions.push(sessionSub);
    
    this.initializeInterview();

    // Proctoring signals: connect analytics WS early for this session
    try { this.cheatDetectionService.connect(this.sessionId); } catch {}

    const cheatDetectionSub = this.cheatDetectionService.cheatDetectionState$.subscribe(state => {
      this.cheatDetectionState = state;
    });
    this.subscriptions.push(cheatDetectionSub);
    this.cheatDetectionService.startMonitoring(this.sessionId);
  }

  /**
   * Authenticate user with URL token (for candidates and recruiters with email links)
   */
  private async authenticateWithToken(token: string, userRole: string): Promise<void> {
    try {
      console.log('🔐 Validating interview access token...');
      this.isLoading = true;
      
      // Show loading message
      this.currentTask = 'Validating your interview access...';
      
      // Validate JWT token with session service
      const tokenValidation = await this.sessionService.validateInterviewToken(token, this.sessionId);
      
      if (!tokenValidation.valid) {
        console.error('❌ Invalid or expired token');
        this.showTokenError('Invalid or expired interview link. Please contact the recruiter for a new link.');
        return;
      }
      
      // Store token information for this session
      this.sessionService.setInterviewTokenInfo({
        token: token,
        sessionId: this.sessionId,
        userType: tokenValidation.userType || userRole,
        userId: tokenValidation.userId,
        candidateName: tokenValidation.candidateName,
        position: tokenValidation.position,
        expiresAt: tokenValidation.expiresAt
      });
      
      console.log('✅ Token validated successfully:', {
        userType: tokenValidation.userType,
        sessionId: this.sessionId,
        expiresAt: tokenValidation.expiresAt
      });
      
      // Update candidate name for display
      if (tokenValidation.candidateName) {
        this.candidateName = tokenValidation.candidateName;
      }
      
      // Subscribe to token expiration
      this.monitorTokenExpiration(tokenValidation.expiresAt);
      
      // Initialize interview with validated token
      this.currentTask = 'Setting up your interview room...';
      await this.initializeInterview();
      
    } catch (error: any) {
      console.error('❌ Token authentication failed:', error);
      this.isLoading = false;
      
      // Provide specific error messages based on error type
      let errorMessage = 'Failed to validate interview access.';
      let helpMessage = 'Please try refreshing the page or contact support.';
      
      if (error?.message?.includes('network') || error?.message?.includes('fetch')) {
        errorMessage = 'Network connection issue detected.';
        helpMessage = 'Please check your internet connection and try again.';
      } else if (error?.message?.includes('expired')) {
        errorMessage = 'Your interview link has expired.';
        helpMessage = 'Please contact the recruiter for a new interview link.';
      } else if (error?.message?.includes('Invalid')) {
        errorMessage = 'Invalid interview link format.';
        helpMessage = 'Please ensure you are using the correct link from your email.';
      }
      
      this.showTokenError(errorMessage, helpMessage);
    }
  }

  /**
   * Monitor token expiration and handle gracefully
   */
  private monitorTokenExpiration(expiresAt: string): void {
    const expirationTime = new Date(expiresAt).getTime();
    const now = Date.now();
    const timeUntilExpiration = expirationTime - now;
    
    if (timeUntilExpiration > 0) {
      // Warn user 5 minutes before expiration
      const warningTime = Math.max(0, timeUntilExpiration - (5 * 60 * 1000));
      
      setTimeout(() => {
        this.showExpirationWarning();
      }, warningTime);
      
      // Handle expiration
      setTimeout(() => {
        this.handleTokenExpiration();
      }, timeUntilExpiration);
    } else {
      // Token already expired
      this.handleTokenExpiration();
    }
  }

  /**
   * Show token error message with optional help text
   */
  private showTokenError(message: string, helpMessage?: string): void {
    const errorDiv = document.createElement('div');
    errorDiv.style.cssText = `
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      background: #fff;
      border: 2px solid #e74c3c;
      border-radius: 8px;
      padding: 30px;
      max-width: 500px;
      text-align: center;
      z-index: 10000;
      box-shadow: 0 10px 30px rgba(0,0,0,0.3);
    `;
    
    const helpText = helpMessage ? `<p style="color: #95a5a6; margin-bottom: 20px; line-height: 1.4; font-size: 14px;">${helpMessage}</p>` : '';
    
    errorDiv.innerHTML = `
      <div style="color: #e74c3c; margin-bottom: 20px;">
        <i class="fas fa-exclamation-triangle" style="font-size: 48px;"></i>
      </div>
      <h3 style="color: #2c3e50; margin-bottom: 15px;">Access Error</h3>
      <p style="color: #7f8c8d; margin-bottom: 25px; line-height: 1.5;">${message}</p>
      ${helpText}
      <div>
        <button onclick="window.location.href='/login'" 
                style="background: #3498db; color: white; border: none; padding: 12px 24px; border-radius: 6px; cursor: pointer; font-weight: 600; margin-right: 10px;">
          Return to Login
        </button>
        <button onclick="window.location.reload()" 
                style="background: #27ae60; color: white; border: none; padding: 12px 24px; border-radius: 6px; cursor: pointer; font-weight: 600;">
          Try Again
        </button>
      </div>
    `;
    
    document.body.appendChild(errorDiv);
  }

  /**
   * Show expiration warning
   */
  private showExpirationWarning(): void {
    const warningDiv = document.createElement('div');
    warningDiv.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: #f39c12;
      color: white;
      padding: 15px 20px;
      border-radius: 6px;
      z-index: 9999;
      box-shadow: 0 4px 12px rgba(243, 156, 18, 0.3);
      font-weight: 600;
    `;
    
    warningDiv.innerHTML = `
      <i class="fas fa-clock"></i>
      Your interview access will expire in 5 minutes
    `;
    
    document.body.appendChild(warningDiv);
    
    // Auto-remove after 10 seconds
    setTimeout(() => {
      if (warningDiv.parentNode) {
        warningDiv.parentNode.removeChild(warningDiv);
      }
    }, 10000);
  }

  /**
   * Handle token expiration
   */
  private handleTokenExpiration(): void {
    this.showTokenError('Your interview session has expired. Please contact the recruiter for assistance.');
  }

  ngOnDestroy(): void {
    this.cleanup();
  }

  /**
   * Initialize the complete interview session
   */
  private async initializeInterview(): Promise<void> {
    try {
      this.isLoading = true;
      
      console.log('📋 Loading interview session...');
      
      // Step 0: Check for existing interview state and attempt recovery
      const shouldRecover = await this.checkAndRecoverInterviewState();
      
      // Step 1: Load interview session details
      await this.loadInterviewSession();
      
      // Step 2: Initialize WebRTC and media
      await this.initializeWebRTC();
      
      // Step 3: Connect to transcript service
      this.connectToTranscriptService();
      
      // Step 4: Initialize AI avatar connections
      this.initializeAIAvatarConnections();
      
      // Step 5: Initialize interview flow (recovery-aware)
      if (shouldRecover) {
        console.log('🔄 Resuming interview from recovered state');
        // Timer and question should already be restored from recovery
      } else {
        // Fresh start
        if (this.useStructuredFlow) {
          await this.initializeStructuredInterview();
        } else {
          // Step 5 (fallback): Load current question
          await this.loadCurrentQuestion();
        }
        
        // Step 6: Start interview timer
        this.startInterviewTimer();
      }
      
      // Step 7: Start continuous state saving
      this.startContinuousStateSaving();
      
      this.isLoading = false;
      this.isInterviewStarted = true;
      this.isRecording = true;
      
      // Step 8: Start speech recognition for candidate voice input
      if (this.isSpeechRecognitionSupported()) {
        console.log('🎤 Starting speech recognition for interview...');
        setTimeout(() => this.startSpeechRecognition(), 2000); // Start after 2 seconds to ensure everything is ready
      }
      
      console.log('✅ Interview room fully initialized');
      
    } catch (error) {
      console.error('❌ Failed to initialize interview:', error);
      this.handleError('Failed to initialize interview session. Please try again.');
    }
  }

  /**
   * Load interview session from backend
   */
  private async loadInterviewSession(): Promise<void> {
    try {
      this.session = await this.interviewService.getInterviewSession(this.sessionId);
      
      if (!this.session) {
        throw new Error('Interview session not found');
      }
      
      // Extract session info
      this.meetingLink = this.session.meetingLink;
      this.roomId = this.session.webrtcRoomId;
      // Prefer personalized candidate name from session/profile if available
      // Fallback to previously set name (e.g., from token) or an ID-based placeholder
      // Safe access for possible shapes: session.candidateName or session.candidate?.fullName
      const profileName = (this.session as any).candidateName || (this.session as any).candidate?.fullName || (this.session as any).candidate?.name;
      this.candidateName = profileName || this.candidateName || `Candidate #${this.session.candidateId}`;
      
      console.log('📊 Loaded session:', {
        sessionId: this.sessionId,
        meetingLink: this.meetingLink,
        status: this.session.status
      });
      
      // Log session info
      this.logSystemMessage('system', `Interview session ${this.sessionId} started`, new Date());
      
    } catch (error) {
      console.error('Failed to load interview session:', error);
      throw error;
    }
  }

  /**
   * Initialize WebRTC with Jitsi Meet integration
   */
  private async initializeWebRTC(): Promise<void> {
    try {
      console.log('🎥 Initializing WebRTC...');
      
      // Check if media devices are available
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        throw new Error('Media devices not supported in this browser');
      }
      
      // First, check permissions status
      await this.checkMediaPermissions();
      
      // Get user media with error handling
      console.log('🎥 Requesting camera and microphone access...');
      this.localStream = await this.getUserMediaWithRetry();
      
      // Display local video
      if (this.localVideo?.nativeElement) {
        this.localVideo.nativeElement.srcObject = this.localStream;
        console.log('✅ Local video stream attached');
      }
      
      // Initialize WebRTC service
      await this.webrtcService.initialize(this.sessionId, this.localStream);
      
      // Set up WebRTC event handlers
      this.setupWebRTCEventHandlers();
      
      // Join Jitsi Meet room if we have a meeting link
      if (this.meetingLink) {
        console.log('🔗 Embedding Jitsi Meet room:', this.meetingLink);
        await this.embedJitsiMeet();
      }
      
    } catch (error) {
      console.error('❌ Failed to initialize WebRTC:', error);
      this.handleWebRTCError(error);
      throw error;
    }
  }

  /**
   * Set up WebRTC event handlers
   */
  private setupWebRTCEventHandlers(): void {
    // Remote stream handler
    const remoteStreamSub = this.webrtcService.onRemoteStream().subscribe((stream: MediaStream) => {
      console.log('📺 Received remote stream');
      if (this.remoteVideo?.nativeElement) {
        this.remoteVideo.nativeElement.srcObject = stream;
      }
    });
    
    // Connection state handler
    const connectionSub = this.webrtcService.onConnectionStateChange().subscribe((state: RTCPeerConnectionState) => {
      console.log('🔗 Connection state changed:', state);
      this.isConnected = state === 'connected';
      
      if (state === 'connected') {
        this.logSystemMessage('system', 'Connected to interview room', new Date());
      } else if (state === 'disconnected') {
        this.logSystemMessage('system', 'Disconnected from interview room', new Date());
      }
    });
    
    // Data channel messages
    const dataChannelSub = this.webrtcService.onDataChannelMessage().subscribe((message: any) => {
      console.log('📨 Received data channel message:', message);
      this.handleDataChannelMessage(message);
    });
    
    this.subscriptions.push(remoteStreamSub, connectionSub, dataChannelSub);
  }

  /**
   * Connect to transcript service
   */
  private connectToTranscriptService(): void {
    try {
      const transcriptSub = this.transcriptService.connect(this.sessionId).subscribe({
        next: (update: any) => {
          console.log('📝 Transcript update:', update);
          this.handleTranscriptUpdate(update);
        },
        error: (error) => {
          console.error('Transcript service error:', error);
        }
      });
      
      this.subscriptions.push(transcriptSub);
      
    } catch (error) {
      console.error('Failed to connect to transcript service:', error);
    }
  }

  /**
   * Load current question from backend
   */
  private async loadCurrentQuestion(): Promise<void> {
    try {
      this.currentQuestion = await this.interviewService.getCurrentQuestion(this.sessionId);
      
      if (this.currentQuestion) {
        this.interviewerQuestion = this.currentQuestion.text;
        this.currentTask = this.interviewerQuestion;
        
        // Update code editor language if it's a coding question
        if (this.currentQuestion.coding_required && this.currentQuestion.programming_language) {
          this.currentLanguage = this.currentQuestion.programming_language;
          this.codeContent = `// ${this.currentQuestion.text}\n\n// Write your ${this.currentLanguage} code here...`;
        }
        
        this.logSystemMessage('interviewer', this.interviewerQuestion, new Date());
      }
      
    } catch (error) {
      console.error('Failed to load current question:', error);
      // Use fallback question
      this.interviewerQuestion = 'Please introduce yourself and tell us about your background.';
      this.currentTask = this.interviewerQuestion;
      this.logSystemMessage('interviewer', this.interviewerQuestion, new Date());
    }
  }

  /**
   * Start interview timer
   */
  private startInterviewTimer(): void {
    // Set initial time (default 60 minutes)
    this.timerSeconds = this.session?.durationMinutes ? this.session.durationMinutes * 60 : 3600;
    
    this.timerInterval = setInterval(() => {
      this.timerSeconds++; // Count up instead of down for better UX
      
      const hours = Math.floor(this.timerSeconds / 3600);
      const minutes = Math.floor((this.timerSeconds % 3600) / 60);
      const seconds = this.timerSeconds % 60;
      
      this.timer = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }, 1000);
  }

  /**
   * Handle transcript updates (deprecated - use public handleTranscriptUpdate method)
   * @deprecated Use the public handleTranscriptUpdate method for better stability
   */
  private handleTranscriptUpdateDeprecated(update: any): void {
    if (update.speaker && update.text) {
      this.logSystemMessage(update.speaker, update.text, new Date(update.timestamp));
      
      // Update live transcript
      this.liveTranscript = update.text;
    }
  }

  /**
   * Handle data channel messages
   */
  private handleDataChannelMessage(message: any): void {
    if (message.type === 'question') {
      this.currentQuestion = message.question;
      this.interviewerQuestion = message.question.text;
      this.logSystemMessage('interviewer', this.interviewerQuestion, new Date());
    } else if (message.type === 'next_question') {
      this.loadCurrentQuestion();
    }
  }

  /**
   * Log important system messages (no longer storing conversation history)
   */
  private logSystemMessage(speaker: string, message: string, timestamp: Date): void {
    console.log('📝 System message:', {
      speaker: speaker,
      message: message.substring(0, 100) + (message.length > 100 ? '...' : ''),
      timestamp: timestamp.toISOString()
    });
  }
  



  /**
   * Toggle microphone on/off
   */
  toggleMicrophone(): void {
    this.isMicrophoneEnabled = !this.isMicrophoneEnabled;
    
    if (this.localStream) {
      const audioTrack = this.localStream.getAudioTracks()[0];
      if (audioTrack) {
        audioTrack.enabled = this.isMicrophoneEnabled;
      }
    }
    
    // Also toggle in WebRTC service
    this.webrtcService.toggleAudio();
    
    console.log('🎤 Microphone:', this.isMicrophoneEnabled ? 'enabled' : 'disabled');
  }

  /**
   * Toggle camera on/off
   */
  toggleCamera(): void {
    this.isCameraEnabled = !this.isCameraEnabled;
    
    if (this.localStream) {
      const videoTrack = this.localStream.getVideoTracks()[0];
      if (videoTrack) {
        videoTrack.enabled = this.isCameraEnabled;
      }
    }
    
    // Also toggle in WebRTC service
    this.webrtcService.toggleVideo();
    
    console.log('📹 Camera:', this.isCameraEnabled ? 'enabled' : 'disabled');
  }

  /**
   * Handle code editor changes
   */
  onCodeChange(): void {
    console.log('💻 Code changed:', this.codeContent?.length || 0, 'characters');
    
    // Send code updates via data channel for real-time collaboration
    if (this.webrtcService && this.currentQuestion?.coding_required) {
      this.webrtcService.sendDataMessage({
        type: 'code_update',
        code: this.codeContent,
        language: this.currentLanguage,
        timestamp: new Date()
      });
    }
  }

  /**
   * Handle session expiration during interview
   */
  private handleSessionExpired(): void {
    console.warn('Session expired during interview');
    
    // Show error message to user
    this.currentTask = 'Session expired. You will be redirected to login.';
    this.isRecording = false;
    this.isInterviewStarted = false;
    
    // Save interview state for potential recovery
    this.saveInterviewState();
    
    // Cleanup resources
    this.cleanup();
    
    // Redirect to login after delay
    setTimeout(() => {
      this.router.navigate(['/login'], {
        queryParams: { 
          sessionExpired: 'true',
          returnUrl: `/interview-room/${this.sessionId}`,
          reason: 'interview_session_expired'
        }
      });
    }, 3000);
  }

  /**
   * Enhanced save interview state for recovery
   */
  private saveInterviewState(): void {
    try {
      const interviewState = {
        sessionId: this.sessionId,
        timerSeconds: this.timerSeconds,
        // No conversation history to save since we removed the blue boxes
        currentQuestion: this.currentQuestion,
        codeContent: this.codeContent,
        currentLanguage: this.currentLanguage,
        candidateResponse: this.candidateResponse,
        liveTranscript: this.liveTranscript,
        interviewerQuestion: this.interviewerQuestion,
        currentTask: this.currentTask,
        isInterviewStarted: this.isInterviewStarted,
        isRecording: this.isRecording,
        // Structured interview state
        structuredInterviewActive: this.structuredInterviewActive,
        currentInterviewStage: this.currentInterviewStage,
        stageProgress: this.stageProgress,
        useStructuredFlow: this.useStructuredFlow,
        // AI state
        aiAvatarState: this.aiAvatarState,
        aiStatus: this.aiStatus,
        timestamp: Date.now(),
        lastSaved: new Date().toISOString()
      };
      
      // Save to both sessionStorage and localStorage for redundancy
      // Only access localStorage in browser environment
      if (isPlatformBrowser(this.platformId)) {
        sessionStorage.setItem('aria_interview_state', JSON.stringify(interviewState));
        localStorage.setItem(`aria_interview_recovery_${this.sessionId}`, JSON.stringify(interviewState));
      }
      
      console.log('🔄 Interview state saved for recovery:', {
        sessionId: this.sessionId,
        timerSeconds: this.timerSeconds,
        // No conversation history tracked anymore
        currentQuestionId: this.currentQuestion?.id
      });
    } catch (error) {
      console.error('Failed to save interview state:', error);
    }
  }
  
  /**
   * Check for existing interview state and recover if possible
   */
  private async checkAndRecoverInterviewState(): Promise<boolean> {
    try {
      console.log('🔍 Checking for recoverable interview state...');
      
      // Check sessionStorage first (most recent)
      let savedState: string | null = null;
      let storageType = 'session';
      
      // Only access localStorage in browser environment
      if (isPlatformBrowser(this.platformId)) {
        savedState = sessionStorage.getItem('aria_interview_state');
        
        // Fallback to localStorage
        if (!savedState) {
          savedState = localStorage.getItem(`aria_interview_recovery_${this.sessionId}`);
          storageType = 'local';
        }
      }
      
      if (!savedState) {
        console.log('🆕 No previous interview state found - starting fresh');
        return false;
      }
      
      const interviewState = JSON.parse(savedState);
      
      // Validate state
      if (!this.isValidInterviewState(interviewState)) {
        console.log('❌ Invalid interview state found - starting fresh');
        this.clearSavedInterviewState();
        return false;
      }
      
      // Check if state is too old (more than 1 hour)
      const stateAge = Date.now() - interviewState.timestamp;
      const maxAge = 60 * 60 * 1000; // 1 hour
      
      if (stateAge > maxAge) {
        console.log('⏰ Saved interview state is too old - starting fresh');
        this.clearSavedInterviewState();
        return false;
      }
      
      // Get current interview progress from backend
      const currentProgress = await this.getInterviewProgressFromBackend();
      
      // Validate that saved state is still relevant
      if (!this.isStateStillRelevant(interviewState, currentProgress)) {
        console.log('🔄 Saved state is outdated - starting fresh with current progress');
        this.clearSavedInterviewState();
        return false;
      }
      
      console.log(`✅ Valid interview state found in ${storageType}Storage - recovering...`);
      
      // Recover the state
      await this.recoverInterviewState(interviewState);
      
      // Show recovery notification
      this.showRecoveryNotification();
      
      return true;
      
    } catch (error) {
      console.error('❌ Error during interview state recovery:', error);
      this.clearSavedInterviewState();
      return false;
    }
  }
  
  /**
   * Validate interview state structure
   */
  private isValidInterviewState(state: any): boolean {
    return state &&
           state.sessionId === this.sessionId &&
           typeof state.timerSeconds === 'number' &&
           // No conversation history validation needed &&
           state.timestamp &&
           state.lastSaved;
  }
  
  /**
   * Get current interview progress from backend
   */
  private async getInterviewProgressFromBackend(): Promise<any> {
    try {
      // Get current interview session status
      const sessionStatus = await this.interviewService.getInterviewSession(this.sessionId);
      
      // Get current question/progress
      const currentQuestion = await this.interviewService.getCurrentQuestion(this.sessionId);
      
      return {
        sessionStatus,
        currentQuestion,
        timestamp: Date.now()
      };
    } catch (error) {
      console.warn('⚠️ Could not fetch current interview progress:', error);
      return null;
    }
  }
  
  /**
   * Check if saved state is still relevant compared to backend
   */
  private isStateStillRelevant(savedState: any, currentProgress: any): boolean {
    if (!currentProgress) {
      // If we can't get current progress, assume saved state is still valid
      return true;
    }
    
    // Check if interview is still in progress
    if (currentProgress.sessionStatus?.status === 'completed' || currentProgress.sessionStatus?.status === 'cancelled') {
      console.log('🏁 Interview has been completed/cancelled on backend');
      return false;
    }
    
    // Check if current question matches (for non-structured interviews)
    if (!this.useStructuredFlow && currentProgress.currentQuestion && savedState.currentQuestion) {
      if (currentProgress.currentQuestion.id !== savedState.currentQuestion.id) {
        console.log('❓ Current question has changed since last save');
        return false;
      }
    }
    
    return true;
  }
  
  /**
   * Recover interview state
   */
  private async recoverInterviewState(savedState: any): Promise<void> {
    console.log('🔄 Recovering interview state:', {
      savedAt: savedState.lastSaved,
      timerSeconds: savedState.timerSeconds,
      conversationCount: savedState.conversationHistory?.length || 0
    });
    
    // Restore basic state
    this.timerSeconds = savedState.timerSeconds || 0;
    this.currentLanguage = savedState.currentLanguage || 'javascript';
    this.codeContent = savedState.codeContent || '// Write your code here...';
    this.candidateResponse = savedState.candidateResponse || '';
    this.liveTranscript = savedState.liveTranscript || '';
    this.interviewerQuestion = savedState.interviewerQuestion || '';
    this.currentTask = savedState.currentTask || '';
    
    // Restore question state
    if (savedState.currentQuestion) {
      this.currentQuestion = savedState.currentQuestion;
      this.interviewerQuestion = savedState.currentQuestion.text;
      this.currentTask = savedState.interviewerQuestion || savedState.currentQuestion.text;
      
      // Update code editor if needed
      if (savedState.currentQuestion.coding_required) {
        this.currentLanguage = savedState.currentQuestion.programming_language || this.currentLanguage;
      }
    }
    
    // No conversation history to restore since we removed the blue boxes
    // The interview will continue with just the yellow current question and red live transcript
    
    // Restore structured interview state
    if (savedState.structuredInterviewActive !== undefined) {
      this.structuredInterviewActive = savedState.structuredInterviewActive;
      this.currentInterviewStage = savedState.currentInterviewStage || '';
      this.stageProgress = savedState.stageProgress;
      this.useStructuredFlow = savedState.useStructuredFlow !== undefined ? savedState.useStructuredFlow : true;
    }
    
    // Restore AI state
    if (savedState.aiAvatarState) {
      this.aiAvatarState = {
        ...this.aiAvatarState,
        ...savedState.aiAvatarState,
        lastInteraction: new Date(savedState.aiAvatarState.lastInteraction || Date.now())
      };
    }
    if (savedState.aiStatus) {
      this.aiStatus = savedState.aiStatus;
    }
    
    // Restart timer with recovered time
    this.startInterviewTimer();
    
    console.log('✅ Interview state successfully recovered');
  }
  
  /**
   * Clear saved interview state
   */
  private clearSavedInterviewState(): void {
    try {
      sessionStorage.removeItem('aria_interview_state');
      localStorage.removeItem(`aria_interview_recovery_${this.sessionId}`);
      console.log('🗑️ Cleared saved interview state');
    } catch (error) {
      console.warn('Warning: Could not clear saved interview state:', error);
    }
  }
  
  /**
   * Show recovery notification to user
   */
  private showRecoveryNotification(): void {
    const notification = document.createElement('div');
    notification.style.cssText = `
      position: fixed;
      top: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: #27ae60;
      color: white;
      padding: 15px 25px;
      border-radius: 8px;
      z-index: 10000;
      box-shadow: 0 4px 12px rgba(39, 174, 96, 0.3);
      font-weight: 600;
      max-width: 500px;
      text-align: center;
    `;
    
    notification.innerHTML = `
      <i class="fas fa-undo-alt"></i>
      Interview recovered! Continuing from where you left off.
      <button onclick="this.parentNode.remove()" 
              style="background: none; border: none; color: white; float: right; cursor: pointer; font-size: 18px; margin-left: 15px;">
        ×
      </button>
    `;
    
    document.body.appendChild(notification);
    
    // Auto-remove after 8 seconds
    setTimeout(() => {
      if (notification.parentNode) {
        notification.parentNode.removeChild(notification);
      }
    }, 8000);
  }
  
  /**
   * Start continuous state saving
   */
  private startContinuousStateSaving(): void {
    // Save state every 30 seconds during active interview
    const saveInterval = setInterval(() => {
      if (this.isInterviewStarted && this.sessionId) {
        this.saveInterviewState();
      }
    }, 30000); // 30 seconds
    
    // Save state on significant events
    this.setupEventBasedStateSaving();
    
    // Clear interval on component destruction
    this.subscriptions.push({
      unsubscribe: () => {
        clearInterval(saveInterval);
        console.log('🛑 Stopped continuous state saving');
      }
    } as any);
    
    console.log('💾 Started continuous interview state saving (every 30s)');
  }
  
  /**
   * Set up event-based state saving
   */
  private setupEventBasedStateSaving(): void {
    // Save on question changes
    // Save on important system messages
    const originalLogSystemMessage = this.logSystemMessage.bind(this);
    this.logSystemMessage = (speaker: string, message: string, timestamp: Date) => {
      originalLogSystemMessage(speaker, message, timestamp);
      
      // Save state when important system events happen
      if (speaker === 'interviewer' || speaker === 'system') {
        setTimeout(() => this.saveInterviewState(), 1000);
      }
    };
    
    // Save on code changes (debounced)
    let codeChangeTimeout: any;
    const originalOnCodeChange = this.onCodeChange.bind(this);
    this.onCodeChange = () => {
      originalOnCodeChange();
      
      // Debounced save (wait for user to stop typing)
      if (codeChangeTimeout) {
        clearTimeout(codeChangeTimeout);
      }
      codeChangeTimeout = setTimeout(() => {
        this.saveInterviewState();
      }, 5000); // 5 seconds after user stops typing
    };
    
    // Save before page unload
    window.addEventListener('beforeunload', () => {
      this.saveInterviewState();
    });
    
    // Save on visibility change (tab switch, minimize)
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        this.saveInterviewState();
      }
    });
  }

  /**
   * Handle errors
   */
  private handleError(message: string): void {
    console.error('Interview Room Error:', message);
    this.currentTask = `Error: ${message}`;
    this.isLoading = false;
  }

  /**
   * Cleanup resources
   */
  private cleanup(): void {
    // Clear timer
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
    
    // Clear typing animation interval
    if (this.currentTypingInterval) {
      clearInterval(this.currentTypingInterval);
      this.currentTypingInterval = null;
    }
    
    // Clear AI speech timeout
    if (this.aiSpeechTimeout) {
      clearTimeout(this.aiSpeechTimeout);
      this.aiSpeechTimeout = null;
    }
    
    // Clear speech queue and processing state
    this.speechQueue = [];
    this.isProcessingSpeech = false;
    this.currentSpeechId = null;
    
    // Stop media streams
    if (this.localStream) {
      this.localStream.getTracks().forEach(track => track.stop());
    }
    
    // Cleanup WebRTC
    this.webrtcService.cleanup();
    
    // Disconnect transcript service
    this.transcriptService.disconnect();
    
    // Disconnect AI avatar WebSockets
    this.disconnectAIAvatarConnections();
    
    // Unsubscribe from all subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions = [];
    
    console.log('🧹 Interview room cleanup completed');
  }

  // ==================== AI AVATAR INTEGRATION ====================

  /**
   * Initialize AI avatar WebSocket connections
   */
  private initializeAIAvatarConnections(): void {
    try {
      console.log('🤖 Initializing AI Avatar connections...');
      
      this.aiAvatarState.status = 'initializing';
      this.aiStatus = 'Connecting...';
      
      // 1. Interview orchestration WebSocket
      const orchestratorWsUrl = `${environment.aiServices.orchestratorWsUrl}/interview/${this.sessionId}?participant=frontend`;
      this.orchestratorWS = new WebSocket(orchestratorWsUrl);
      
      this.orchestratorWS.onopen = () => {
        console.log('🔗 Connected to interview orchestrator');
        this.updateAIStatus('ready', 'AI Interviewer connected');
        
        // If we were waiting to initialize structured interview, do it now
        if (this.structuredInterviewActive && this.currentTask === 'Connecting to AI interviewer...') {
          this.initializeStructuredInterview();
        }
      };
      
      this.orchestratorWS.onmessage = (event) => {
        const message = JSON.parse(event.data);
        this.handleOrchestratorMessage(message);
      };
      
      this.orchestratorWS.onerror = (error) => {
        console.error('❌ Orchestrator WebSocket error:', error);
        // If structured interview fails to connect, fall back
        if (this.structuredInterviewActive) {
          this.handleStructuredInterviewError('AI Orchestrator connection failed');
        }
      };
      
      this.orchestratorWS.onclose = (event) => {
        console.warn('🔌 Orchestrator WebSocket closed');
        if (this.structuredInterviewActive) {
          this.updateAIStatus('ready', 'AI interviewer disconnected');
        }
      };
      
      // 2. Speech processing WebSocket
      const speechWsUrl = `${environment.aiServices.speechServiceWsUrl}/transcript/${this.sessionId}`;
      this.speechWS = new WebSocket(speechWsUrl);
      
      // 3. Voice synthesis WebSocket for TTS
      const voiceSynthesisWsUrl = `${environment.aiServices.voiceSynthesisWsUrl}/tts/${this.sessionId}`;
      this.voiceSynthesisWS = new WebSocket(voiceSynthesisWsUrl);
      
      this.speechWS.onopen = () => {
        console.log('🎤 Connected to speech service');
      };
      
      this.speechWS.onmessage = (event) => {
        const message = JSON.parse(event.data);
        this.handleSpeechMessage(message);
      };
      
      // 3. Analytics WebSocket for real-time candidate analysis
      const analyticsWsUrl = `${environment.aiServices.analyticsServiceWsUrl}/analytics/${this.sessionId}?participant=frontend`;
      this.analyticsWS = new WebSocket(analyticsWsUrl);
      
      this.analyticsWS.onopen = () => {
        console.log('📊 Connected to analytics service');
      };
      
      this.analyticsWS.onmessage = (event) => {
        const message = JSON.parse(event.data);
        this.handleAnalyticsMessage(message);
      };
      
      // Start AI avatar animations
      this.startAIAvatarAnimations();
      
    } catch (error) {
      console.error('❌ Failed to initialize AI avatar connections:', error);
      this.updateAIStatus('ready', 'AI connection failed');
    }
  }

  /**
   * Handle messages from interview orchestrator
   */
  private handleOrchestratorMessage(message: any): void {
    console.log('🎯 Orchestrator message:', message);
    
    switch (message.type) {
      case 'ai_speech':
        this.handleAISpeech(message.data);
        break;
        
      case 'new_question':
        this.handleNewQuestion(message.data);
        break;
        
      case 'session_update':
        this.handleSessionUpdate(message.data);
        break;
        
      case 'ai_status':
        this.updateAIStatus(message.data.status, message.data.action);
        break;
        
      case 'alex_ai_speech':
        // Handle Alex AI speech (introduction and other speech)
        this.handleAlexAISpeech(message.data);
        break;
        
      case 'structured_stage_update':
        // Handle structured interview stage updates
        if (this.structuredInterviewActive) {
          this.handleStructuredStageUpdate(message.data);
        }
        break;
        
      case 'structured_question':
        // Handle structured interview questions
        if (this.structuredInterviewActive) {
          this.handleStructuredQuestion(message.data);
        }
        break;
        
      case 'structured_coding_challenge':
        // Handle structured coding challenges
        if (this.structuredInterviewActive) {
          this.handleStructuredCodingChallenge(message.data);
        }
        break;
        
      case 'interview_initialization_ack':
        // Acknowledgment that structured interview was initialized
        console.log('✅ Structured interview initialization acknowledged by AI Avatar');
        this.updateAIStatus('ready', 'Structured interview ready to begin');
        break;
        
      case 'interview_stage_transition':
        // Handle stage transitions from AI Avatar
        if (this.structuredInterviewActive && message.data.nextStage) {
          this.transitionToNextStage(message.data.nextStage);
        }
        break;
        
      case 'interview_conclusion':
        // Handle interview conclusion
        if (this.structuredInterviewActive) {
          this.handleInterviewConclusion(message.data);
        }
        break;
        
      case 'response_analysis_complete':
        // AI has finished analyzing the response
        this.handleResponseAnalysisComplete(message.data);
        break;
        
      case 'next_question_ready':
        // AI has prepared the next question
        this.handleNextQuestionFromAI(message.data);
        break;
        
      case 'ai_feedback':
        // AI provides feedback on the response
        this.handleAIFeedback(message.data);
        break;
        
      default:
        console.log('Unknown orchestrator message type:', message.type);
    }
  }

  /**
   * Unified AI speech handling entry point - truly consolidated processing
   */
  private handleAISpeech(speechData: any): void {
    console.log('🗣️ Unified AI speech handler:', speechData.text?.substring(0, 50) + '...');
    
    // Validate speech data
    if (!speechData || !speechData.text || typeof speechData.text !== 'string') {
      console.warn('⚠️ Invalid speech data received:', speechData);
      return;
    }
    
    const speechText = speechData.text.trim();
    if (!speechText) {
      console.warn('⚠️ Empty speech text received');
      return;
    }
    
    // Enhanced duplicate prevention with multiple checks
    if (this.isDuplicateSpeech(speechText)) {
      console.log('⚠️ Skipping duplicate AI speech:', speechText.substring(0, 50) + '...');
      return;
    }
    
    // CRITICAL: Stop any ongoing speech COMPLETELY before starting new speech
    this.forceStopAllSpeech();
    
    // Wait a bit to ensure complete stop
    setTimeout(() => {
      // Double-check that speech is still valid and not duplicate after delay
      if (!this.isDuplicateSpeech(speechText) && !this.aiIsSpeaking) {
        console.log('🎯 Starting new AI speech after cleanup delay');
        
        // Process speech through unified pipeline
        this.processUnifiedAISpeech({
          text: speechText,
          audioUrl: speechData.audioUrl,
          action: speechData.action || 'speaking',
          isIntroduction: speechData.isIntroduction || speechData.action === 'introduction',
          duration: speechData.duration,
          priority: speechData.priority || 'normal'
        });
      } else {
        console.log('⚠️ Speech became duplicate or AI already speaking after cleanup - skipping');
      }
    }, 300); // 300ms delay to ensure complete cleanup
  }

  /**
   * Enhanced Alex AI speech handler - now just an alias to the unified handler
   */
  private handleAlexAISpeech(speechData: any): void {
    console.log('🤖 Alex AI speech (routing to unified handler):', speechData.text?.substring(0, 50) + '...');
    
    // Route all Alex AI speech through the unified handler only
    this.handleAISpeech(speechData);
  }
  
  /**
   * Enhanced duplicate prevention with multiple checks
   */
  private isDuplicateSpeech(speechText: string): boolean {
    const now = Date.now();
    const checkWindow = 10000; // Reduced to 10 seconds to allow for natural conversation flow
    
    // IMPORTANT: Only check live transcript for duplicates, NOT conversation history
    // AI speech should NEVER appear in conversation history, only in live transcript
    
    // Check current live transcript
    const isDuplicateInTranscript = this.liveTranscript === speechText;
    
    // Check if currently processing the same speech
    const isCurrentlyProcessing = this.isProcessingSpeech && 
      this.speechQueue.length > 0 && 
      this.speechQueue[0].text === speechText;
    
    // Check if we have the same speech ID already processing
    const isSameSpeechId = this.currentSpeechId !== null && 
      this.aiIsSpeaking;
    
    const isDuplicate = isDuplicateInTranscript || isCurrentlyProcessing || isSameSpeechId;
    
    if (isDuplicate) {
      console.log('🔄 Duplicate speech detected:', {
        inTranscript: isDuplicateInTranscript,
        processing: isCurrentlyProcessing,
        sameSpeechId: isSameSpeechId,
        text: speechText.substring(0, 50) + '...'
      });
    }
    
    return isDuplicate;
  }
  
  /**
   * Process unified AI speech - main processing method for all AI speech
   */
  private processUnifiedAISpeech(speechData: {
    text: string;
    audioUrl?: string;
    action: string;
    isIntroduction: boolean;
    duration?: number;
    priority: string;
  }): void {
    console.log('📝 Processing unified AI speech:', {
      text: speechData.text.substring(0, 50) + '...',
      action: speechData.action,
      isIntroduction: speechData.isIntroduction,
      priority: speechData.priority
    });
    
    // Clear any existing typing animation
    if (this.currentTypingInterval) {
      clearInterval(this.currentTypingInterval);
      this.currentTypingInterval = null;
    }
    
    // Update AI status immediately
    const statusText = speechData.isIntroduction ? 
      'Alex AI is introducing himself...' : 
      'Alex AI is speaking...';
    
    this.updateAIStatus('speaking', statusText);
    this.aiIsSpeaking = true;
    this.aiIsInteracting = true;
    
    // Clear current task to avoid showing duplicate in yellow box
    this.currentTask = '';
    
    // Generate unique speech ID for tracking
    this.currentSpeechId = `unified_speech_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    console.log('🎬 Starting unified speech with ID:', this.currentSpeechId);
    
    // ONLY show in live transcript (red box) while speaking - DO NOT add to conversation history
    this.animateUnifiedTranscript(speechData.text);
    
    // Add AI speech to conversation history immediately
    this.addToConversationHistory('ai', speechData.text, new Date(), {
      isTyping: false,
      cleaned: false
    });
    
    // Process the speech with unified handling
    this.processUnifiedSpeech(speechData, this.currentSpeechId);
  }
  
  /**
   * Add message to conversation safely with enhanced duplicate prevention
   * @deprecated This method is no longer used since conversation history was removed
   */
  private addToConversationSafely(speaker: string, message: string, timestamp: Date): void {
    // Since conversation history is removed, just log the message for debugging
    this.logSystemMessage(speaker, message, timestamp);
  }
  
  /**
   * Process unified speech with enhanced error handling and flow management
   */
  private async processUnifiedSpeech(speechData: any, speechId: string): Promise<void> {
    if (this.isProcessingSpeech) {
      console.log('🔄 Speech already processing, queueing...');
      this.speechQueue.push({
        text: speechData.text, 
        audioUrl: speechData.audioUrl
      });
      return;
    }
    
    this.isProcessingSpeech = true;
    
    try {
      console.log(`🎯 Processing unified speech (ID: ${speechId})`);
      
      // Verify speech ID is still current before processing
      if (this.currentSpeechId !== speechId) {
        console.log(`⚠️ Speech ID mismatch at start: expected ${speechId}, current ${this.currentSpeechId}`);
        this.handleUnifiedSpeechError(new Error('Speech ID mismatch at start'), speechId);
        return;
      }
      
      // Play the speech with enhanced error handling
      await this.playUnifiedAISpeech(speechData.text, speechData.audioUrl, speechId);
      
      // Verify speech ID is still current after synthesis
      if (this.currentSpeechId !== speechId) {
        console.log(`⚠️ Speech ID changed during synthesis, aborting completion for ${speechId}`);
        this.handleUnifiedSpeechError(new Error('Speech ID changed during synthesis'), speechId);
        return;
      }
      
      // Use a more conservative speech duration calculation
      const speechDuration = speechData.duration || this.calculateAccurateSpeechDuration(speechData.text);
      console.log('🕐 Unified speech duration:', speechDuration, 'ms');
      
      // Clear any existing timeout
      if (this.aiSpeechTimeout) {
        clearTimeout(this.aiSpeechTimeout);
        this.aiSpeechTimeout = null;
      }
      
      // Set completion timeout with strict validation
      if (this.currentSpeechId === speechId) {
        this.aiSpeechTimeout = setTimeout(() => {
          // Triple-check speech ID before completion
          if (this.currentSpeechId === speechId) {
            this.completeUnifiedSpeech(speechId, speechData);
          } else {
            console.log(`⚠️ Speech ID changed during timeout, skipping completion for ${speechId}`);
          }
        }, speechDuration);
      } else {
        console.log(`⚠️ Speech ID mismatch after synthesis, skipping timeout setup for ${speechId}`);
        this.handleUnifiedSpeechError(new Error('Speech ID mismatch after synthesis'), speechId);
      }
      
    } catch (error) {
      console.error('❌ Error in unified speech processing:', error);
      this.handleUnifiedSpeechError(error, speechId);
    }
  }
  
  /**
   * Complete unified speech and update states
   */
  private completeUnifiedSpeech(speechId: string, speechData: any): void {
    // Only update if this is still the current speech
    if (this.currentSpeechId !== speechId) {
      console.log(`⚠️ Speech completion ID mismatch: expected ${speechId}, current ${this.currentSpeechId}`);
      return;
    }
    
    console.log(`✅ Unified speech completed (ID: ${speechId})`);
    
    // IMPORTANT: Keep transcript in live transcript box only - never add AI speech to conversation history
    // The live transcript (red box) should persist to show what the AI said
    
    // Reset speech processing state
    this.aiIsSpeaking = false;
    this.aiSpeechTimeout = null;
    this.isProcessingSpeech = false;
    this.currentSpeechId = null;
    
    // Update status based on speech type
    if (speechData.isIntroduction) {
      this.updateAIStatus('ready', 'Introduction complete - ready for questions');
      this.currentTask = 'Alex AI has introduced himself. Ready for your questions!';
    } else {
      this.updateAIStatus('listening', 'Waiting for your response');
      this.currentTask = 'Please provide your response';
    }
    
    // Process next speech in queue if any
    this.processNextUnifiedSpeech();
  }
  
  /**
   * Process next speech in unified queue
   */
  private processNextUnifiedSpeech(): void {
    if (this.speechQueue.length > 0) {
      const nextSpeech = this.speechQueue.shift()!;
      console.log('🔄 Processing next unified speech in queue');
      
      const nextSpeechData = {
        text: nextSpeech.text,
        audioUrl: nextSpeech.audioUrl,
        action: 'queued',
        isIntroduction: false,
        priority: 'normal'
      };
      
      this.processUnifiedAISpeech(nextSpeechData);
    }
  }
  
  /**
   * Handle unified speech errors
   */
  private handleUnifiedSpeechError(error: any, speechId: string): void {
    console.error(`❌ Unified speech error (ID: ${speechId}):`, error);
    
    // Reset states
    this.isProcessingSpeech = false;
    this.aiIsSpeaking = false;
    
    // Update status to indicate error but continue
    this.updateAIStatus('ready', 'Speech error - ready to continue');
    
    // Process next speech if any
    this.processNextUnifiedSpeech();
  }
  
  /**
   * Play unified AI speech with consistent handling
   */
  private async playUnifiedAISpeech(text: string, audioUrl: string | undefined, speechId: string): Promise<void> {
    try {
      console.log(`🔊 Playing unified AI speech (ID: ${speechId}):`, text.substring(0, 50) + '...');
      
      // Initialize speech synthesis if needed
      if (isPlatformBrowser(this.platformId) && !this.speechSynthesis) {
        this.speechSynthesis = window.speechSynthesis;
      }
      
      // Method 1: Use provided audio URL if available
      if (audioUrl) {
        await this.playAudioFromUrl(audioUrl);
        return;
      }
      
      // Method 2: Use Speech Synthesis API with consistent voice
      if (this.speechSynthesis) {
        await this.synthesizeUnifiedSpeech(text, speechId);
      } else {
        console.warn('Speech synthesis not available, showing text only');
        this.showSpeechFallback(text);
      }
      
    } catch (error) {
      console.error(`❌ Failed to play unified AI speech (ID: ${speechId}):`, error);
      this.showSpeechFallback(text);
    }
  }
  
  /**
   * Synthesize unified speech with consistent voice and error handling
   */
  private async synthesizeUnifiedSpeech(text: string, speechId: string): Promise<void> {
    if (!this.speechSynthesis) {
      throw new Error('Speech synthesis not available');
    }
    
    // Verify speech ID is still current
    if (this.currentSpeechId !== speechId) {
      console.log(`⚠️ Speech ID mismatch at synthesis start: expected ${speechId}, current ${this.currentSpeechId}`);
      throw new Error('Speech ID mismatch at synthesis start');
    }
    
    // Stop any existing speech before starting new one
    if (this.speechSynthesis.speaking) {
      console.log('🛑 Stopping existing speech before starting new unified speech');
      this.speechSynthesis.cancel();
      // Wait a bit for cancellation to complete
      await new Promise(resolve => setTimeout(resolve, 150));
    }
    
    // Re-verify speech ID after cancellation delay
    if (this.currentSpeechId !== speechId) {
      console.log(`⚠️ Speech ID changed during cancellation: expected ${speechId}, current ${this.currentSpeechId}`);
      throw new Error('Speech ID changed during cancellation');
    }
    
    // Ensure voices are loaded
    await this.ensureVoicesLoaded();
    
    // Create speech utterance only if speech ID is still current
    if (this.currentSpeechId !== speechId) {
      console.log(`⚠️ Speech ID changed before utterance creation: expected ${speechId}, current ${this.currentSpeechId}`);
      throw new Error('Speech ID changed before utterance creation');
    }
    
    this.currentSpeechUtterance = new SpeechSynthesisUtterance(text);
    
    // Configure speech settings
    const avatarConfig = environment.aiServices.avatarConfig;
    this.currentSpeechUtterance.rate = avatarConfig.speechRate;
    this.currentSpeechUtterance.pitch = 1.0;
    this.currentSpeechUtterance.volume = avatarConfig.speechVolume;
    
    // Use consistent female voice
    if (this.selectedFemaleVoice) {
      this.currentSpeechUtterance.voice = this.selectedFemaleVoice;
      console.log('🎤 Using unified voice:', this.selectedFemaleVoice.name);
    }
    
    return new Promise((resolve, reject) => {
      let hasEnded = false;
      let utteranceRef = this.currentSpeechUtterance;
      
      if (!utteranceRef) {
        console.error('❌ No utterance reference available');
        resolve();
        return;
      }
      
      utteranceRef.onstart = () => {
        if (this.currentSpeechId === speechId && !hasEnded) {
          console.log(`🗣️ Unified speech synthesis started (ID: ${speechId})`);
          this.aiIsSpeaking = true;
        }
      };
      
      utteranceRef.onend = () => {
        if (!hasEnded) {
          hasEnded = true;
          console.log(`🔇 Unified speech synthesis ended naturally (ID: ${speechId})`);
          if (this.currentSpeechUtterance === utteranceRef) {
            this.currentSpeechUtterance = null;
          }
          resolve();
        }
      };
      
      utteranceRef.onerror = (error: SpeechSynthesisErrorEvent) => {
        if (!hasEnded) {
          hasEnded = true;
          console.error(`❌ Unified speech synthesis error (ID: ${speechId}):`, error.error);
          if (this.currentSpeechUtterance === utteranceRef) {
            this.currentSpeechUtterance = null;
          }
          
          // Handle different error types gracefully - always resolve to continue flow
          console.log(`🔄 Resolving speech error (${error.error}) to continue interview flow`);
          resolve();
        }
      };
      
      // Final check before starting synthesis
      if (this.currentSpeechId === speechId && utteranceRef) {
        console.log(`▶️ Starting unified speech synthesis (ID: ${speechId})`);
        
        // Add a longer delay to ensure previous speech is fully stopped and prevent interruption
        setTimeout(() => {
          if (this.currentSpeechId === speechId && !hasEnded && utteranceRef) {
            try {
              this.speechSynthesis!.speak(utteranceRef);
            } catch (speakError) {
              console.error('❌ Error calling speak():', speakError);
              if (!hasEnded) {
                hasEnded = true;
                resolve();
              }
            }
          } else {
            if (!hasEnded) {
              hasEnded = true;
              console.log(`⚠️ Speech conditions changed, resolving without speaking`);
              resolve();
            }
          }
        }, 200);
      } else {
        if (!hasEnded) {
          hasEnded = true;
          console.log(`⚠️ Speech ID mismatch or no utterance, resolving without speaking`);
          resolve();
        }
      }
    });
  }
  
  /**
   * Animate transcript for unified speech
   */
  private animateUnifiedTranscript(text: string): void {
    // Skip animation if text is already displayed
    if (this.liveTranscript === text) {
      console.log('🔄 Skipping transcript animation - text already displayed');
      return;
    }
    
    // Clear current transcript
    this.liveTranscript = '';
    
    // Calculate optimal typing speed for better UX
    const wordsPerMinute = 180; // Slightly faster for better user experience
    const averageWordLength = 5;
    const charactersPerSecond = (wordsPerMinute * averageWordLength) / 60;
    const intervalMs = Math.max(1000 / charactersPerSecond, 15); // Minimum 15ms between characters
    
    let currentIndex = 0;
    
    const typeInterval = setInterval(() => {
      if (currentIndex < text.length) {
        this.liveTranscript = text.substring(0, currentIndex + 1);
        currentIndex++;
      } else {
        clearInterval(typeInterval);
        this.liveTranscript = text;
        console.log('✅ Unified transcript animation completed');
      }
    }, intervalMs);
    
    // Store for cleanup
    this.currentTypingInterval = typeInterval;
  }
  
  /**
   * DEPRECATED: Queue Alex AI speech - now redirects to unified handler
   * This method is kept for backward compatibility but routes everything to unified handler
   */
  private queueAlexAISpeech(speechData: any): void {
    console.log('⚠️ queueAlexAISpeech called (deprecated) - redirecting to unified handler');
    
    // Route to unified handler instead of processing separately
    this.handleAISpeech({
      text: speechData.text,
      audioUrl: speechData.audioUrl,
      action: speechData.action || 'speaking',
      isIntroduction: speechData.action === 'introduction' || speechData.isIntroduction,
      duration: speechData.duration,
      priority: speechData.priority || 'normal'
    });
  }
  
  /**
   * DEPRECATED: Process speech queue - now handled by unified system
   * This method is kept for backward compatibility
   */
  private async processSpeechQueue(speechData: any, speechId: string): Promise<void> {
    console.log('⚠️ processSpeechQueue called (deprecated) - speech should be handled by unified system');
    
    // This method should not be called anymore as everything goes through unified handler
    // If it is called, it means there's still some old code path being used
    console.warn('Please check why processSpeechQueue was called instead of unified handler');
  }
  
  /**
   * DEPRECATED: Process next speech in queue - now handled by unified system
   */
  private processNextSpeechInQueue(): void {
    console.log('⚠️ processNextSpeechInQueue called (deprecated) - using unified queue instead');
    
    // Use the unified queue system instead
    this.processNextUnifiedSpeech();
  }

  /**
   * Animate Alex AI speech in the live transcript
   */
  private animateAlexAISpeech(text: string): void {
    // Don't animate if text is already in live transcript (prevent duplication)
    if (this.liveTranscript === text) {
      console.log('🔄 Skipping animation - text already in live transcript');
      return;
    }
    
    // Clear current live transcript only if it's different
    if (this.liveTranscript !== text) {
      this.liveTranscript = '';
    }
    
    // Calculate typing speed (faster for better UX)
    const wordsPerMinute = 200; // Faster typing speed for better UX
    const averageWordLength = 5; // Average word length including spaces
    const charactersPerSecond = (wordsPerMinute * averageWordLength) / 60;
    const intervalMs = Math.max(1000 / charactersPerSecond, 20); // Minimum 20ms between characters
    
    let currentIndex = 0;
    
    const typeInterval = setInterval(() => {
      if (currentIndex < text.length) {
        this.liveTranscript = text.substring(0, currentIndex + 1);
        currentIndex++;
      } else {
        // Animation complete
        clearInterval(typeInterval);
        this.liveTranscript = text;
        console.log('✅ Live transcript animation completed');
      }
    }, intervalMs);
    
    // Store interval reference for cleanup if needed
    this.currentTypingInterval = typeInterval;
    
    console.log('🎬 Starting live transcript animation for:', text.substring(0, 50) + '...');
  }

  /**
   * Handle new question from AI
   */
  private handleNewQuestion(questionData: any): void {
    console.log('❓ New question from AI:', questionData);
    
    this.currentQuestion = questionData;
    this.interviewerQuestion = questionData.text;
    this.currentTask = questionData.text;
    
    // Update code editor if it's a coding question
    if (questionData.coding_required) {
      this.currentLanguage = questionData.programming_language || 'javascript';
      this.codeContent = `// ${questionData.text}\n\n// Write your ${this.currentLanguage} code here...`;
    }
    
    // IMPORTANT: Questions now show in the yellow current question box only
    // No longer adding to conversation history since we removed the blue boxes
    this.logSystemMessage('interviewer', questionData.text, new Date());
    
    this.updateAIStatus('listening', 'Waiting for your response to the question');
  }

  /**
   * Handle messages from speech service
   */
  private handleSpeechMessage(message: any): void {
    console.log('🎤 Speech service message:', message);
    
    switch (message.type) {
      case 'transcript_update':
        // Handle candidate speech transcription
        if (message.speaker === 'candidate' || !message.speaker) {
          this.liveTranscript = message.text;
          this.candidateResponse = message.text;
          this.updateAIStatus('analyzing', 'Processing your speech');
        }
        break;
        
      case 'ai_speech_generated':
        // Handle AI speech from speech service
        this.handleAISpeech({
          text: message.text,
          audioUrl: message.audioUrl,
          duration: message.duration
        });
        break;
        
      case 'speech_synthesis_ready':
        // AI speech synthesis is ready to play
        console.log('🔊 AI speech synthesis ready');
        this.updateAIStatus('speaking', 'Speaking...');
        break;
        
      case 'speech_end':
        this.updateAIStatus('thinking', 'Analyzing your response');
        break;
        
      case 'candidate_speech_start':
        this.updateAIStatus('listening', 'Listening...');
        break;
        
      case 'candidate_speech_end':
        this.updateAIStatus('thinking', 'Processing your response');
        break;
        
      case 'confidence_score':
        this.aiAvatarState.confidence = message.score;
        break;
        
      case 'error':
        console.error('❌ Speech service error:', message.error);
        this.updateAIStatus('ready', 'Speech error occurred');
        break;
        
      default:
        console.log('Unknown speech message type:', message.type);
    }
  }

  /**
   * Handle messages from analytics service
   */
  private handleAnalyticsMessage(message: any): void {
    switch (message.type) {
      case 'real_time_analysis':
        this.updateAIAnalysis(message.data);
        break;
        
      case 'engagement_score':
        console.log('📈 Engagement score:', message.score);
        break;
        
      case 'bias_alert':
        console.warn('⚠️ Bias detected:', message.details);
        break;
    }
  }

  /**
   * Update AI avatar status and action
   */
  private updateAIStatus(status: typeof this.aiAvatarState.status, action: string): void {
    this.aiAvatarState.status = status;
    this.aiAvatarState.currentAction = action;
    this.aiAvatarState.lastInteraction = new Date();
    this.aiStatus = action;
    
    // Update visual indicators
    this.aiIsInteracting = status === 'speaking' || status === 'analyzing' || status === 'thinking';
    
    console.log(`🤖 AI Status: ${status} - ${action}`);
  }

  /**
   * Update AI analysis state
   */
  private updateAIAnalysis(analysisData: any): void {
    this.aiAvatarState.isAnalyzing = true;
    this.aiAvatarState.confidence = analysisData.confidence || this.aiAvatarState.confidence;
    
    setTimeout(() => {
      this.aiAvatarState.isAnalyzing = false;
    }, 2000);
  }

  /**
   * Start AI avatar visual animations
   */
  private startAIAvatarAnimations(): void {
    // Voice bars animation
    setInterval(() => {
      if (this.aiIsSpeaking) {
        this.voiceBars = this.voiceBars.map(() => ({
          height: Math.random() * 80 + 20
        }));
      } else {
        this.voiceBars = this.voiceBars.map(() => ({
          height: Math.random() * 20 + 10
        }));
      }
    }, 100);
    
    // AI interaction pulse
    setInterval(() => {
      if (this.aiIsInteracting) {
        // Trigger pulse animation via CSS class
        const aiElement = document.querySelector('.ai-avatar');
        if (aiElement) {
          aiElement.classList.toggle('pulse');
        }
      }
    }, 1000);
  }

  /**
   * Estimate speech duration based on text length
   */
  private estimateSpeechDuration(text: string): number {
    // Average speaking rate: ~150 words per minute
    const words = text.split(' ').length;
    const wordsPerSecond = 150 / 60;
    const baseDuration = (words / wordsPerSecond) * 1000;
    
    // Add some padding and minimum duration
    return Math.max(baseDuration + 1000, 2000);
  }
  
  /**
   * Calculate more accurate speech duration for Alex AI speech
   */
  private calculateAccurateSpeechDuration(text: string): number {
    if (!text || text.trim().length === 0) {
      return 2000; // 2 seconds minimum
    }
    
    // More sophisticated calculation considering:
    // - Character count (including punctuation)
    // - Word boundaries
    // - Speech synthesis processing time
    const characters = text.length;
    const words = text.trim().split(/\s+/).length;
    
    // Base calculation: Average speaking rate of 150 WPM
    const baseWordsPerSecond = 150 / 60; // 2.5 words per second
    const baseDurationFromWords = (words / baseWordsPerSecond) * 1000;
    
    // Character-based calculation for more accuracy (avg 5 chars per word + spaces)
    const averageCharsPerSecond = (baseWordsPerSecond * 5) + (baseWordsPerSecond * 0.5); // ~13.75 chars/sec
    const baseDurationFromChars = (characters / averageCharsPerSecond) * 1000;
    
    // Use the longer of the two calculations for safety
    const calculatedDuration = Math.max(baseDurationFromWords, baseDurationFromChars);
    
    // Add extra time for:
    // - Speech synthesis processing (500ms)
    // - Natural pauses and breathing (20% of base duration)
    // - Buffer to prevent cutting off (1 second)
    const processingBuffer = 500;
    const naturalPausesBuffer = calculatedDuration * 0.2;
    const safetyBuffer = 1000;
    
    const totalDuration = calculatedDuration + processingBuffer + naturalPausesBuffer + safetyBuffer;
    
    // Ensure minimum 3 seconds for any speech, maximum 30 seconds
    const finalDuration = Math.max(Math.min(totalDuration, 30000), 3000);
    
    console.log('📊 Speech duration calculation:', {
      text: text.substring(0, 50) + (text.length > 50 ? '...' : ''),
      characters: characters,
      words: words,
      baseDurationFromWords: Math.round(baseDurationFromWords),
      baseDurationFromChars: Math.round(baseDurationFromChars),
      calculatedDuration: Math.round(calculatedDuration),
      totalBuffers: Math.round(processingBuffer + naturalPausesBuffer + safetyBuffer),
      finalDuration: Math.round(finalDuration)
    });
    
    return finalDuration;
  }

  /**
   * Play AI speech using Speech Synthesis API or audio URL
   */
  private async playAISpeech(text: string, audioUrl?: string): Promise<void> {
    try {
      console.log('🔊 Playing AI speech:', text.substring(0, 50) + '...');
      
      // Stop any current speech
      this.stopCurrentSpeech();
      
      // Initialize speech synthesis if needed
      if (isPlatformBrowser(this.platformId) && !this.speechSynthesis) {
        this.speechSynthesis = window.speechSynthesis;
      }
      
      // Method 1: Use provided audio URL if available
      if (audioUrl) {
        await this.playAudioFromUrl(audioUrl);
        return;
      }
      
      // Method 2: Use Speech Synthesis API as fallback
      if (this.speechSynthesis) {
        await this.synthesizeAndPlaySpeech(text);
      } else {
        console.warn('Speech synthesis not available, showing text only');
        this.showSpeechFallback(text);
      }
      
    } catch (error) {
      console.error('❌ Failed to play AI speech:', error);
      // Fallback: just show the text in transcript
      this.showSpeechFallback(text);
    }
  }

  /**
   * Play AI speech with enhanced error handling and retry logic
   */
  private async playAISpeechWithErrorHandling(text: string, audioUrl?: string): Promise<void> {
    this.speechRetryCount = 0;
    await this.attemptPlayAISpeech(text, audioUrl);
  }
  
  /**
   * Play AI speech with enhanced handling for interruption prevention
   */
  private async playAISpeechWithEnhancedHandling(text: string, audioUrl: string | undefined, speechId: string): Promise<void> {
    try {
      console.log(`🔊 Playing AI speech with ID ${speechId}:`, text.substring(0, 50) + '...');
      
      // Initialize speech synthesis if needed
      if (isPlatformBrowser(this.platformId) && !this.speechSynthesis) {
        this.speechSynthesis = window.speechSynthesis;
      }
      
      // Method 1: Use provided audio URL if available
      if (audioUrl) {
        await this.playAudioFromUrl(audioUrl);
        return;
      }
      
      // Method 2: Use Speech Synthesis API with enhanced handling
      if (this.speechSynthesis) {
        await this.synthesizeAndPlaySpeechWithEnhancedHandling(text, speechId);
      } else {
        console.warn('Speech synthesis not available, showing text only');
        this.showSpeechFallback(text);
      }
      
    } catch (error) {
      console.error(`❌ Failed to play AI speech (ID: ${speechId}):`, error);
      // Show fallback instead of retrying for enhanced version
      this.showSpeechFallback(text);
    }
  }

  /**
   * Attempt to play AI speech with retry logic
   */
  private async attemptPlayAISpeech(text: string, audioUrl?: string): Promise<void> {
    try {
      console.log(`🔊 Attempting to play AI speech (attempt ${this.speechRetryCount + 1}):`, text.substring(0, 50) + '...');
      
      // Stop any current speech
      this.stopCurrentSpeech();
      
      // Initialize speech synthesis if needed
      if (isPlatformBrowser(this.platformId) && !this.speechSynthesis) {
        this.speechSynthesis = window.speechSynthesis;
      }
      
      // Method 1: Use provided audio URL if available
      if (audioUrl) {
        await this.playAudioFromUrl(audioUrl);
        return;
      }
      
      // Method 2: Use Speech Synthesis API with enhanced error handling
      if (this.speechSynthesis) {
        await this.synthesizeAndPlaySpeechWithRetry(text);
      } else {
        console.warn('Speech synthesis not available, showing text only');
        this.showSpeechFallback(text);
      }
      
    } catch (error) {
      console.error(`❌ Failed to play AI speech (attempt ${this.speechRetryCount + 1}):`, error);
      
      if (this.speechRetryCount < this.maxSpeechRetries) {
        this.speechRetryCount++;
        console.log(`🔄 Retrying speech synthesis (attempt ${this.speechRetryCount + 1})...`);
        setTimeout(() => this.attemptPlayAISpeech(text, audioUrl), 500);
      } else {
        console.error('❌ Max speech retry attempts reached, showing fallback');
        this.showSpeechFallback(text);
      }
    }
  }

  /**
   * Play audio from URL
   */
  private async playAudioFromUrl(audioUrl: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const audio = new Audio(audioUrl);
      
      audio.onloadeddata = () => {
        console.log('✅ Audio loaded, starting playback');
        audio.play().then(() => {
          console.log('🔊 Audio playback started');
        }).catch(error => {
          console.error('Audio playback failed:', error);
          reject(error);
        });
      };
      
      audio.onended = () => {
        console.log('🔇 Audio playback ended');
        resolve();
      };
      
      audio.onerror = (error) => {
        console.error('Audio loading failed:', error);
        reject(error);
      };
      
      // Set volume and load
      audio.volume = 0.8;
      audio.load();
    });
  }

  /**
   * Initialize voices and ensure they are loaded
   */
  private async ensureVoicesLoaded(): Promise<void> {
    if (!this.speechSynthesis) {
      console.warn('Speech synthesis not available');
      return;
    }
    
    if (this.voicesLoaded && this.selectedFemaleVoice) {
      return; // Already loaded and voice selected
    }
    
    return new Promise((resolve) => {
      const loadVoices = () => {
        const voices = this.speechSynthesis!.getVoices();
        
        if (voices.length > 0) {
          console.log('🎤 Available voices loaded:', voices.length);
          console.log('🎤 Voice details:', voices.map(v => ({
            name: v.name,
            lang: v.lang,
            gender: this.inferVoiceGender(v),
            localService: v.localService
          })));
          
          this.voicesLoaded = true;
          this.selectConsistentFemaleVoice(voices);
          resolve();
        } else if (this.voiceSelectionAttempts < this.maxVoiceSelectionAttempts) {
          this.voiceSelectionAttempts++;
          console.log(`🔄 Attempt ${this.voiceSelectionAttempts}: Waiting for voices to load...`);
          setTimeout(loadVoices, 200);
        } else {
          console.warn('⚠️ Max voice loading attempts reached, using default voice');
          this.voicesLoaded = true;
          resolve();
        }
      };
      
      loadVoices();
      
      // Also listen for voice changes event (some browsers fire this)
      if (this.speechSynthesis && typeof this.speechSynthesis.onvoiceschanged !== 'undefined') {
        this.speechSynthesis.onvoiceschanged = () => {
          const voices = this.speechSynthesis!.getVoices();
          if (voices.length > 0 && !this.voicesLoaded) {
            console.log('🎤 Voices loaded via onvoiceschanged event');
            this.voicesLoaded = true;
            this.selectConsistentFemaleVoice(voices);
            resolve();
          }
        };
      }
    });
  }
  
  /**
   * Infer voice gender based on name patterns and characteristics
   */
  private inferVoiceGender(voice: SpeechSynthesisVoice): 'female' | 'male' | 'unknown' {
    const voiceName = voice.name.toLowerCase();
    
    // Common female voice name patterns
    const femalePatterns = [
      'zira', 'hazel', 'sara', 'susan', 'samantha', 'victoria', 
      'allison', 'ava', 'veena', 'vicki', 'karen', 'heather',
      'female', 'woman', 'girl', 'lady', 'alice', 'anna', 
      'bella', 'chloe', 'diana', 'emma', 'fiona', 'grace',
      'helen', 'ivy', 'jane', 'kate', 'lisa', 'maria',
      'nora', 'olivia', 'penny', 'rachel', 'sophie', 'tessa',
      'elena', 'moira', 'amelie', 'claire', 'joanna',
      'kendra', 'kimberly', 'salli', 'nicole', 'russell',
      'tanja', 'marlene', 'vicki', 'petra', 'gudrun'
    ];
    
    // Common male voice name patterns
    const malePatterns = [
      'david', 'mark', 'alex', 'daniel', 'tom', 'george',
      'male', 'man', 'boy', 'guy', 'brad', 'bruce',
      'charles', 'edward', 'frank', 'gary', 'henry',
      'james', 'john', 'kevin', 'larry', 'michael',
      'nathan', 'oscar', 'paul', 'richard', 'steve',
      'victor', 'william', 'matthew', 'jorge', 'diego',
      'hans', 'ralf', 'yannick', 'thomas'
    ];
    
    // Check for female patterns first (prioritize female voices)
    for (const pattern of femalePatterns) {
      if (voiceName.includes(pattern)) {
        return 'female';
      }
    }
    
    // Check for male patterns
    for (const pattern of malePatterns) {
      if (voiceName.includes(pattern)) {
        return 'male';
      }
    }
    
    // Special handling for specific voice names that don't follow patterns
    if (voiceName.includes('google') && voiceName.includes('female')) {
      return 'female';
    }
    
    if (voiceName.includes('google') && voiceName.includes('male')) {
      return 'male';
    }
    
    return 'unknown';
  }
  
  /**
   * Select a consistent female voice and cache it
   */
  private selectConsistentFemaleVoice(voices: SpeechSynthesisVoice[]): void {
    const avatarConfig = environment.aiServices.avatarConfig;
    
    console.log('🎤 Selecting consistent female voice from', voices.length, 'available voices');
    
    // Filter voices by language first
    const englishVoices = voices.filter(voice => 
      voice.lang.toLowerCase().startsWith(avatarConfig.voiceLanguage.toLowerCase()) ||
      voice.lang.toLowerCase().startsWith(avatarConfig.voiceFallbackLanguage.toLowerCase())
    );
    
    console.log('🌐 Filtered to', englishVoices.length, 'English voices');
    
    // Categorize voices by inferred gender
    const femaleVoices = englishVoices.filter(voice => this.inferVoiceGender(voice) === 'female');
    const unknownVoices = englishVoices.filter(voice => this.inferVoiceGender(voice) === 'unknown');
    const maleVoices = englishVoices.filter(voice => this.inferVoiceGender(voice) === 'male');
    
    console.log('👩 Female voices found:', femaleVoices.length);
    console.log('❓ Unknown gender voices:', unknownVoices.length);
    console.log('👨 Male voices found:', maleVoices.length);
    
    let selectedVoice: SpeechSynthesisVoice | null = null;
    
    // Priority 1: Try to find a voice from preferred list that is female
    for (const preferredName of avatarConfig.preferredVoices) {
      const voice = femaleVoices.find(v => 
        v.name.toLowerCase().includes(preferredName.toLowerCase())
      );
      
      if (voice) {
        selectedVoice = voice;
        console.log('✅ Found preferred female voice:', voice.name);
        break;
      }
    }
    
    // Priority 2: Select best female voice based on quality indicators
    if (!selectedVoice && femaleVoices.length > 0) {
      // Prefer non-local (network/cloud) voices for better quality
      const cloudFemaleVoices = femaleVoices.filter(v => !v.localService);
      
      if (cloudFemaleVoices.length > 0) {
        selectedVoice = cloudFemaleVoices[0];
        console.log('☁️ Selected cloud female voice:', selectedVoice.name);
      } else {
        selectedVoice = femaleVoices[0];
        console.log('📱 Selected local female voice:', selectedVoice.name);
      }
    }
    
    // Priority 3: Try preferred names in unknown gender voices (might be female)
    if (!selectedVoice) {
      for (const preferredName of avatarConfig.preferredVoices) {
        const voice = unknownVoices.find(v => 
          v.name.toLowerCase().includes(preferredName.toLowerCase())
        );
        
        if (voice) {
          selectedVoice = voice;
          console.log('🤔 Selected preferred voice with unknown gender:', voice.name);
          break;
        }
      }
    }
    
    // Priority 4: Use first unknown voice as fallback
    if (!selectedVoice && unknownVoices.length > 0) {
      selectedVoice = unknownVoices[0];
      console.log('⚠️ Selected unknown gender voice as fallback:', selectedVoice.name);
    }
    
    // Priority 5: Final fallback to any English voice
    if (!selectedVoice && englishVoices.length > 0) {
      selectedVoice = englishVoices[0];
      console.log('🆘 Selected any available voice as final fallback:', selectedVoice.name);
    }
    
    // Cache the selected voice
    this.selectedFemaleVoice = selectedVoice;
    
    if (this.selectedFemaleVoice) {
      console.log('🎯 Cached voice for consistent use:', {
        name: this.selectedFemaleVoice.name,
        lang: this.selectedFemaleVoice.lang,
        gender: this.inferVoiceGender(this.selectedFemaleVoice),
        localService: this.selectedFemaleVoice.localService,
        voiceURI: this.selectedFemaleVoice.voiceURI
      });
    } else {
      console.error('❌ No suitable voice found for speech synthesis');
    }
  }
  
  /**
   * Synthesize and play speech using Web Speech API with consistent female voice
   */
  private async synthesizeAndPlaySpeech(text: string): Promise<void> {
    if (!this.speechSynthesis) {
      console.warn('Speech synthesis not available');
      throw new Error('Speech synthesis not available');
    }
    
    // Ensure voices are loaded and female voice is selected
    await this.ensureVoicesLoaded();
    
    // Create speech utterance
    this.currentSpeechUtterance = new SpeechSynthesisUtterance(text);
    
    // Configure speech settings from environment
    const avatarConfig = environment.aiServices.avatarConfig;
    this.currentSpeechUtterance.rate = avatarConfig.speechRate;
    this.currentSpeechUtterance.pitch = 1.0;
    this.currentSpeechUtterance.volume = avatarConfig.speechVolume;
    
    // Use the cached consistent female voice
    if (this.selectedFemaleVoice) {
      this.currentSpeechUtterance.voice = this.selectedFemaleVoice;
      console.log('🎤 Using consistent female voice:', this.selectedFemaleVoice.name, 
                 '(Gender:', this.inferVoiceGender(this.selectedFemaleVoice), ')');
    } else {
      console.warn('⚠️ No female voice cached, using browser default');
    }
    
    return new Promise((resolve, reject) => {
      // Set up event handlers
      this.currentSpeechUtterance!.onstart = () => {
        console.log('🗣️ Female speech synthesis started with voice:', 
                   this.currentSpeechUtterance?.voice?.name || 'default');
        this.aiIsSpeaking = true;
      };
      
      this.currentSpeechUtterance!.onend = () => {
        console.log('🔇 Female speech synthesis ended naturally');
        this.aiIsSpeaking = false;
        this.currentSpeechUtterance = null;
        resolve();
      };
      
      this.currentSpeechUtterance!.onerror = (error: SpeechSynthesisErrorEvent) => {
        // Handle different error types with appropriate logging levels
        if (error.error === 'interrupted' || error.error === 'canceled') {
          console.log('🔄 Speech was interrupted or canceled, resolving normally');
        } else {
          console.error('❌ Female speech synthesis error:', {
            error: error.error,
            type: error.type,
            voice: this.currentSpeechUtterance?.voice?.name,
            text: text.substring(0, 100) + '...'
          });
        }
        
        this.aiIsSpeaking = false;
        this.currentSpeechUtterance = null;
        
        // Don't reject immediately for certain error types
        if (error.error === 'interrupted' || error.error === 'canceled') {
          resolve();
        } else {
          reject(error);
        }
      };
      
      // Start speech synthesis
      console.log('▶️ Starting speech synthesis with female voice');
      this.speechSynthesis!.speak(this.currentSpeechUtterance!);
    });
  }

  /**
   * Synthesize and play speech with retry logic
   */
  private async synthesizeAndPlaySpeechWithRetry(text: string): Promise<void> {
    try {
      await this.synthesizeAndPlaySpeech(text);
    } catch (error: any) {
      console.error('Speech synthesis failed:', error);
      
      // If it's a voice-related error, try with browser default voice
      if (error.error === 'voice-unavailable' || error.error === 'synthesis-failed') {
        console.log('🔄 Retrying with browser default voice...');
        this.selectedFemaleVoice = null; // Force default voice
        await this.synthesizeAndPlaySpeech(text);
      } else {
        throw error;
      }
    }
  }
  
  /**
   * Synthesize and play speech with enhanced interruption handling
   */
  private async synthesizeAndPlaySpeechWithEnhancedHandling(text: string, speechId: string): Promise<void> {
    if (!this.speechSynthesis) {
      console.warn('Speech synthesis not available');
      throw new Error('Speech synthesis not available');
    }
    
    // Ensure voices are loaded and female voice is selected
    await this.ensureVoicesLoaded();
    
    // Create speech utterance
    this.currentSpeechUtterance = new SpeechSynthesisUtterance(text);
    
    // Configure speech settings from environment
    const avatarConfig = environment.aiServices.avatarConfig;
    this.currentSpeechUtterance.rate = avatarConfig.speechRate;
    this.currentSpeechUtterance.pitch = 1.0;
    this.currentSpeechUtterance.volume = avatarConfig.speechVolume;
    
    // Use the cached consistent female voice
    if (this.selectedFemaleVoice) {
      this.currentSpeechUtterance.voice = this.selectedFemaleVoice;
      console.log('🎤 Using consistent female voice:', this.selectedFemaleVoice.name, 
                 '(Gender:', this.inferVoiceGender(this.selectedFemaleVoice), ')');
    } else {
      console.warn('⚠️ No female voice cached, using browser default');
    }
    
    return new Promise((resolve, reject) => {
      // Set up event handlers with speech ID tracking
      this.currentSpeechUtterance!.onstart = () => {
        // Only proceed if this is still the current speech
        if (this.currentSpeechId === speechId) {
          console.log(`🗣️ Speech synthesis started (ID: ${speechId}) with voice:`, 
                     this.currentSpeechUtterance?.voice?.name || 'default');
          this.aiIsSpeaking = true;
        } else {
          console.log(`⚠️ Speech started but ID mismatch: expected ${speechId}, current ${this.currentSpeechId}`);
          // Cancel this speech as it's outdated
          if (this.speechSynthesis) {
            this.speechSynthesis.cancel();
          }
          resolve();
        }
      };
      
      this.currentSpeechUtterance!.onend = () => {
        // Only proceed if this is still the current speech
        if (this.currentSpeechId === speechId) {
          console.log(`🔇 Speech synthesis ended naturally (ID: ${speechId})`);
          this.aiIsSpeaking = false;
          this.currentSpeechUtterance = null;
          resolve();
        } else {
          console.log(`⚠️ Speech ended but ID mismatch: expected ${speechId}, current ${this.currentSpeechId}`);
          resolve();
        }
      };
      
      this.currentSpeechUtterance!.onerror = (error: SpeechSynthesisErrorEvent) => {
        console.error(`❌ Speech synthesis error (ID: ${speechId}):`, {
          error: error.error,
          type: error.type,
          voice: this.currentSpeechUtterance?.voice?.name,
          text: text.substring(0, 100) + '...'
        });
        
        this.aiIsSpeaking = false;
        this.currentSpeechUtterance = null;
        
        // Handle different error types gracefully
        if (error.error === 'interrupted' || error.error === 'canceled') {
          console.log(`🔄 Speech was interrupted or canceled (ID: ${speechId}), resolving normally`);
          resolve(); // Don't treat interruption as error
        } else if (error.error === 'synthesis-unavailable' || error.error === 'synthesis-failed') {
          console.log(`🔄 Synthesis failed (ID: ${speechId}), trying fallback`);
          reject(error);
        } else {
          // For other errors, still resolve to prevent blocking
          console.log(`⚠️ Unknown speech error (ID: ${speechId}), resolving to continue`);
          resolve();
        }
      };
      
      // Start speech synthesis only if this is still the current speech
      if (this.currentSpeechId === speechId) {
        console.log(`▶️ Starting speech synthesis (ID: ${speechId}) with female voice`);
        this.speechSynthesis!.speak(this.currentSpeechUtterance!);
      } else {
        console.log(`⚠️ Speech ID mismatch at start: expected ${speechId}, current ${this.currentSpeechId}`);
        resolve();
      }
    });
  }

  /**
   * Stop current speech synthesis
   */
  private stopCurrentSpeech(): void {
    console.log('🛑 Stopping current speech synthesis');
    
    // Cancel speech synthesis if active
    if (this.speechSynthesis && this.speechSynthesis.speaking) {
      try {
        this.speechSynthesis.cancel();
      } catch (error) {
        console.warn('Warning: Error cancelling speech synthesis:', error);
      }
    }
    
    // Clear utterance reference
    if (this.currentSpeechUtterance) {
      this.currentSpeechUtterance = null;
    }
    
    // Clear timeout
    if (this.aiSpeechTimeout) {
      clearTimeout(this.aiSpeechTimeout);
      this.aiSpeechTimeout = null;
    }
    
    // Clear typing animation
    if (this.currentTypingInterval) {
      clearInterval(this.currentTypingInterval);
      this.currentTypingInterval = null;
    }
    
    // Clear speech queue and processing state
    this.speechQueue = [];
    this.isProcessingSpeech = false;
    this.currentSpeechId = null;
    
    // Reset AI speaking state
    this.aiIsSpeaking = false;
    
    console.log('✅ Current speech stopped and state reset');
  }
  
  /**
   * Force stop all speech with aggressive cleanup
   */
  private forceStopAllSpeech(): void {
    console.log('🚨 FORCE STOPPING ALL SPEECH - aggressive cleanup');
    
    // Stop current speech
    this.stopCurrentSpeech();
    
    // Force cancel speech synthesis multiple times to ensure it stops
    if (this.speechSynthesis) {
      try {
        this.speechSynthesis.cancel();
        // Wait a bit and cancel again
        setTimeout(() => {
          if (this.speechSynthesis && this.speechSynthesis.speaking) {
            this.speechSynthesis.cancel();
          }
        }, 50);
      } catch (error) {
        console.warn('Warning: Error in force speech cancellation:', error);
      }
    }
    
    // Clear ALL intervals and timeouts that might be running
    if (this.currentTypingInterval) {
      clearInterval(this.currentTypingInterval);
      this.currentTypingInterval = null;
    }
    
    if (this.aiSpeechTimeout) {
      clearTimeout(this.aiSpeechTimeout);
      this.aiSpeechTimeout = null;
    }
    
    // Reset ALL speech-related states
    this.speechQueue = [];
    this.isProcessingSpeech = false;
    this.currentSpeechId = null;
    this.currentSpeechUtterance = null;
    this.aiIsSpeaking = false;
    this.aiIsInteracting = false;
    
    console.log('🧹 FORCE STOP completed - all speech states cleared');
  }

  /**
   * Show speech fallback when audio fails
   */
  private showSpeechFallback(text: string): void {
    // Highlight the text in the transcript as spoken
    const transcriptElement = document.querySelector('.live-transcript');
    if (transcriptElement) {
      transcriptElement.classList.add('ai-speaking');
      
      setTimeout(() => {
        transcriptElement.classList.remove('ai-speaking');
      }, this.estimateSpeechDuration(text));
    }
  }

  /**
   * Send message to AI avatar
   */
  private sendMessageToAI(type: string, data: any): void {
    const message = {
      type: type,
      data: data,
      timestamp: new Date().toISOString(),
      sessionId: this.sessionId
    };
    
    if (this.orchestratorWS && this.orchestratorWS.readyState === WebSocket.OPEN) {
      this.orchestratorWS.send(JSON.stringify(message));
    }
  }

  /**
   * Notify AI of response completion
   */
  private notifyAIResponseComplete(): void {
    this.sendMessageToAI('response_complete', {
      transcript: this.liveTranscript,
      code: this.currentQuestion?.coding_required ? this.codeContent : null,
      duration: this.timerSeconds
    });
    
    this.updateAIStatus('analyzing', 'Processing your response...');
  }

  /**
   * Handle session update from orchestrator
   */
  private handleSessionUpdate(updateData: any): void {
    console.log('📊 Session update:', updateData);
    
    if (updateData.status) {
      // Update session status
      if (this.session) {
        this.session.status = updateData.status;
      }
    }
    
    if (updateData.progress) {
      // Update interview progress
      console.log('Progress:', updateData.progress);
    }
  }

  /**
   * Disconnect AI avatar WebSocket connections
   */
  private disconnectAIAvatarConnections(): void {
    console.log('🔌 Disconnecting AI avatar connections...');
    
    if (this.orchestratorWS) {
      this.orchestratorWS.close();
      this.orchestratorWS = null;
    }
    
    if (this.speechWS) {
      this.speechWS.close();
      this.speechWS = null;
    }
    
    if (this.analyticsWS) {
      this.analyticsWS.close();
      this.analyticsWS = null;
    }
    
    this.updateAIStatus('ready', 'Disconnected');
  }


  /**
   * Enhanced end response with AI notification
   */
  async endResponseWithAI(): Promise<void> {
    // Notify AI first
    this.notifyAIResponseComplete();
    
    // Then proceed with normal response submission
    await this.endResponse();
  }

  /**
   * End interview for any role (unified flow)
   */
  async endInterview(): Promise<void> {
    try {
      console.log('🏁 Ending interview session...');

      // Stop recording and timer
      this.isRecording = false;
      this.isInterviewStarted = false;

      // End interview on backend
      if (this.sessionId) {
        await this.interviewService.endInterview(this.sessionId);
      }

      this.logSystemMessage('system', 'Interview session ended', new Date());

      // Cleanup and redirect
      this.cleanup();

      setTimeout(() => {
        // Redirect recruiters and candidates to their respective dashboards if needed
        const target = this.isRecruiter() ? '/recruiter-dashboard' : '/dashboard';
        this.router.navigate([target], {
          queryParams: { sessionEnded: this.sessionId }
        });
      }, 1000);

    } catch (error) {
      console.error('Error ending interview:', error);
      this.router.navigate(['/dashboard']);
    }
  }

  // ==================== MEDIA PERMISSIONS HANDLING ====================

  /**
   * Check media permissions status before requesting access
   */
  private async checkMediaPermissions(): Promise<void> {
    try {
      if (navigator.permissions) {
        const cameraPermission = await navigator.permissions.query({ name: 'camera' as PermissionName });
        const microphonePermission = await navigator.permissions.query({ name: 'microphone' as PermissionName });
        
        console.log('📹 Camera permission:', cameraPermission.state);
        console.log('🎤 Microphone permission:', microphonePermission.state);
        
        if (cameraPermission.state === 'denied' || microphonePermission.state === 'denied') {
          throw new Error('Camera or microphone access has been denied');
        }
      }
    } catch (error) {
      console.warn('Could not check permissions:', error);
      // Continue anyway, getUserMedia will handle the actual permission request
    }
  }

  /**
   * Get user media with retry logic and fallbacks
   */
  private async getUserMediaWithRetry(retryCount: number = 0): Promise<MediaStream> {
    const maxRetries = 3;
    
    try {
      // First attempt: Full quality video + audio
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 1280, min: 640 },
          height: { ideal: 720, min: 480 },
          frameRate: { ideal: 30, min: 15 }
        },
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true
        }
      });
      
      console.log('✅ Successfully obtained media stream');
      return stream;
      
    } catch (error) {
      console.warn(`Media access attempt ${retryCount + 1} failed:`, error);
      
      if (retryCount < maxRetries) {
        // Retry with lower quality settings
        return this.getUserMediaWithFallback(retryCount + 1);
      } else {
        throw error;
      }
    }
  }

  /**
   * Get user media with progressive fallbacks
   */
  private async getUserMediaWithFallback(attempt: number): Promise<MediaStream> {
    const fallbackConfigs = [
      // Attempt 2: Lower quality video
      {
        video: {
          width: { ideal: 640 },
          height: { ideal: 480 },
          frameRate: { ideal: 15 }
        },
        audio: true
      },
      // Attempt 3: Basic video
      {
        video: true,
        audio: true
      },
      // Attempt 4: Audio only
      {
        video: false,
        audio: true
      }
    ];
    
    const config = fallbackConfigs[attempt - 2];
    if (!config) {
      throw new Error('All media access attempts failed');
    }
    
    try {
      console.log(`🔄 Retrying with fallback config ${attempt - 1}:`, config);
      const stream = await navigator.mediaDevices.getUserMedia(config);
      
      if (!config.video) {
        this.showMediaWarning('Camera access unavailable. Interview will continue with audio only.');
        this.isCameraEnabled = false;
      }
      
      return stream;
      
    } catch (error) {
      if (attempt < 4) {
        return this.getUserMediaWithFallback(attempt + 1);
      }
      throw error;
    }
  }

  /**
   * Handle WebRTC initialization errors
   */
  private handleWebRTCError(error: any): void {
    let errorMessage = 'Failed to initialize video/audio';
    let userMessage = 'Unable to access camera and microphone. Please check your permissions and try again.';
    
    if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
      errorMessage = 'Media permissions denied';
      userMessage = 'Camera and microphone access is required for the interview. Please allow permissions and refresh the page.';
      this.showPermissionError(userMessage);
    } else if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
      errorMessage = 'No media devices found';
      userMessage = 'No camera or microphone was found on your device. Please connect media devices and try again.';
      this.showDeviceError(userMessage);
    } else if (error.name === 'NotSupportedError') {
      errorMessage = 'Media not supported';
      userMessage = 'Your browser does not support the required media features. Please use a modern browser like Chrome, Firefox, or Safari.';
      this.showBrowserError(userMessage);
    } else if (error.message?.includes('Media devices not supported')) {
      errorMessage = 'Browser compatibility issue';
      userMessage = 'Your browser does not support media devices. Please use a modern browser and ensure you are on HTTPS.';
      this.showBrowserError(userMessage);
    } else {
      // Generic error
      this.showGenericMediaError(error.message || 'Unknown error occurred');
    }
    
    console.error('🚨 WebRTC Error:', errorMessage, error);
  }

  /**
   * Show permission error dialog
   */
  private showPermissionError(message: string): void {
    this.showMediaErrorDialog('Camera & Microphone Required', message, [
      {
        text: 'Allow Permissions',
        action: () => this.requestPermissionsAgain()
      },
      {
        text: 'Help',
        action: () => this.showPermissionHelp()
      }
    ]);
  }

  /**
   * Show device error dialog
   */
  private showDeviceError(message: string): void {
    this.showMediaErrorDialog('No Media Devices', message, [
      {
        text: 'Try Again',
        action: () => this.retryMediaAccess()
      },
      {
        text: 'Continue Audio Only',
        action: () => this.continueAudioOnly()
      }
    ]);
  }

  /**
   * Show browser compatibility error
   */
  private showBrowserError(message: string): void {
    this.showMediaErrorDialog('Browser Not Supported', message, [
      {
        text: 'Open in Chrome',
        action: () => window.open('https://www.google.com/chrome/', '_blank')
      },
      {
        text: 'Try Anyway',
        action: () => this.retryMediaAccess()
      }
    ]);
  }

  /**
   * Show generic media error
   */
  private showGenericMediaError(details: string): void {
    this.showMediaErrorDialog('Media Access Failed', 
      `There was an issue accessing your camera or microphone: ${details}`, [
      {
        text: 'Try Again',
        action: () => this.retryMediaAccess()
      },
      {
        text: 'Contact Support',
        action: () => this.contactSupport()
      }
    ]);
  }

  /**
   * Show media error dialog with actions
   */
  private showMediaErrorDialog(title: string, message: string, actions: Array<{text: string, action: () => void}>): void {
    const errorDiv = document.createElement('div');
    errorDiv.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,0.8);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10000;
    `;
    
    const actionsHtml = actions.map((action, index) => 
      `<button id="media-error-action-${index}" 
               style="background: ${index === 0 ? '#3498db' : '#95a5a6'}; color: white; border: none; 
                      padding: 12px 24px; margin: 0 10px; border-radius: 6px; cursor: pointer; font-weight: 600;">
        ${action.text}
       </button>`
    ).join('');
    
    errorDiv.innerHTML = `
      <div style="background: white; border-radius: 12px; padding: 40px; max-width: 600px; text-align: center; box-shadow: 0 20px 60px rgba(0,0,0,0.3);">
        <div style="color: #e74c3c; margin-bottom: 20px;">
          <i class="fas fa-video-slash" style="font-size: 64px;"></i>
        </div>
        <h2 style="color: #2c3e50; margin-bottom: 20px;">${title}</h2>
        <p style="color: #7f8c8d; margin-bottom: 30px; line-height: 1.6; font-size: 16px;">${message}</p>
        <div style="margin-top: 30px;">
          ${actionsHtml}
        </div>
      </div>
    `;
    
    document.body.appendChild(errorDiv);
    
    // Attach event listeners
    actions.forEach((action, index) => {
      const button = document.getElementById(`media-error-action-${index}`);
      if (button) {
        button.addEventListener('click', () => {
          document.body.removeChild(errorDiv);
          action.action();
        });
      }
    });
  }

  /**
   * Show media warning (non-blocking)
   */
  private showMediaWarning(message: string): void {
    const warningDiv = document.createElement('div');
    warningDiv.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: #f39c12;
      color: white;
      padding: 15px 20px;
      border-radius: 6px;
      z-index: 9999;
      box-shadow: 0 4px 12px rgba(243, 156, 18, 0.3);
      font-weight: 600;
      max-width: 400px;
    `;
    
    warningDiv.innerHTML = `
      <i class="fas fa-exclamation-triangle"></i>
      ${message}
      <button onclick="this.parentNode.remove()" 
              style="background: none; border: none; color: white; float: right; cursor: pointer; font-size: 18px;">
        ×
      </button>
    `;
    
    document.body.appendChild(warningDiv);
    
    // Auto-remove after 10 seconds
    setTimeout(() => {
      if (warningDiv.parentNode) {
        warningDiv.parentNode.removeChild(warningDiv);
      }
    }, 10000);
  }

  /**
   * Request permissions again
   */
  private async requestPermissionsAgain(): Promise<void> {
    try {
      await this.initializeWebRTC();
      window.location.reload();
    } catch (error) {
      console.error('Permission retry failed:', error);
    }
  }

  /**
   * Retry media access
   */
  private async retryMediaAccess(): Promise<void> {
    try {
      this.isLoading = true;
      await this.initializeWebRTC();
      this.isLoading = false;
    } catch (error) {
      console.error('Media access retry failed:', error);
      this.isLoading = false;
    }
  }

  /**
   * Continue with audio only
   */
  private async continueAudioOnly(): Promise<void> {
    try {
      this.isLoading = true;
      this.isCameraEnabled = false;
      
      // Get audio-only stream
      this.localStream = await navigator.mediaDevices.getUserMedia({ 
        video: false, 
        audio: true 
      });
      
      // Initialize WebRTC service with audio-only
      await this.webrtcService.initialize(this.sessionId, this.localStream);
      this.setupWebRTCEventHandlers();
      
      if (this.meetingLink) {
        await this.webrtcService.joinRoom(this.meetingLink);
      }
      
      this.isLoading = false;
      this.showMediaWarning('Interview started with audio only. Video is disabled.');
      
    } catch (error) {
      console.error('Audio-only fallback failed:', error);
      this.handleError('Unable to access microphone');
    }
  }

  /**
   * Get code editor title based on current language/job role
   */
  getCodeEditorTitle(): string {
    if (this.currentQuestion?.programming_language) {
      const lang = this.currentQuestion.programming_language;
      return `${lang.charAt(0).toUpperCase() + lang.slice(1)} Code Editor`;
    }
    
    // Determine language from job role
    const jobRole = this.session?.jobRole?.toLowerCase() || '';
    if (jobRole.includes('.net') || jobRole.includes('c#')) {
      return 'C# Code Editor';
    } else if (jobRole.includes('java')) {
      return 'Java Code Editor';
    } else if (jobRole.includes('python')) {
      return 'Python Code Editor';
    } else if (jobRole.includes('javascript') || jobRole.includes('react') || jobRole.includes('node')) {
      return 'JavaScript Code Editor';
    } else if (jobRole.includes('typescript') || jobRole.includes('angular')) {
      return 'TypeScript Code Editor';
    }
    
    return `${this.currentLanguage.charAt(0).toUpperCase() + this.currentLanguage.slice(1)} Code Editor`;
  }
  
  /**
   * Get code editor placeholder based on current language/job role
   */
  getCodePlaceholder(): string {
    if (this.currentQuestion?.programming_language) {
      const lang = this.currentQuestion.programming_language;
      return `Write your ${lang} code here...`;
    }
    
    // Determine language from job role
    const jobRole = this.session?.jobRole?.toLowerCase() || '';
    if (jobRole.includes('.net') || jobRole.includes('c#')) {
      return 'Write your C# code here...';
    } else if (jobRole.includes('java')) {
      return 'Write your Java code here...';
    } else if (jobRole.includes('python')) {
      return 'Write your Python code here...';
    } else if (jobRole.includes('javascript') || jobRole.includes('react') || jobRole.includes('node')) {
      return 'Write your JavaScript code here...';
    } else if (jobRole.includes('typescript') || jobRole.includes('angular')) {
      return 'Write your TypeScript code here...';
    }
    
    return `Write your ${this.currentLanguage} code here...`;
  }

  /**
   * Show permission help instructions
   */
  private showPermissionHelp(): void {
    const helpDiv = document.createElement('div');
    helpDiv.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,0.9);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10001;
    `;
    
    helpDiv.innerHTML = `
      <div style="background: white; border-radius: 12px; padding: 40px; max-width: 700px; max-height: 80vh; overflow-y: auto;">
        <h2 style="color: #2c3e50; margin-bottom: 30px; text-align: center;">🎥 How to Enable Camera & Microphone</h2>
        
        <div style="margin-bottom: 25px;">
          <h3 style="color: #34495e; margin-bottom: 15px;">📱 Chrome/Edge:</h3>
          <ol style="color: #7f8c8d; line-height: 1.6;">
            <li>Look for the camera/microphone icon in your address bar</li>
            <li>Click on it and select "Allow"</li>
            <li>Or go to Settings → Privacy → Camera/Microphone and allow this site</li>
          </ol>
        </div>
        
        <div style="margin-bottom: 25px;">
          <h3 style="color: #34495e; margin-bottom: 15px;">🦊 Firefox:</h3>
          <ol style="color: #7f8c8d; line-height: 1.6;">
            <li>Click the shield icon in the address bar</li>
            <li>Turn off "Blocked" for Camera and Microphone</li>
            <li>Refresh the page</li>
          </ol>
        </div>
        
        <div style="margin-bottom: 25px;">
          <h3 style="color: #34495e; margin-bottom: 15px;">🍎 Safari:</h3>
          <ol style="color: #7f8c8d; line-height: 1.6;">
            <li>Go to Safari → Settings → Websites → Camera/Microphone</li>
            <li>Set this website to "Allow"</li>
            <li>Refresh the page</li>
          </ol>
        </div>
        
        <div style="text-align: center; margin-top: 30px;">
          <button onclick="this.closest('div').remove()" 
                  style="background: #3498db; color: white; border: none; padding: 12px 24px; border-radius: 6px; cursor: pointer; font-weight: 600;">
            Got It
          </button>
        </div>
      </div>
    `;
    
    document.body.appendChild(helpDiv);
  }

  /**
   * Contact support action
   */
  private contactSupport(): void {
    // In a real implementation, this would open a support chat or redirect to help page
    window.open('mailto:support@ariaa.com?subject=Interview%20Technical%20Issue&body=I%20am%20having%20trouble%20with%20camera/microphone%20access%20during%20my%20interview.', '_blank');
  }

  // ==================== JITSI MEET INTEGRATION ====================

  /**
   * Embed Jitsi Meet directly in the component
   */
  private async embedJitsiMeet(): Promise<void> {
    try {
      console.log('🎯 Embedding Jitsi Meet in interview room:', this.meetingLink);
      
      // Wait for Jitsi External API to be loaded
      await this.loadJitsiExternalAPI();
      
      // Extract room name from URL
      const roomName = this.extractRoomNameFromUrl(this.meetingLink);
      const domain = this.extractDomainFromUrl(this.meetingLink);
      
      if (!this.jitsiContainer?.nativeElement) {
        console.error('❌ Jitsi container not found');
        return;
      }
      
      // Clear any existing content
      this.jitsiContainer.nativeElement.innerHTML = '';
      
      // Jitsi Meet configuration
      const options = {
        roomName: roomName,
        width: '100%',
        height: '100%',
        parentNode: this.jitsiContainer.nativeElement,
        configOverwrite: {
          startWithAudioMuted: !this.isAudioEnabled,
          startWithVideoMuted: !this.isVideoEnabled,
          prejoinPageEnabled: false,
          disableModeratorIndicator: false,
          startScreenSharing: false,
          enableEmailInStats: false,
          disableDeepLinking: true,
          disableInviteFunctions: true,
          enableWelcomePage: false,
          enableClosePage: false,
          toolbarButtons: [
            'microphone', 'camera', 'closedcaptions', 'desktop', 'fullscreen',
            'fodeviceselection', 'hangup', 'profile', 'chat', 'recording',
            'livestreaming', 'etherpad', 'sharedvideo', 'settings', 'raisehand',
            'videoquality', 'filmstrip', 'invite', 'feedback', 'stats', 'shortcuts',
            'tileview', 'videobackgroundblur', 'download', 'help', 'mute-everyone'
          ]
        },
        interfaceConfigOverwrite: {
          TOOLBAR_BUTTONS: [
            'microphone', 'camera', 'closedcaptions', 'desktop', 'fullscreen',
            'fodeviceselection', 'hangup', 'profile', 'chat',
            'settings', 'raisehand', 'videoquality', 'filmstrip'
          ],
          SETTINGS_SECTIONS: ['devices', 'language', 'moderator', 'profile'],
          SHOW_JITSI_WATERMARK: false,
          SHOW_WATERMARK_FOR_GUESTS: false,
          SHOW_BRAND_WATERMARK: false,
          BRAND_WATERMARK_LINK: '',
          SHOW_POWERED_BY: false,
          SHOW_PROMOTIONAL_CLOSE_PAGE: false,
          SHOW_CHROME_EXTENSION_BANNER: false,
          DISABLE_VIDEO_BACKGROUND: false,
          HIDE_INVITE_MORE_HEADER: true
        },
        userInfo: {
          displayName: this.candidateName || `Candidate_${this.sessionId}`
        }
      };

      // Create Jitsi Meet instance
      const JitsiMeetAPI = (window as any).JitsiMeetExternalAPI;
      const jitsiAPI = new JitsiMeetAPI(domain, options);
      
      // Set up event handlers
      this.setupJitsiEventHandlers(jitsiAPI);
      
      // Update connection state
      this.isConnected = true;
      this.logSystemMessage('system', 'Connected to Jitsi Meet video call', new Date());
      
      console.log('✅ Successfully embedded Jitsi Meet');
      
    } catch (error) {
      console.error('❌ Failed to embed Jitsi Meet:', error);
      this.handleJitsiEmbedError(error);
    }
  }

  /**
   * Load Jitsi External API dynamically
   */
  private async loadJitsiExternalAPI(): Promise<void> {
    return new Promise((resolve, reject) => {
      // Check if already loaded
      if ((window as any).JitsiMeetExternalAPI) {
        resolve();
        return;
      }
      
      const script = document.createElement('script');
      script.src = `https://${this.extractDomainFromUrl(this.meetingLink)}/external_api.js`;
      script.async = true;
      
      script.onload = () => {
        console.log('✅ Jitsi External API loaded');
        resolve();
      };
      
      script.onerror = () => {
        console.error('❌ Failed to load Jitsi External API');
        reject(new Error('Failed to load Jitsi External API'));
      };
      
      document.head.appendChild(script);
    });
  }

  /**
   * Set up Jitsi Meet event handlers
   */
  private setupJitsiEventHandlers(jitsiAPI: any): void {
    // Conference events
    jitsiAPI.addListener('readyToClose', () => {
      console.log('🔴 Jitsi Meet ready to close');
      this.isConnected = false;
      this.logSystemMessage('system', 'Disconnected from video call', new Date());
    });

    jitsiAPI.addListener('conferenceJoined', (event: any) => {
      console.log('✅ Joined Jitsi Meet conference:', event);
      this.isConnected = true;
      this.logSystemMessage('system', 'Successfully joined video call', new Date());
    });

    jitsiAPI.addListener('conferenceLeft', (event: any) => {
      console.log('👋 Left Jitsi Meet conference:', event);
      this.isConnected = false;
      this.logSystemMessage('system', 'Left video call', new Date());
    });

    // Participant events
    jitsiAPI.addListener('participantJoined', (event: any) => {
      console.log('👤 Participant joined:', event);
      this.logSystemMessage('system', `${event.displayName || 'Someone'} joined the call`, new Date());
    });

    jitsiAPI.addListener('participantLeft', (event: any) => {
      console.log('👋 Participant left:', event);
      this.logSystemMessage('system', `${event.displayName || 'Someone'} left the call`, new Date());
    });

    // Audio/Video events
    jitsiAPI.addListener('audioMuteStatusChanged', (event: any) => {
      console.log('🎤 Audio mute status changed:', event);
      this.isAudioEnabled = !event.muted;
    });

    jitsiAPI.addListener('videoMuteStatusChanged', (event: any) => {
      console.log('📹 Video mute status changed:', event);
      this.isVideoEnabled = !event.muted;
    });

    // Error handling
    jitsiAPI.addListener('log', (event: any) => {
      if (event.logLevel === 'ERROR') {
        console.error('❌ Jitsi Meet error:', event);
        this.logSystemMessage('system', 'Video call error occurred', new Date());
      }
    });
  }

  /**
   * Extract room name from Jitsi Meet URL
   */
  private extractRoomNameFromUrl(url: string): string {
    try {
      const urlObj = new URL(url);
      return urlObj.pathname.substring(1); // Remove leading slash
    } catch (error) {
      console.warn('Failed to extract room name from URL:', url);
      return `ARIA-Interview-${this.sessionId}`;
    }
  }

  /**
   * Extract domain from Jitsi Meet URL
   */
  private extractDomainFromUrl(url: string): string {
    try {
      const urlObj = new URL(url);
      return urlObj.hostname;
    } catch (error) {
      console.warn('Failed to extract domain from URL:', url);
      return 'meet.jit.si';
    }
  }

  /**
   * Handle Jitsi embed errors
   */
  private handleJitsiEmbedError(error: any): void {
    console.error('Jitsi Meet embed error:', error);
    
    // Show fallback message
    if (this.jitsiContainer?.nativeElement) {
      this.jitsiContainer.nativeElement.innerHTML = `
        <div style="
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          height: 100%;
          background: #f8f9fa;
          border: 2px dashed #dee2e6;
          border-radius: 8px;
          color: #6c757d;
          text-align: center;
          padding: 40px;
        ">
          <i class="fas fa-video-slash" style="font-size: 48px; margin-bottom: 16px;"></i>
          <h3 style="margin-bottom: 12px; color: #495057;">Unable to load video call</h3>
          <p style="margin-bottom: 20px;">The video call component failed to load.</p>
          <button 
            onclick="window.open('${this.meetingLink}', '_blank', 'width=1200,height=800')" 
            style="
              background: #007bff;
              color: white;
              border: none;
              padding: 12px 24px;
              border-radius: 6px;
              cursor: pointer;
              font-weight: 600;
            ">
            <i class="fas fa-external-link-alt"></i> Open in New Window
          </button>
        </div>
      `;
    }
    
    this.logSystemMessage('system', 'Video call failed to load. You can open it in a new window using the button above.', new Date());
  }

  /**
   * Join Jitsi Meet directly (temporary implementation)
   * This opens Jitsi Meet in a new window/tab
   */
  private joinJitsiMeetDirectly(): void {
    try {
      console.log('🚀 Opening Jitsi Meet in new window:', this.meetingLink);
      
      // Show user that they're being redirected to Jitsi Meet
      const notification = document.createElement('div');
      notification.style.cssText = `
        position: fixed;
        top: 20px;
        left: 50%;
        transform: translateX(-50%);
        background: #27ae60;
        color: white;
        padding: 15px 25px;
        border-radius: 8px;
        z-index: 10000;
        box-shadow: 0 4px 12px rgba(39, 174, 96, 0.3);
        font-weight: 600;
      `;
      
      notification.innerHTML = `
        <i class="fas fa-video"></i>
        Opening Jitsi Meet video call...
        <button onclick="this.parentNode.remove()" 
                style="background: none; border: none; color: white; float: right; cursor: pointer; font-size: 18px; margin-left: 15px;">
          ×
        </button>
      `;
      
      document.body.appendChild(notification);
      
      // Open Jitsi Meet in new window
      const jitsiWindow = window.open(
        this.meetingLink,
        'jitsi-interview',
        'width=1200,height=800,scrollbars=yes,resizable=yes,status=no,toolbar=no,menubar=no,location=no'
      );
      
      if (jitsiWindow) {
        // Update connection state
        this.isConnected = true;
        this.logSystemMessage('system', 'Joined Jitsi Meet video call', new Date());
        
        // Monitor if window is closed
        const checkClosed = setInterval(() => {
          if (jitsiWindow.closed) {
            clearInterval(checkClosed);
            this.isConnected = false;
            this.logSystemMessage('system', 'Left Jitsi Meet video call', new Date());
            console.log('📴 Jitsi Meet window closed');
          }
        }, 1000);
        
      } else {
        // Popup blocked or failed to open
        this.showJitsiPopupBlockedError();
      }
      
      // Remove notification after 5 seconds
      setTimeout(() => {
        if (notification.parentNode) {
          notification.parentNode.removeChild(notification);
        }
      }, 5000);
      
    } catch (error) {
      console.error('❌ Failed to open Jitsi Meet:', error);
      this.handleError('Failed to join video call. Please try again.');
    }
  }

  /**
   * Show error when Jitsi Meet popup is blocked
   */
  private showJitsiPopupBlockedError(): void {
    const errorDiv = document.createElement('div');
    errorDiv.style.cssText = `
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      background: #fff;
      border: 2px solid #e74c3c;
      border-radius: 12px;
      padding: 30px;
      max-width: 500px;
      text-align: center;
      z-index: 10000;
      box-shadow: 0 20px 60px rgba(0,0,0,0.3);
    `;
    
    errorDiv.innerHTML = `
      <div style="color: #e74c3c; margin-bottom: 20px;">
        <i class="fas fa-exclamation-triangle" style="font-size: 48px;"></i>
      </div>
      <h3 style="color: #2c3e50; margin-bottom: 15px;">Popup Blocked</h3>
      <p style="color: #7f8c8d; margin-bottom: 25px; line-height: 1.5;">
        Your browser blocked the video call popup. Please allow popups for this site and try again.
      </p>
      <div>
        <button onclick="window.open('${this.meetingLink}', '_blank'); this.closest('div').remove();" 
                style="background: #27ae60; color: white; border: none; padding: 12px 24px; border-radius: 6px; cursor: pointer; font-weight: 600; margin-right: 10px;">
          Open Video Call
        </button>
        <button onclick="this.closest('div').remove()" 
                style="background: #95a5a6; color: white; border: none; padding: 12px 24px; border-radius: 6px; cursor: pointer; font-weight: 600;">
          Cancel
        </button>
      </div>
    `;
    
    document.body.appendChild(errorDiv);
  }
  
  // ==================== UI STABILITY HELPER METHODS ====================
  
  /**
   * Update UI for processing state with visual feedback
   */
  private updateUIForProcessing(): void {
    this.currentTask = '🔄 Processing your response...';
    
    // Add visual loading indicator
    this.logSystemMessage('system', 'Processing response...', new Date());
    
    // Disable End Response button temporarily
    const endButton = document.querySelector('.end-response') as HTMLButtonElement;
    if (endButton) {
      endButton.disabled = true;
      endButton.style.opacity = '0.6';
      endButton.style.cursor = 'not-allowed';
      endButton.textContent = 'Processing...';
    }
  }
  
  /**
   * Create timeout promise for request timeout handling
   */
  private createTimeoutPromise(timeoutMs: number): Promise<never> {
    return new Promise((_, reject) => {
      setTimeout(() => reject(new Error('Request timeout')), timeoutMs);
    });
  }
  
  /**
   * Process response result and update UI accordingly
   */
  private async processResponseResult(result: any): Promise<void> {
    try {
      if (result.shouldEnd) {
        this.currentTask = '🎉 Interview completed! Thank you for your time.';
        this.logSystemMessage('system', 'Interview completed successfully', new Date());
        
        // Wait a moment before ending to let user see the message
        setTimeout(() => this.endInterview(), 3000);
        
      } else if (result.nextQuestion) {
        // Update to next question
        this.currentQuestion = result.nextQuestion;
        this.interviewerQuestion = result.nextQuestion.text;
        this.currentTask = result.nextQuestion.text;
        
        // Log the question
        this.logSystemMessage('interviewer', this.interviewerQuestion, new Date());
        
        // Reset UI state for next question
        this.resetUIForNextQuestion(result.nextQuestion);
        
        console.log('✅ Next question loaded:', result.nextQuestion.id);
        
      } else {
        // No clear direction from backend
        this.currentTask = 'Waiting for next instruction...';
        console.warn('⚠️ Unclear response from backend:', result);
      }
      
    } catch (error) {
      console.error('❌ Error processing response result:', error);
      this.handleSubmissionError(error);
    } finally {
      // Re-enable the End Response button
      this.resetEndResponseButton();
    }
  }
  
  /**
   * Reset UI state for next question
   */
  private resetUIForNextQuestion(nextQuestion: any): void {
    // Clear previous response data
    this.candidateResponse = '';
    this.liveTranscript = '';
    
    // Update code editor if needed
    if (nextQuestion.coding_required) {
      this.currentLanguage = nextQuestion.programming_language || 'javascript';
      this.codeContent = this.generateCodeTemplate(nextQuestion);
    } else {
      // Reset to default for non-coding questions
      this.codeContent = '// No coding required for this question';
    }
    
    // Reset AI state
    this.updateAIStatus('listening', 'Ready for your response');
  }
  
  /**
   * Generate appropriate code template based on question and language
   */
  private generateCodeTemplate(question: any): string {
    const lang = question.programming_language || 'javascript';
    const questionText = question.text || 'Write your solution';
    
    // Create language-specific templates
    switch (lang.toLowerCase()) {
      case 'javascript':
      case 'typescript':
        return `// ${questionText}\n\nfunction solution() {\n    // Write your ${lang} code here...\n    \n}\n\n// Test your solution\nconsole.log(solution());`;
        
      case 'python':
        return `# ${questionText}\n\ndef solution():\n    # Write your Python code here...\n    pass\n\n# Test your solution\nprint(solution())`;
        
      case 'java':
        return `// ${questionText}\n\npublic class Solution {\n    public static void main(String[] args) {\n        // Write your Java code here...\n        \n    }\n}`;
        
      case 'csharp':
      case 'c#':
        return `// ${questionText}\n\nusing System;\n\npublic class Solution\n{\n    public static void Main(string[] args)\n    {\n        // Write your C# code here...\n        \n    }\n}`;
        
      case 'cpp':
      case 'c++':
        return `// ${questionText}\n\n#include <iostream>\nusing namespace std;\n\nint main() {\n    // Write your C++ code here...\n    \n    return 0;\n}`;
        
      default:
        return `// ${questionText}\n\n// Write your ${lang} code here...`;
    }
  }
  
  /**
   * Reset End Response button to normal state
   */
  private resetEndResponseButton(): void {
    const endButton = document.querySelector('.end-response') as HTMLButtonElement;
    if (endButton) {
      endButton.disabled = false;
      endButton.style.opacity = '1';
      endButton.style.cursor = 'pointer';
      endButton.textContent = 'End Response';
    }
  }
  
  /**
   * Handle submission errors with user feedback
   */
  private handleSubmissionError(error: any): void {
    console.error('❌ Submission error:', error);
    
    let errorMessage = 'Failed to submit response. Please try again.';
    
    if (error.message?.includes('timeout')) {
      errorMessage = 'Request timed out. Please check your connection and try again.';
    } else if (error.message?.includes('Network')) {
      errorMessage = 'Network error. Please check your internet connection.';
    } else if (error.status === 404) {
      errorMessage = 'Session not found. Please refresh and try again.';
    } else if (error.status === 500) {
      errorMessage = 'Server error. Please try again in a moment.';
    }
    
    this.currentTask = `❌ ${errorMessage}`;
    
    // Show user-friendly error notification
    this.showErrorNotification(errorMessage);
    
    // Re-enable the button for retry
    this.resetEndResponseButton();
  }
  
  /**
   * Show cooldown message to prevent spam clicking
   */
  private showCooldownMessage(remainingMs: number): void {
    const cooldownDiv = document.createElement('div');
    cooldownDiv.style.cssText = `
      position: fixed;
      top: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: #ff9800;
      color: white;
      padding: 12px 20px;
      border-radius: 6px;
      z-index: 9999;
      box-shadow: 0 4px 12px rgba(255, 152, 0, 0.3);
      font-weight: 600;
    `;
    
    cooldownDiv.innerHTML = `
      <i class="fas fa-clock"></i>
      Please wait ${Math.ceil(remainingMs / 1000)} seconds before submitting again
    `;
    
    document.body.appendChild(cooldownDiv);
    
    // Auto-remove after cooldown period
    setTimeout(() => {
      if (cooldownDiv.parentNode) {
        cooldownDiv.parentNode.removeChild(cooldownDiv);
      }
    }, remainingMs + 500);
  }
  
  /**
   * Show validation error for missing data
   */
  private showValidationError(message: string): void {
    const errorDiv = document.createElement('div');
    errorDiv.style.cssText = `
      position: fixed;
      top: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: #e74c3c;
      color: white;
      padding: 15px 25px;
      border-radius: 6px;
      z-index: 9999;
      box-shadow: 0 4px 12px rgba(231, 76, 60, 0.3);
      font-weight: 600;
      max-width: 400px;
      text-align: center;
    `;
    
    errorDiv.innerHTML = `
      <i class="fas fa-exclamation-triangle"></i>
      ${message}
      <button onclick="this.parentNode.remove()" 
              style="background: none; border: none; color: white; float: right; cursor: pointer; font-size: 18px; margin-left: 15px;">
        ×
      </button>
    `;
    
    document.body.appendChild(errorDiv);
    
    // Auto-remove after 5 seconds
    setTimeout(() => {
      if (errorDiv.parentNode) {
        errorDiv.parentNode.removeChild(errorDiv);
      }
    }, 5000);
  }
  
  /**
   * Show error notification with retry option
   */
  private showErrorNotification(message: string): void {
    const errorDiv = document.createElement('div');
    errorDiv.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: #e74c3c;
      color: white;
      padding: 16px 20px;
      border-radius: 8px;
      z-index: 9999;
      box-shadow: 0 6px 20px rgba(231, 76, 60, 0.3);
      font-weight: 600;
      max-width: 400px;
      border-left: 4px solid #c0392b;
    `;
    
    errorDiv.innerHTML = `
      <div style="display: flex; align-items: center; gap: 10px;">
        <i class="fas fa-exclamation-circle"></i>
        <span>${message}</span>
        <button onclick="this.parentNode.parentNode.remove()" 
                style="background: none; border: none; color: white; cursor: pointer; font-size: 18px; margin-left: auto;">
          ×
        </button>
      </div>
      <div style="margin-top: 12px; text-align: center;">
        <button onclick="location.reload()" 
                style="background: #c0392b; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; font-size: 12px;">
          Retry
        </button>
      </div>
    `;
    
    document.body.appendChild(errorDiv);
    
    // Auto-remove after 10 seconds
    setTimeout(() => {
      if (errorDiv.parentNode) {
        errorDiv.parentNode.removeChild(errorDiv);
      }
    }, 10000);
  }
  
  /**
   * Check if response submission is allowed (debouncing and validation)
   */
  public canSubmitResponse(): boolean {
    const now = Date.now();
    const hasQuestion = !!this.currentQuestion;
    const notProcessing = !this.isSubmittingResponse && !this.isProcessingAction;
    const cooldownPassed = (now - this.lastSubmissionTime) >= this.submissionCooldown;
    const hasSession = !!this.sessionId;
    
    return hasQuestion && notProcessing && cooldownPassed && hasSession;
  }
  
  /**
   * Get response button display text based on current state
   */
  public getResponseButtonText(): string {
    if (this.isSubmittingResponse) {
      return 'Submitting...';
    }
    
    if (this.isProcessingAction) {
      return 'Processing...';
    }
    
    const now = Date.now();
    const remainingCooldown = this.submissionCooldown - (now - this.lastSubmissionTime);
    
    if (remainingCooldown > 0) {
      return `Wait ${Math.ceil(remainingCooldown / 1000)}s`;
    }
    
    return 'End Response';
  }
  
  /**
   * Handle keyboard events safely (prevent unwanted spacebar behavior)
   */
  public onKeyboardEvent(event: KeyboardEvent): void {
    // Only handle keyboard events if we're in the interview room and focused on appropriate elements
    const target = event.target as HTMLElement;
    const isTextInput = target.tagName === 'TEXTAREA' || target.tagName === 'INPUT' || target.contentEditable === 'true';
    
    // Prevent spacebar from triggering button clicks when typing
    if (event.code === 'Space' && isTextInput) {
      // Allow normal spacebar behavior in text inputs
      return;
    }
    
    // Handle other keyboard shortcuts
    if (event.ctrlKey || event.metaKey) {
      switch (event.key) {
        case 'Enter':
          // Ctrl/Cmd + Enter to submit response
          event.preventDefault();
          if (this.canSubmitResponse()) {
            this.endResponse();
          }
          break;
          
        case 'm':
          // Ctrl/Cmd + M to toggle microphone
          event.preventDefault();
          this.toggleMicrophone();
          break;
          
        case 'v':
          // Ctrl/Cmd + V to toggle video (if not pasting)
          if (!isTextInput) {
            event.preventDefault();
            this.toggleCamera();
          }
          break;
      }
    }
  }
  
  /**
   * Safely handle transcript updates with rate limiting
   */
  public handleTranscriptUpdate(update: any): void {
    // Validate update data
    if (!update || typeof update !== 'object') {
      console.warn('Invalid transcript update:', update);
      return;
    }
    
    // Ensure required fields exist
    const speaker = update.speaker || 'unknown';
    const text = update.text || '';
    const timestamp = update.timestamp ? new Date(update.timestamp) : new Date();
    
    // Skip empty updates
    if (!text.trim()) {
      return;
    }
    
    // Update live transcript for candidate or current speaker
    if (speaker === 'candidate' || !this.liveTranscript) {
      this.liveTranscript = text;
    }
    
    // Log the transcript update
    this.logSystemMessage(speaker, text, timestamp);
    
  }

  // ==================== SPEECH RECOGNITION FOR CANDIDATE VOICE ====================

  /**
   * Initialize speech recognition for candidate voice input
   */
  private initializeSpeechRecognition(): void {
    // Only initialize in browser environment
    if (!isPlatformBrowser(this.platformId)) {
      console.log('🔇 Speech recognition not available in server-side environment');
      return;
    }

    try {
      // Check for Web Speech API support
      const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
      
      if (!SpeechRecognition) {
        console.warn('🔇 Speech recognition not supported in this browser');
        return;
      }

      console.log('🎤 Initializing speech recognition for candidate voice...');

      // Create speech recognition instance
      this.speechRecognition = new SpeechRecognition();
      
      // Configure speech recognition settings
      this.speechRecognition.continuous = true; // Keep listening
      this.speechRecognition.interimResults = true; // Get partial results
      this.speechRecognition.lang = 'en-US'; // Set language
      this.speechRecognition.maxAlternatives = 1; // Only get best match
      
      // Set up event handlers
      this.setupSpeechRecognitionEventHandlers();
      
      console.log('✅ Speech recognition initialized successfully');
      
    } catch (error) {
      console.error('❌ Failed to initialize speech recognition:', error);
    }
  }

  /**
   * Set up speech recognition event handlers
   */
  private setupSpeechRecognitionEventHandlers(): void {
    if (!this.speechRecognition) {
      return;
    }

    // Speech recognition start event
    this.speechRecognition.onstart = () => {
      console.log('🎤 Speech recognition started');
      this.isSpeechRecognitionActive = true;
      this.isListeningToCandidate = true;
    };

    // Speech recognition result event
    this.speechRecognition.onresult = (event: any) => {
      let interimTranscript = '';
      let finalTranscript = '';

      // Process all results
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript;
        
        if (event.results[i].isFinal) {
          finalTranscript += transcript;
        } else {
          interimTranscript += transcript;
        }
      }

      // Update transcripts
      this.interimTranscript = interimTranscript;
      this.finalTranscript = finalTranscript;

      // Update live transcript with combined text
      const combinedTranscript = (this.finalTranscript + this.interimTranscript).trim();
      if (combinedTranscript) {
        this.liveTranscript = combinedTranscript;
        this.candidateResponse = combinedTranscript;
        
        // Update conversation history with cleaned candidate speech
        this.updateCandidateConversationMessage(combinedTranscript, !interimTranscript);
        
        // Send to transcript service
        this.sendCandidateTranscriptUpdate(combinedTranscript, !interimTranscript);
        
        console.log('🗣️ Candidate speech:', combinedTranscript.substring(0, 50) + '...');
      }
    };

    // Speech recognition end event
    this.speechRecognition.onend = () => {
      console.log('🔇 Speech recognition ended');
      this.isSpeechRecognitionActive = false;
      this.isListeningToCandidate = false;
      
      // Restart speech recognition if interview is active
      if (this.isInterviewStarted && this.isRecording) {
        setTimeout(() => this.restartSpeechRecognition(), 500);
      }
    };

    // Speech recognition error event
    this.speechRecognition.onerror = (event: any) => {
      console.error('❌ Speech recognition error:', event.error);
      
      // Handle specific errors
      switch (event.error) {
        case 'no-speech':
          console.log('🤫 No speech detected, continuing to listen...');
          break;
        case 'audio-capture':
          console.error('🎤 Audio capture failed - microphone may not be available');
          this.showSpeechRecognitionError('Microphone access failed. Please check your microphone permissions.');
          break;
        case 'not-allowed':
          console.error('🚫 Speech recognition permission denied');
          this.showSpeechRecognitionError('Microphone permission denied. Please allow microphone access for voice recognition.');
          break;
        case 'network':
          console.error('🌐 Network error during speech recognition');
          break;
        default:
          console.error('❓ Unknown speech recognition error:', event.error);
      }
      
      this.isSpeechRecognitionActive = false;
      this.isListeningToCandidate = false;
    };

    // Speech recognition no match event
    this.speechRecognition.onnomatch = () => {
      console.log('❓ Speech recognition - no match found');
    };

    // Speech recognition sound start event
    this.speechRecognition.onsoundstart = () => {
      console.log('🔊 Speech recognition detected sound');
    };

    // Speech recognition sound end event
    this.speechRecognition.onsoundend = () => {
      console.log('🔇 Speech recognition sound ended');
    };

    // Speech recognition speech start event
    this.speechRecognition.onspeechstart = () => {
      console.log('🗣️ Speech recognition detected speech');
      this.isListeningToCandidate = true;
    };

    // Speech recognition speech end event
    this.speechRecognition.onspeechend = () => {
      console.log('🤐 Speech recognition speech ended');
      this.isListeningToCandidate = false;
    };
  }

  /**
   * Start speech recognition for candidate voice
   */
  public startSpeechRecognition(): void {
    if (!this.speechRecognition) {
      console.warn('🔇 Speech recognition not initialized');
      return;
    }

    if (this.isSpeechRecognitionActive) {
      console.log('🎤 Speech recognition already active');
      return;
    }

    try {
      console.log('🎤 Starting speech recognition for candidate voice...');
      this.speechRecognition.start();
      
      // Set timeout to restart if needed
      this.speechRecognitionTimeout = setTimeout(() => {
        if (!this.isSpeechRecognitionActive && this.isInterviewStarted) {
          console.log('🔄 Restarting speech recognition due to timeout');
          this.restartSpeechRecognition();
        }
      }, 10000); // 10 seconds timeout
      
    } catch (error) {
      console.error('❌ Failed to start speech recognition:', error);
      this.showSpeechRecognitionError('Failed to start voice recognition. Please try again.');
    }
  }

  /**
   * Stop speech recognition for candidate voice
   */
  public stopSpeechRecognition(): void {
    if (!this.speechRecognition) {
      return;
    }

    try {
      console.log('🛑 Stopping speech recognition...');
      
      // Clear timeout
      if (this.speechRecognitionTimeout) {
        clearTimeout(this.speechRecognitionTimeout);
        this.speechRecognitionTimeout = null;
      }
      
      // Stop recognition
      this.speechRecognition.stop();
      this.isSpeechRecognitionActive = false;
      this.isListeningToCandidate = false;
      
    } catch (error) {
      console.error('❌ Error stopping speech recognition:', error);
    }
  }

  /**
   * Restart speech recognition
   */
  private restartSpeechRecognition(): void {
    console.log('🔄 Restarting speech recognition...');
    
    // Stop current recognition
    this.stopSpeechRecognition();
    
    // Wait a bit before restarting
    setTimeout(() => {
      if (this.isInterviewStarted && this.isRecording) {
        this.startSpeechRecognition();
      }
    }, 1000);
  }

  /**
   * Send candidate transcript update to transcript service
   */
  private sendCandidateTranscriptUpdate(transcript: string, isFinal: boolean): void {
    try {
      // Create transcript update object
      const transcriptUpdate = {
        speaker: 'candidate',
        text: transcript,
        isFinal: isFinal,
        timestamp: new Date().toISOString(),
        sessionId: this.sessionId
      };

      // Send to transcript service via WebSocket
      if (this.speechWS && this.speechWS.readyState === WebSocket.OPEN) {
        this.speechWS.send(JSON.stringify({
          type: 'candidate_transcript',
          data: transcriptUpdate
        }));
      }

      // Also send via regular transcript service using chat message method
      this.transcriptService.sendChatMessage(this.sessionId, transcript);
      
      console.log('📤 Sent candidate transcript update:', {
        text: transcript.substring(0, 50) + '...',
        isFinal: isFinal
      });
      
    } catch (error) {
      console.error('❌ Failed to send candidate transcript update:', error);
    }
  }

  /**
   * Show speech recognition error message
   */
  private showSpeechRecognitionError(message: string): void {
    const errorDiv = document.createElement('div');
    errorDiv.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: #e74c3c;
      color: white;
      padding: 15px 20px;
      border-radius: 6px;
      z-index: 9999;
      box-shadow: 0 4px 12px rgba(231, 76, 60, 0.3);
      font-weight: 600;
      max-width: 400px;
    `;
    
    errorDiv.innerHTML = `
      <i class="fas fa-microphone-slash"></i>
      ${message}
      <button onclick="this.parentNode.remove()" 
              style="background: none; border: none; color: white; float: right; cursor: pointer; font-size: 18px; margin-left: 15px;">
        ×
      </button>
    `;
    
    document.body.appendChild(errorDiv);
    
    // Auto-remove after 8 seconds
    setTimeout(() => {
      if (errorDiv.parentNode) {
        errorDiv.parentNode.removeChild(errorDiv);
      }
    }, 8000);
  }

  /**
   * Toggle speech recognition on/off
   */
  public toggleSpeechRecognition(): void {
    if (this.isSpeechRecognitionActive) {
      this.stopSpeechRecognition();
      console.log('🔇 Speech recognition stopped by user');
    } else {
      this.startSpeechRecognition();
      console.log('🎤 Speech recognition started by user');
    }
  }

  /**
   * Get speech recognition status for UI display
   */
  public getSpeechRecognitionStatus(): string {
    if (!this.speechRecognition) {
      return 'Not Available';
    }
    
    if (this.isSpeechRecognitionActive) {
      return this.isListeningToCandidate ? 'Listening...' : 'Active';
    }
    
    return 'Inactive';
  }

  /**
   * Check if speech recognition is supported
   */
  public isSpeechRecognitionSupported(): boolean {
    if (!isPlatformBrowser(this.platformId)) {
      return false;
    }
    
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    return !!SpeechRecognition;
  }

  // ==================== STRUCTURED INTERVIEW FLOW INTEGRATION ====================

  /**
   * Initialize structured interview flow with AI Avatar service
   */
  private async initializeStructuredInterview(): Promise<void> {
    try {
      console.log('🎯 Initializing structured interview flow...');
      
      this.structuredInterviewActive = true;
      this.updateAIStatus('initializing', 'Starting structured interview flow');
      
      // Send initialization request to AI Avatar service
      const initializationMessage = {
        type: 'initialize_structured_interview',
        data: {
          sessionId: this.sessionId,
          candidateName: this.candidateName,
          position: this.session?.jobRole || 'Software Engineer',
          experience: (this.session as any)?.experienceLevel || 'intermediate',
          skills: (this.session as any)?.requiredTechnologies || [],
          timestamp: new Date().toISOString()
        }
      };
      
      // Send via orchestrator WebSocket if available
      if (this.orchestratorWS && this.orchestratorWS.readyState === WebSocket.OPEN) {
        this.orchestratorWS.send(JSON.stringify(initializationMessage));
        console.log('📤 Sent structured interview initialization to orchestrator');
        
        // Set waiting state - AI will respond when ready
        this.currentInterviewStage = 'waiting_for_ai';
        this.currentTask = 'Waiting for AI interviewer to begin...';
        this.updateAIStatus('initializing', 'AI interviewer is joining the session');
        
      } else {
        // Wait for orchestrator connection and retry
        console.log('⏳ Orchestrator not ready, waiting for connection...');
        this.currentTask = 'Connecting to AI interviewer...';
        this.updateAIStatus('initializing', 'Connecting to AI services');
        
        // Retry when orchestrator connects
        setTimeout(() => this.initializeStructuredInterviewRetry(initializationMessage), 2000);
      }
      
      // Monitor stage progression
      this.monitorInterviewStages();
      
      console.log('✅ Structured interview initialization sent, waiting for AI response');
      
    } catch (error) {
      console.error('❌ Failed to initialize structured interview:', error);
      this.handleStructuredInterviewError('Failed to initialize structured interview flow');
    }
  }
  
  /**
   * Retry structured interview initialization
   */
  private initializeStructuredInterviewRetry(initializationMessage: any): void {
    if (this.orchestratorWS && this.orchestratorWS.readyState === WebSocket.OPEN) {
      this.orchestratorWS.send(JSON.stringify(initializationMessage));
      console.log('📤 Retried structured interview initialization');
      
      // Update status now that we're connected
      this.currentTask = 'Waiting for AI interviewer to begin...';
      this.updateAIStatus('initializing', 'AI interviewer is joining the session');
      
    } else {
      console.warn('⚠️ Orchestrator still not ready, falling back to regular interview flow');
      this.structuredInterviewActive = false;
      this.useStructuredFlow = false;
      this.loadCurrentQuestion();
    }
  }
  
  /**
   * Monitor interview stage progression
   */
  private monitorInterviewStages(): void {
    // Listen for stage updates from AI Avatar service
    const stageMonitoringInterval = setInterval(() => {
      if (!this.structuredInterviewActive) {
        clearInterval(stageMonitoringInterval);
        return;
      }
      
      // Request stage update from orchestrator
      const stageUpdateRequest = {
        type: 'get_stage_status',
        data: {
          sessionId: this.sessionId,
          timestamp: new Date().toISOString()
        }
      };
      
      if (this.orchestratorWS && this.orchestratorWS.readyState === WebSocket.OPEN) {
        this.orchestratorWS.send(JSON.stringify(stageUpdateRequest));
      }
    }, 10000); // Check every 10 seconds
    
    // Clear interval on component destruction
    this.subscriptions.push({
      unsubscribe: () => clearInterval(stageMonitoringInterval)
    } as any);
  }
  
  /**
   * Handle structured interview stage updates
   */
  private handleStructuredStageUpdate(stageData: any): void {
    console.log('📊 Structured interview stage update:', stageData);
    
    if (stageData.stage && stageData.stage !== this.currentInterviewStage) {
      this.currentInterviewStage = stageData.stage;
      console.log(`🔄 Interview stage changed to: ${this.currentInterviewStage}`);
      
      // Update UI based on stage
      this.updateUIForStage(this.currentInterviewStage);
    }
    
    if (stageData.progress) {
      this.stageProgress = stageData.progress;
      console.log('Progress:', this.stageProgress);
    }
    
    if (stageData.currentAction) {
      this.currentTask = stageData.currentAction;
    }
    
    // Handle stage-specific data
    if (stageData.question) {
      this.handleStructuredQuestion(stageData.question);
    }
    
    if (stageData.codingChallenge) {
      this.handleStructuredCodingChallenge(stageData.codingChallenge);
    }
    
    if (stageData.completed && stageData.nextStage) {
      this.transitionToNextStage(stageData.nextStage);
    }
  }
  
  /**
   * Update UI based on current interview stage
   */
  private updateUIForStage(stage: string): void {
    switch (stage) {
      case 'introduction':
        this.currentTask = 'Introduction phase - Please introduce yourself';
        this.updateAIStatus('listening', 'Ready to hear your introduction');
        break;
        
      case 'technical_warmup':
        this.currentTask = 'Technical warm-up questions';
        this.updateAIStatus('thinking', 'Preparing technical questions');
        break;
        
      case 'coding_challenge':
        this.currentTask = 'Coding challenge - Please solve the programming problem';
        this.updateAIStatus('listening', 'Ready for coding challenge');
        break;
        
      case 'behavioral_questions':
        this.currentTask = 'Behavioral questions - Tell us about your experiences';
        this.updateAIStatus('listening', 'Ready for behavioral questions');
        break;
        
      case 'advanced_technical':
        this.currentTask = 'Advanced technical discussion';
        this.updateAIStatus('thinking', 'Preparing advanced questions');
        break;
        
      case 'conclusion':
        this.currentTask = 'Interview conclusion - Any final questions?';
        this.updateAIStatus('speaking', 'Wrapping up the interview');
        break;
        
      default:
        this.currentTask = `${stage.replace('_', ' ').toUpperCase()} phase`;
        this.updateAIStatus('ready', 'Processing interview stage');
    }
    
    // Log stage transition
    this.logSystemMessage('system', `Moving to ${stage.replace('_', ' ')} phase`, new Date());
  }
  
  /**
   * Handle structured question from AI Avatar
   */
  private handleStructuredQuestion(questionData: any): void {
    console.log('❓ Structured question received:', questionData);
    
    // Extract question text - backend sends it as 'question', not 'text'
    const questionText = questionData.question || questionData.text || 'Question not available';
    
    // Update current question
    this.currentQuestion = {
      id: questionData.id || `structured_${Date.now()}`,
      text: questionText,
      type: questionData.coding_required ? 'coding' : (questionData.type || 'technical'),
      coding_required: questionData.coding_required || false,
      programming_language: questionData.programming_language,
      difficulty: questionData.difficulty || 'Medium',
      estimatedSeconds: questionData.timeLimit ? questionData.timeLimit : (questionData.expectedTime ? questionData.expectedTime * 60 : 300),
      category: questionData.category || 'general'
    };
    
    this.interviewerQuestion = questionText;
    this.currentTask = questionText;
    
    // Update code editor if coding question
    if (questionData.coding_required) {
      this.currentLanguage = questionData.programming_language || 'javascript';
      this.codeContent = this.generateCodeTemplate(questionData);
    }
    
    // Log the question
    this.logSystemMessage('interviewer', questionText, new Date());
    
    // Update AI status
    this.updateAIStatus('listening', 'Waiting for your response');
  }
  
  /**
   * Handle structured coding challenge
   */
  private handleStructuredCodingChallenge(challengeData: any): void {
    console.log('💻 Structured coding challenge received:', challengeData);
    
    // Create coding question
    this.currentQuestion = {
      id: challengeData.id || `coding_${Date.now()}`,
      text: challengeData.problem,
      type: 'coding',
      coding_required: true,
      programming_language: challengeData.language || 'javascript',
      difficulty: challengeData.difficulty || 'Medium',
      estimatedSeconds: challengeData.timeLimit ? challengeData.timeLimit * 60 : 1800,
      category: 'coding',
      constraints: challengeData.constraints,
      examples: challengeData.examples,
      hints: challengeData.hints
    };
    
    this.interviewerQuestion = challengeData.problem;
    this.currentTask = challengeData.problem;
    this.currentLanguage = challengeData.language || 'javascript';
    
    // Set up code editor with starter code
    this.codeContent = challengeData.starterCode || this.generateCodeTemplate(challengeData);
    
    // Add to conversation with additional context
    let conversationText = challengeData.problem;
    if (challengeData.examples) {
      conversationText += '\n\nExamples:\n' + challengeData.examples;
    }
    if (challengeData.constraints) {
      conversationText += '\n\nConstraints:\n' + challengeData.constraints;
    }
    
    this.logSystemMessage('interviewer', conversationText, new Date());
    this.updateAIStatus('listening', 'Ready to review your coding solution');
  }
  
  /**
   * Transition to next interview stage
   */
  private transitionToNextStage(nextStage: string): void {
    console.log(`🔄 Transitioning from ${this.currentInterviewStage} to ${nextStage}`);
    
    // Clear previous question/task data
    this.candidateResponse = '';
    this.liveTranscript = '';
    
    // Update stage
    this.currentInterviewStage = nextStage;
    
    // Update UI for new stage
    this.updateUIForStage(nextStage);
    
    // Notify orchestrator of stage transition acknowledgment
    const transitionMessage = {
      type: 'stage_transition_ack',
      data: {
        sessionId: this.sessionId,
        fromStage: this.currentInterviewStage,
        toStage: nextStage,
        timestamp: new Date().toISOString()
      }
    };
    
    if (this.orchestratorWS && this.orchestratorWS.readyState === WebSocket.OPEN) {
      this.orchestratorWS.send(JSON.stringify(transitionMessage));
    }
  }
  
  /**
   * Handle structured interview errors
   */
  private handleStructuredInterviewError(errorMessage: string): void {
    console.error('❌ Structured interview error:', errorMessage);
    
    this.structuredInterviewActive = false;
    this.updateAIStatus('ready', 'Falling back to standard interview mode');
    
    // Show error notification
    this.showErrorNotification(`Structured interview failed: ${errorMessage}. Continuing with standard interview.`);
    
    // Fallback to regular question loading
    this.loadCurrentQuestion();
  }
  
  /**
   * Submit response in structured interview context
   */
  async submitStructuredResponse(): Promise<void> {
    if (!this.structuredInterviewActive) {
      // Use regular response submission
      return this.endResponseOriginal();
    }
    
    try {
      // Set processing flags
      this.isSubmittingResponse = true;
      this.isProcessingAction = true;
      this.lastSubmissionTime = Date.now();
      
      // Show processing state with visual feedback
      this.updateUIForProcessing();
      
      // Enhanced response submission for structured interview
      const structuredResponseData = {
        sessionId: this.sessionId,
        stage: this.currentInterviewStage,
        questionId: this.currentQuestion?.id,
        transcriptText: this.candidateResponse || this.liveTranscript,
        codeSubmission: this.currentQuestion?.coding_required ? this.codeContent : null,
        responseStartTime: new Date(),
        responseEndTime: new Date(),
        stageProgress: this.stageProgress,
        interactionData: {
          aiStatus: this.aiAvatarState.status,
          confidence: this.aiAvatarState.confidence,
          duration: this.timerSeconds
        }
      };
      
      console.log('📤 Submitting structured response:', {
        stage: structuredResponseData.stage,
        questionId: structuredResponseData.questionId,
        hasTranscript: !!structuredResponseData.transcriptText,
        hasCode: !!structuredResponseData.codeSubmission
      });
      
      // Clear current transcript to show response was submitted
      this.liveTranscript = '';
      this.candidateResponse = '';
      
      // Send to orchestrator for processing
      const responseMessage = {
        type: 'response_submission',
        data: structuredResponseData
      };
      
      if (this.orchestratorWS && this.orchestratorWS.readyState === WebSocket.OPEN) {
        this.orchestratorWS.send(JSON.stringify(responseMessage));
        this.updateAIStatus('analyzing', 'AI is analyzing your response...');
        this.currentTask = 'AI is analyzing your response and preparing the next question...';
        
        console.log('✅ Response sent to AI orchestrator for analysis');
      } else {
        console.warn('⚠️ Orchestrator not connected, falling back to regular submission');
        // Fallback to regular submission
        await this.submitRegularResponse(structuredResponseData);
      }
      
      // Also submit via regular interview service as backup for persistence
      if (this.currentQuestion) {
        const regularResponseData = {
          questionId: this.currentQuestion.id,
          transcriptText: structuredResponseData.transcriptText,
          codeSubmission: structuredResponseData.codeSubmission || undefined,
          responseStartTime: structuredResponseData.responseStartTime,
          responseEndTime: structuredResponseData.responseEndTime,
          spacebarPressed: true,
          sessionId: this.sessionId
        };
        
        // Submit in background for persistence without waiting
        this.interviewService.submitResponse(this.sessionId, regularResponseData).catch(error => {
          console.warn('Background response submission failed:', error);
        });
      }
      
    } catch (error) {
      console.error('❌ Structured response submission failed:', error);
      this.handleSubmissionError(error);
    } finally {
      // Reset processing flags
      this.isSubmittingResponse = false;
      this.isProcessingAction = false;
      this.resetEndResponseButton();
    }
  }
  
  /**
   * Submit response using regular interview service (fallback)
   */
  private async submitRegularResponse(responseData: any): Promise<void> {
    if (!this.currentQuestion) {
      throw new Error('No current question available');
    }
    
    const regularResponseData = {
      questionId: this.currentQuestion.id,
      transcriptText: responseData.transcriptText,
      codeSubmission: responseData.codeSubmission || undefined,
      responseStartTime: responseData.responseStartTime,
      responseEndTime: responseData.responseEndTime,
      spacebarPressed: true,
      sessionId: this.sessionId
    };
    
    const result = await this.interviewService.submitResponse(this.sessionId, regularResponseData);
    console.log('✅ Regular response submitted successfully:', result);
    
    // Process the result to get next question
    await this.processResponseResult(result);
  }
  
  /**
   * Enhanced end response method with structured flow support
   */
  async endResponse(): Promise<void> {
    if (this.structuredInterviewActive) {
      return this.submitStructuredResponse();
    }
    
    // Use original endResponse implementation for non-structured interviews
    return this.endResponseOriginal();
  }
  
  /**
   * Original end response implementation (renamed)
   */
  private async endResponseOriginal(): Promise<void> {
    try {
      // Safeguard 1: Check if already processing
      if (this.isSubmittingResponse || this.isProcessingAction) {
        console.log('⏳ Response submission already in progress, ignoring duplicate request');
        return;
      }
      
      // Safeguard 2: Implement cooldown to prevent rapid submissions
      const now = Date.now();
      if (now - this.lastSubmissionTime < this.submissionCooldown) {
        const remainingCooldown = this.submissionCooldown - (now - this.lastSubmissionTime);
        console.log(`🛡️ Submission cooldown active, ${remainingCooldown}ms remaining`);
        this.showCooldownMessage(remainingCooldown);
        return;
      }
      
      // Safeguard 3: Validate required data
      if (!this.currentQuestion) {
        console.warn('❌ No current question to respond to');
        this.showValidationError('No question available to respond to.');
        return;
      }
      
      if (!this.sessionId) {
        console.warn('❌ No session ID available');
        this.showValidationError('Session not properly initialized.');
        return;
      }
      
      // Set processing flags
      this.isSubmittingResponse = true;
      this.isProcessingAction = true;
      this.lastSubmissionTime = now;
      
      console.log('📤 Starting response submission for question:', this.currentQuestion.id);
      
      // Prepare response submission
      const responseData = {
        questionId: this.currentQuestion.id,
        transcriptText: this.candidateResponse || this.liveTranscript,
        codeSubmission: this.currentQuestion.coding_required ? this.codeContent : undefined,
        responseStartTime: new Date(),
        responseEndTime: new Date(),
        spacebarPressed: true,
        sessionId: this.sessionId
      };
      
      console.log('📤 Submitting response:', {
        questionId: responseData.questionId,
        transcriptLength: responseData.transcriptText?.length || 0,
        hasCode: !!responseData.codeSubmission,
        sessionId: responseData.sessionId
      });
      
      // Show processing state with visual feedback
      this.updateUIForProcessing();
      
      try {
        // Submit response to backend with timeout
        const result = await Promise.race([
          this.interviewService.submitResponse(this.sessionId, responseData),
          this.createTimeoutPromise(10000) // 10 second timeout
        ]);
        
        if (!result) {
          throw new Error('Request timed out');
        }
        
        console.log('✅ Response submitted successfully:', result);
        
        // Handle response result
        await this.processResponseResult(result);
        
      } catch (submissionError) {
        console.error('❌ Failed to submit response:', submissionError);
        this.handleSubmissionError(submissionError);
      }
      
    } catch (error) {
      console.error('❌ Unexpected error in endResponse:', error);
      this.handleSubmissionError(error);
    } finally {
      // Always reset processing flags
      this.isSubmittingResponse = false;
      this.isProcessingAction = false;
    }
  }
  
  /**
   * Get display text for current interview stage
   */
  public getCurrentStageDisplay(): string {
    if (!this.structuredInterviewActive) {
      return 'Standard Interview';
    }
    
    const stageDisplayMap: { [key: string]: string } = {
      'introduction': 'Introduction',
      'technical_warmup': 'Technical Warm-up',
      'coding_challenge': 'Coding Challenge',
      'behavioral_questions': 'Behavioral Questions',
      'advanced_technical': 'Advanced Technical',
      'conclusion': 'Conclusion'
    };
    
    return stageDisplayMap[this.currentInterviewStage] || this.currentInterviewStage.replace('_', ' ');
  }
  
  /**
   * Get progress percentage for current stage
   */
  public getStageProgressPercentage(): number {
    if (!this.structuredInterviewActive || !this.stageProgress) {
      return 0;
    }
    
    const totalStages = 6; // introduction, technical_warmup, coding_challenge, behavioral_questions, advanced_technical, conclusion
    const stageOrder = ['introduction', 'technical_warmup', 'coding_challenge', 'behavioral_questions', 'advanced_technical', 'conclusion'];
    const currentStageIndex = stageOrder.indexOf(this.currentInterviewStage);
    
    if (currentStageIndex === -1) return 0;
    
    const baseProgress = (currentStageIndex / totalStages) * 100;
    const stageProgressPercent = (this.stageProgress.completed || 0) / (this.stageProgress.total || 1) * (100 / totalStages);
    
    return Math.min(baseProgress + stageProgressPercent, 100);
  }
  
  /**
   * Check if structured interview is active
   */
  public isStructuredInterviewActive(): boolean {
    return this.structuredInterviewActive;
  }
  
  /**
   * Handle interview conclusion from AI Avatar
   */
  private handleInterviewConclusion(conclusionData: any): void {
    console.log('🎉 Interview conclusion received:', conclusionData);
    
    this.currentInterviewStage = 'conclusion';
    this.currentTask = 'Interview completed - Thank you for your time!';
    
    // Update AI status
    this.updateAIStatus('speaking', 'Interview completed');
    
    // Log conclusion message
    if (conclusionData.message) {
      this.logSystemMessage('interviewer', conclusionData.message, new Date());
    } else {
      this.logSystemMessage('system', 'Interview has been completed successfully.', new Date());
    }
    
    // Mark structured interview as completed
    this.structuredInterviewActive = false;
    
    // Auto-end interview after a short delay
    setTimeout(() => {
      this.endInterview();
    }, 5000);
  }

  /**
   * Handle response analysis completion from AI
   */
  private handleResponseAnalysisComplete(analysisData: any): void {
    console.log('🧠 AI response analysis complete:', analysisData);
    
    // Update AI status to indicate analysis is done
    this.updateAIStatus('thinking', 'Analysis complete, preparing next question...');
    
    // Update current task
    this.currentTask = 'AI has finished analyzing your response. Preparing next question...';
    
    // If analysis includes feedback, log it
    if (analysisData.feedback) {
      this.logSystemMessage('system', `AI Feedback: ${analysisData.feedback}`, new Date());
    }
    
    // If analysis includes scores, update confidence
    if (analysisData.confidence !== undefined) {
      this.aiAvatarState.confidence = analysisData.confidence;
    }
    
    // The AI should send a separate 'next_question_ready' message with the actual question
  }

  /**
   * Handle next question from AI
   */
  private handleNextQuestionFromAI(questionData: any): void {
    console.log('❓ Next question ready from AI:', questionData);
    
    // Clear processing states
    this.isSubmittingResponse = false;
    this.isProcessingAction = false;
    
    // Reset the End Response button
    this.resetEndResponseButton();
    
    // Clear previous response data
    this.candidateResponse = '';
    this.liveTranscript = '';
    
    // Handle different question formats from AI
    if (questionData.question) {
      // Structured interview format
      this.handleStructuredQuestion(questionData);
    } else if (questionData.text) {
      // Regular question format
      this.handleNewQuestion(questionData);
    } else if (questionData.problem) {
      // Coding challenge format
      this.handleStructuredCodingChallenge(questionData);
    } else {
      // Generic question handling
      const questionText = questionData.content || questionData.prompt || 'Next question received';
      
      this.currentQuestion = {
        id: questionData.id || `ai_question_${Date.now()}`,
        text: questionText,
        type: questionData.type || 'general',
        coding_required: questionData.coding_required || false,
        programming_language: questionData.programming_language,
        difficulty: questionData.difficulty || 'Medium',
        estimatedSeconds: questionData.timeLimit || 300,
        category: questionData.category || 'general'
      };
      
      this.interviewerQuestion = questionText;
      this.currentTask = questionText;
      
      // Update code editor if needed
      if (questionData.coding_required) {
        this.currentLanguage = questionData.programming_language || 'javascript';
        this.codeContent = this.generateCodeTemplate(questionData);
      }
      
      this.logSystemMessage('interviewer', questionText, new Date());
    }
    
    // Update AI status to listening for response
    this.updateAIStatus('listening', 'Waiting for your response to the new question');
    
    console.log('✅ Next question processed and ready for candidate response');
  }

  /**
   * Handle AI feedback on candidate responses
   */
  private handleAIFeedback(feedbackData: any): void {
    console.log('💬 AI feedback received:', feedbackData);
    
    // Handle different types of feedback
    if (feedbackData.type === 'encouragement') {
      this.updateAIStatus('speaking', 'Providing encouragement...');
      
      // Show encouraging feedback in live transcript temporarily
      if (feedbackData.message) {
        this.liveTranscript = `AI Encouragement: ${feedbackData.message}`;
        
        // Clear after a few seconds
        setTimeout(() => {
          if (this.liveTranscript.startsWith('AI Encouragement:')) {
            this.liveTranscript = '';
          }
        }, 5000);
      }
      
    } else if (feedbackData.type === 'clarification') {
      this.updateAIStatus('speaking', 'Asking for clarification...');
      
      // Treat clarification as a follow-up question
      if (feedbackData.clarificationQuestion) {
        this.currentTask = `Clarification needed: ${feedbackData.clarificationQuestion}`;
        this.logSystemMessage('interviewer', feedbackData.clarificationQuestion, new Date());
      }
      
    } else if (feedbackData.type === 'hint') {
      this.updateAIStatus('speaking', 'Providing a hint...');
      
      // Show hint in current task
      if (feedbackData.hint) {
        this.currentTask = `Hint: ${feedbackData.hint}`;
        this.logSystemMessage('system', `Hint: ${feedbackData.hint}`, new Date());
      }
      
    } else if (feedbackData.type === 'correction') {
      this.updateAIStatus('speaking', 'Providing correction...');
      
      // Show correction feedback
      if (feedbackData.correction) {
        this.currentTask = `Correction: ${feedbackData.correction}`;
        this.logSystemMessage('interviewer', feedbackData.correction, new Date());
      }
      
    } else {
      // Generic feedback handling
      this.updateAIStatus('speaking', 'Providing feedback...');
      
      if (feedbackData.message) {
        this.logSystemMessage('interviewer', feedbackData.message, new Date());
      }
    }
    
    // If feedback includes confidence score, update it
    if (feedbackData.confidence !== undefined) {
      this.aiAvatarState.confidence = feedbackData.confidence;
    }
    
    // Handle AI speech if audio feedback is provided
    if (feedbackData.speech) {
      this.handleAISpeech({
        text: feedbackData.speech,
        audioUrl: feedbackData.audioUrl,
        action: 'feedback',
        isIntroduction: false
      });
    }
    
    // Update status back to listening after feedback
    setTimeout(() => {
      this.updateAIStatus('listening', 'Waiting for your response');
    }, 3000);
  }
  
  // ==================== ENHANCED TRANSCRIPT EVENT HANDLERS ====================

  /**
   * Handle error reported from EnhancedTranscriptComponent
   */
  onTranscriptError(error: TranscriptError): void {
    console.error('📝 Transcript error reported:', error);
    
    // Log the error
    this.logSystemMessage('system', `Transcript error: ${error.description}`, new Date());
    
    // Show error notification to user
    this.showErrorNotification(`Transcript Error: ${error.description}`);
    
    // Send error to analytics for tracking
    if (this.analyticsWS && this.analyticsWS.readyState === WebSocket.OPEN) {
      const errorMessage = {
        type: 'transcript_error',
        data: {
          sessionId: this.sessionId,
          error: error,
          timestamp: new Date().toISOString()
        }
      };
      
      this.analyticsWS.send(JSON.stringify(errorMessage));
    }
    
    // Try to recover transcript service if connection-related error
    // Note: 'connection' and 'websocket' are not valid TranscriptError types
    // but keeping this for potential future error types
    console.log('🔄 Attempting to reconnect transcript service...');
    setTimeout(() => {
      this.connectToTranscriptService();
    }, 2000);
  }

  /**
   * Handle correction submitted from EnhancedTranscriptComponent
   */
  onTranscriptCorrectionSubmitted(correction: ErrorCorrectionRequest): void {
    console.log('📝 Transcript correction submitted:', correction);
    
    // Update live transcript with corrected text
    if (this.liveTranscript === correction.originalText) {
      this.liveTranscript = correction.correctedText;
    }
    
    // Update candidate response if it matches
    if (this.candidateResponse === correction.originalText) {
      this.candidateResponse = correction.correctedText;
    }
    
    // Log the correction
    this.logSystemMessage('system', `Transcript corrected: "${correction.originalText}" → "${correction.correctedText}"`, correction.timestamp);
    
    // Send correction to transcript service
    if (this.transcriptService) {
      this.transcriptService.submitCorrection(
        this.sessionId,
        correction.originalText,
        correction.correctedText,
        correction.timestamp
      ).catch((error: any) => {
        console.warn('Failed to submit transcript correction to service:', error);
      });
    }
    
    // Send correction to AI services for improved accuracy
    const correctionMessage = {
      type: 'transcript_correction',
      data: {
        sessionId: this.sessionId,
        originalText: correction.originalText,
        correctedText: correction.correctedText,
        timestamp: correction.timestamp.toISOString(),
        speaker: 'candidate'
      }
    };
    
    // Send to orchestrator
    if (this.orchestratorWS && this.orchestratorWS.readyState === WebSocket.OPEN) {
      this.orchestratorWS.send(JSON.stringify(correctionMessage));
    }
    
    // Send to analytics
    if (this.analyticsWS && this.analyticsWS.readyState === WebSocket.OPEN) {
      this.analyticsWS.send(JSON.stringify(correctionMessage));
    }
    
    // Show success notification
    this.showSuccessNotification('Transcript correction submitted successfully');
  }

  /**
   * Handle transcript exported from EnhancedTranscriptComponent
   */
  onTranscriptExported(exportData: { format: string; data: any }): void {
    // Generate filename similar to what enhanced transcript component does
    const filename = `interview_transcript_${this.sessionId}_${new Date().toISOString().split('T')[0]}.${exportData.format}`;
    const contentLength = typeof exportData.data === 'string' ? exportData.data.length : JSON.stringify(exportData.data).length;
    
    console.log('📥 Transcript exported:', {
      format: exportData.format,
      filename: filename,
      contentLength: contentLength
    });
    
    // Log the export action
    this.logSystemMessage('system', `Transcript exported as ${exportData.format}: ${filename}`, new Date());
    
    // Send export event to analytics
    if (this.analyticsWS && this.analyticsWS.readyState === WebSocket.OPEN) {
      const exportMessage = {
        type: 'transcript_exported',
        data: {
          sessionId: this.sessionId,
          format: exportData.format,
          filename: filename,
          contentLength: contentLength,
          timestamp: new Date().toISOString()
        }
      };
      
      this.analyticsWS.send(JSON.stringify(exportMessage));
    }
    
    // Show success notification
    this.showSuccessNotification(`Transcript exported successfully as ${filename}`);
  }
  
  /**
   * Show success notification
   */
  private showSuccessNotification(message: string): void {
    const successDiv = document.createElement('div');
    successDiv.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: #27ae60;
      color: white;
      padding: 15px 20px;
      border-radius: 6px;
      z-index: 9999;
      box-shadow: 0 4px 12px rgba(39, 174, 96, 0.3);
      font-weight: 600;
      max-width: 400px;
      border-left: 4px solid #229954;
    `;
    
    successDiv.innerHTML = `
      <div style="display: flex; align-items: center; gap: 10px;">
        <i class="fas fa-check-circle"></i>
        <span>${message}</span>
        <button onclick="this.parentNode.parentNode.remove()" 
                style="background: none; border: none; color: white; cursor: pointer; font-size: 18px; margin-left: auto;">
          ×
        </button>
      </div>
    `;
    
    document.body.appendChild(successDiv);
    
    // Auto-remove after 5 seconds
    setTimeout(() => {
      if (successDiv.parentNode) {
        successDiv.parentNode.removeChild(successDiv);
      }
    }, 5000);
  }
  
  /**
   * Show authentication guidance for candidates having trouble accessing the interview
   */
  private showAuthenticationGuidance(): void {
    const guidanceDiv = document.createElement('div');
    guidanceDiv.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,0.9);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10000;
    `;
    
    guidanceDiv.innerHTML = `
      <div style="background: white; border-radius: 12px; padding: 40px; max-width: 700px; max-height: 80vh; overflow-y: auto; text-align: center;">
        <div style="color: #3498db; margin-bottom: 30px;">
          <i class="fas fa-user-shield" style="font-size: 64px;"></i>
        </div>
        
        <h2 style="color: #2c3e50; margin-bottom: 20px;">🔑 Interview Access Required</h2>
        
        <div style="color: #7f8c8d; margin-bottom: 30px; line-height: 1.6; text-align: left;">
          <p style="margin-bottom: 20px;"><strong>We couldn't find your interview access credentials.</strong></p>
          
          <p style="margin-bottom: 15px;">To join this interview, you need:</p>
          <ul style="margin-bottom: 20px; padding-left: 20px;">
            <li>📧 <strong>Email invitation link</strong> from the recruiter</li>
            <li>🔗 <strong>Interview URL with access token</strong></li>
            <li>👤 <strong>Login credentials</strong> if you're a recruiter</li>
          </ul>
          
          <div style="background: #f8f9fa; padding: 15px; border-radius: 6px; margin-bottom: 20px;">
            <strong>📱 For Candidates:</strong>
            <br>Check your email for an interview invitation with a direct link to join.
          </div>
          
          <div style="background: #e8f4f8; padding: 15px; border-radius: 6px; margin-bottom: 20px;">
            <strong>👩‍💼 For Recruiters:</strong>
            <br>Please login with your recruiter credentials first.
          </div>
          
          <p style="font-size: 14px; color: #95a5a6;">Session ID: <code style="background: #ecf0f1; padding: 2px 6px; border-radius: 3px;">${this.sessionId}</code></p>
        </div>
        
        <div style="margin-top: 30px;">
          <button onclick="window.location.href='/login'" 
                  style="background: #3498db; color: white; border: none; padding: 12px 24px; border-radius: 6px; cursor: pointer; font-weight: 600; margin-right: 15px;">
            🚀 Go to Login
          </button>
          <button onclick="window.location.href='mailto:support@company.com?subject=Interview%20Access%20Issue&body=I%20am%20having%20trouble%20accessing%20my%20interview%20session%20${this.sessionId}'" 
                  style="background: #95a5a6; color: white; border: none; padding: 12px 24px; border-radius: 6px; cursor: pointer; font-weight: 600;">
            📧 Contact Support
          </button>
        </div>
        
        <div style="margin-top: 20px; font-size: 12px; color: #bdc3c7;">
          You will be redirected to login in a few seconds...
        </div>
      </div>
    `;
    
    document.body.appendChild(guidanceDiv);
  }
}
