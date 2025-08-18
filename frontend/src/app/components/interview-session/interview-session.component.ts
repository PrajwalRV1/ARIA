import { Component, OnInit, OnDestroy, ViewChild, ElementRef, HostListener } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, interval, fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

// Monaco Editor
import * as monaco from 'monaco-editor';

// Services
import { InterviewService } from '../../services/interview.service';
import { WebRTCService } from '../../services/webrtc.service';
import { TranscriptService } from '../../services/transcript.service';

// Models
import { InterviewSession, Question, TranscriptUpdate } from '../../models/interview.models';

@Component({
  selector: 'app-interview-session',
  templateUrl: './interview-session.component.html',
  styleUrls: ['./interview-session.component.scss']
})
export class InterviewSessionComponent implements OnInit, OnDestroy {
  @ViewChild('localVideo', { static: true }) localVideo!: ElementRef<HTMLVideoElement>;
  @ViewChild('remoteVideo', { static: true }) remoteVideo!: ElementRef<HTMLVideoElement>;
  @ViewChild('monacoEditor', { static: true }) monacoContainer!: ElementRef;
  @ViewChild('transcriptContainer', { static: true }) transcriptContainer!: ElementRef;

  // Session data
  sessionId: string = '';
  session: InterviewSession | null = null;
  currentQuestion: Question | null = null;
  
  // Interview state
  isConnected = false;
  isRecording = false;
  isInterviewStarted = false;
  isInterviewEnded = false;
  currentQuestionIndex = 0;
  
  // Monaco Editor
  monacoEditor: monaco.editor.IStandaloneCodeEditor | null = null;
  currentLanguage = 'typescript';
  
  // Transcript and Communication
  transcriptText = '';
  fullTranscript: TranscriptUpdate[] = [];
  chatMessages: any[] = [];
  currentChatMessage = '';
  
  // WebRTC and Media
  localStream: MediaStream | null = null;
  isVideoEnabled = true;
  isAudioEnabled = true;
  connectionQuality = 'good';
  
  // Spacebar detection
  private spacebarPressed = false;
  private responseStartTime: Date | null = null;
  private responseEndTime: Date | null = null;
  
  // Subscriptions
  private subscriptions: Subscription[] = [];
  private transcriptSubscription: Subscription | null = null;
  
  // UI State
  isLoading = false;
  showTranscript = true;
  showCodeEditor = true;
  showChat = false;
  
  // Progress tracking
  timeElapsed = 0;
  questionsAnswered = 0;
  private timerSubscription: Subscription | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private interviewService: InterviewService,
    private webrtcService: WebRTCService,
    private transcriptService: TranscriptService
  ) {}

  ngOnInit(): void {
    this.sessionId = this.route.snapshot.params['sessionId'];
    
    if (!this.sessionId) {
      this.router.navigate(['/dashboard']);
      return;
    }
    
    this.initializeInterview();
    this.setupKeyboardListeners();
    this.setupSpacebarDetection();
  }

  ngOnDestroy(): void {
    this.cleanup();
  }

  private async initializeInterview(): Promise<void> {
    try {
      this.isLoading = true;
      
      // Load interview session
      this.session = await this.interviewService.getInterviewSession(this.sessionId);
      
      if (!this.session) {
        throw new Error('Interview session not found');
      }
      
      // Initialize WebRTC
      await this.initializeWebRTC();
      
      // Initialize Monaco Editor
      this.initializeMonacoEditor();
      
      // Connect to transcript service
      this.connectToTranscriptService();
      
      // Load current question
      await this.loadCurrentQuestion();
      
      // Start timer
      this.startTimer();
      
      this.isLoading = false;
      
    } catch (error) {
      console.error('Failed to initialize interview:', error);
      this.handleError('Failed to initialize interview session');
    }
  }

  private async initializeWebRTC(): Promise<void> {
    try {
      // Get user media
      this.localStream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 1280 },
          height: { ideal: 720 },
          frameRate: { ideal: 30 }
        },
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 48000
        }
      });
      
      // Display local video
      this.localVideo.nativeElement.srcObject = this.localStream;
      
      // Initialize WebRTC service
      await this.webrtcService.initialize(this.sessionId, this.localStream);
      
      // Set up WebRTC event handlers
      this.webrtcService.onRemoteStream((stream: MediaStream) => {
        this.remoteVideo.nativeElement.srcObject = stream;
      });
      
      this.webrtcService.onConnectionStateChange((state: RTCPeerConnectionState) => {
        this.isConnected = state === 'connected';
        this.updateConnectionQuality(state);
      });
      
      this.webrtcService.onDataChannelMessage((message: any) => {
        this.handleDataChannelMessage(message);
      });
      
      console.log('WebRTC initialized successfully');
      
    } catch (error) {
      console.error('Failed to initialize WebRTC:', error);
      throw new Error('Camera and microphone access required');
    }
  }

  private initializeMonacoEditor(): void {
    try {
      // Configure Monaco Editor
      const editorOptions: monaco.editor.IStandaloneEditorConstructionOptions = {
        value: '// Type your code here...\n',
        language: this.currentLanguage,
        theme: 'vs-dark',
        automaticLayout: true,
        minimap: { enabled: false },
        fontSize: 14,
        lineNumbers: 'on',
        roundedSelection: false,
        scrollBeyondLastLine: false,
        readOnly: false,
        wordWrap: 'on',
        folding: true,
        lineDecorationsWidth: 20,
        lineNumbersMinChars: 3,
        renderLineHighlight: 'all',
        selectOnLineNumbers: true,
        tabSize: 2,
        insertSpaces: true
      };
      
      // Create editor instance
      this.monacoEditor = monaco.editor.create(
        this.monacoContainer.nativeElement,
        editorOptions
      );
      
      // Set up content change listener with debouncing
      const contentChangeSubscription = fromEvent(this.monacoEditor.onDidChangeModelContent, (e: any) => e)
        .pipe(
          debounceTime(500),
          distinctUntilChanged()
        )
        .subscribe(() => {
          this.onCodeChange();
        });
      
      this.subscriptions.push(contentChangeSubscription);
      
      // Set up key bindings
      this.monacoEditor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
        this.onCodeSubmit();
      });
      
      console.log('Monaco Editor initialized');
      
    } catch (error) {
      console.error('Failed to initialize Monaco Editor:', error);
    }
  }

  private connectToTranscriptService(): void {
    try {
      this.transcriptSubscription = this.transcriptService.connect(this.sessionId)
        .subscribe({
          next: (update: TranscriptUpdate) => {
            this.handleTranscriptUpdate(update);
          },
          error: (error) => {
            console.error('Transcript service error:', error);
          }
        });
      
      console.log('Connected to transcript service');
      
    } catch (error) {
      console.error('Failed to connect to transcript service:', error);
    }
  }

  private async loadCurrentQuestion(): Promise<void> {
    try {
      this.currentQuestion = await this.interviewService.getCurrentQuestion(this.sessionId);
      
      if (this.currentQuestion) {
        // Update Monaco editor language if it's a coding question
        if (this.currentQuestion.coding_required) {
          this.updateEditorLanguage(this.currentQuestion.programming_language || 'typescript');
        }
      }
      
    } catch (error) {
      console.error('Failed to load current question:', error);
    }
  }

  // Spacebar detection for response completion
  private setupSpacebarDetection(): void {
    const spacebarSubscription = fromEvent<KeyboardEvent>(document, 'keydown')
      .subscribe((event: KeyboardEvent) => {
        if (event.code === 'Space' && !this.isTypingInEditor()) {
          event.preventDefault();
          this.onSpacebarPressed();
        }
      });
    
    this.subscriptions.push(spacebarSubscription);
  }

  private setupKeyboardListeners(): void {
    // Global keyboard shortcuts
    const keyboardSubscription = fromEvent<KeyboardEvent>(document, 'keydown')
      .subscribe((event: KeyboardEvent) => {
        // Toggle video (Ctrl+Shift+V)
        if (event.ctrlKey && event.shiftKey && event.key === 'V') {
          event.preventDefault();
          this.toggleVideo();
        }
        
        // Toggle audio (Ctrl+Shift+A)
        if (event.ctrlKey && event.shiftKey && event.key === 'A') {
          event.preventDefault();
          this.toggleAudio();
        }
        
        // Toggle transcript (Ctrl+Shift+T)
        if (event.ctrlKey && event.shiftKey && event.key === 'T') {
          event.preventDefault();
          this.toggleTranscript();
        }
        
        // Submit code (Ctrl+Enter)
        if (event.ctrlKey && event.key === 'Enter' && this.monacoEditor?.hasTextFocus()) {
          event.preventDefault();
          this.onCodeSubmit();
        }
      });
    
    this.subscriptions.push(keyboardSubscription);
  }

  private isTypingInEditor(): boolean {
    return this.monacoEditor?.hasTextFocus() || 
           document.activeElement?.tagName === 'INPUT' || 
           document.activeElement?.tagName === 'TEXTAREA';
  }

  // Event handlers
  async onSpacebarPressed(): Promise<void> {
    if (this.spacebarPressed) return;
    
    this.spacebarPressed = true;
    this.responseEndTime = new Date();
    
    console.log('Spacebar pressed - ending response');
    
    try {
      // Signal end of response to transcript service
      await this.transcriptService.signalResponseEnd(this.sessionId);
      
      // Submit current response
      await this.submitResponse();
      
      // Reset for next response
      setTimeout(() => {
        this.spacebarPressed = false;
        this.responseStartTime = null;
        this.responseEndTime = null;
      }, 1000);
      
    } catch (error) {
      console.error('Error handling spacebar press:', error);
      this.spacebarPressed = false;
    }
  }

  onCodeChange(): void {
    if (!this.monacoEditor) return;
    
    const code = this.monacoEditor.getValue();
    
    // Send code update to transcript service
    this.transcriptService.sendCodeUpdate(this.sessionId, code);
    
    // Start response timing if not started
    if (!this.responseStartTime) {
      this.responseStartTime = new Date();
    }
  }

  onCodeSubmit(): void {
    if (!this.monacoEditor) return;
    
    const code = this.monacoEditor.getValue();
    console.log('Code submitted:', code);
    
    // Trigger automatic response submission
    this.onSpacebarPressed();
  }

  onChatMessageSend(): void {
    if (!this.currentChatMessage.trim()) return;
    
    const message = {
      text: this.currentChatMessage,
      timestamp: new Date(),
      sender: 'candidate'
    };
    
    this.chatMessages.push(message);
    
    // Send to transcript service
    this.transcriptService.sendChatMessage(this.sessionId, this.currentChatMessage);
    
    this.currentChatMessage = '';
    
    // Start response timing if not started
    if (!this.responseStartTime) {
      this.responseStartTime = new Date();
    }
  }

  private async submitResponse(): Promise<void> {
    try {
      if (!this.currentQuestion) return;
      
      const code = this.monacoEditor?.getValue() || '';
      const responseData = {
        questionId: this.currentQuestion.id,
        transcriptText: this.getRecentTranscript(),
        codeSubmission: code,
        chatMessages: this.getRecentChatMessages(),
        responseStartTime: this.responseStartTime,
        responseEndTime: this.responseEndTime,
        spacebarPressed: true
      };
      
      // Submit to interview service
      const result = await this.interviewService.submitResponse(this.sessionId, responseData);
      
      if (result.nextQuestion) {
        this.currentQuestion = result.nextQuestion;
        this.currentQuestionIndex++;
        this.questionsAnswered++;
        
        // Clear editor for new question
        if (this.monacoEditor) {
          this.monacoEditor.setValue('// Type your code here...\n');
        }
      } else if (result.shouldEnd) {
        await this.endInterview();
      }
      
    } catch (error) {
      console.error('Failed to submit response:', error);
      this.handleError('Failed to submit response');
    }
  }

  // Media controls
  toggleVideo(): void {
    if (!this.localStream) return;
    
    const videoTrack = this.localStream.getVideoTracks()[0];
    if (videoTrack) {
      videoTrack.enabled = !videoTrack.enabled;
      this.isVideoEnabled = videoTrack.enabled;
    }
  }

  toggleAudio(): void {
    if (!this.localStream) return;
    
    const audioTrack = this.localStream.getAudioTracks()[0];
    if (audioTrack) {
      audioTrack.enabled = !audioTrack.enabled;
      this.isAudioEnabled = audioTrack.enabled;
    }
  }

  // UI controls
  toggleTranscript(): void {
    this.showTranscript = !this.showTranscript;
  }

  toggleCodeEditor(): void {
    this.showCodeEditor = !this.showCodeEditor;
    
    // Resize Monaco editor when visibility changes
    setTimeout(() => {
      this.monacoEditor?.layout();
    }, 100);
  }

  toggleChat(): void {
    this.showChat = !this.showChat;
  }

  updateEditorLanguage(language: string): void {
    if (!this.monacoEditor) return;
    
    this.currentLanguage = language;
    const model = this.monacoEditor.getModel();
    
    if (model) {
      monaco.editor.setModelLanguage(model, language);
    }
  }

  // Interview control
  async startInterview(): Promise<void> {
    try {
      await this.interviewService.startInterview(this.sessionId);
      this.isInterviewStarted = true;
      this.responseStartTime = new Date();
      
    } catch (error) {
      console.error('Failed to start interview:', error);
      this.handleError('Failed to start interview');
    }
  }

  async endInterview(): Promise<void> {
    try {
      await this.interviewService.endInterview(this.sessionId);
      this.isInterviewEnded = true;
      this.cleanup();
      
      // Navigate to results page
      this.router.navigate(['/interview', this.sessionId, 'results']);
      
    } catch (error) {
      console.error('Failed to end interview:', error);
    }
  }

  // Utility methods
  private handleTranscriptUpdate(update: TranscriptUpdate): void {
    this.fullTranscript.push(update);
    
    // Update display transcript (last 10 messages)
    const recentTranscripts = this.fullTranscript.slice(-10);
    this.transcriptText = recentTranscripts
      .map(t => `[${new Date(t.timestamp).toLocaleTimeString()}] ${t.text}`)
      .join('\n');
    
    // Auto-scroll transcript
    setTimeout(() => {
      if (this.transcriptContainer?.nativeElement) {
        const element = this.transcriptContainer.nativeElement;
        element.scrollTop = element.scrollHeight;
      }
    }, 100);
  }

  private handleDataChannelMessage(message: any): void {
    console.log('Data channel message:', message);
    
    if (message.type === 'ai_question') {
      // Handle AI interviewer messages
      this.currentQuestion = message.question;
    }
  }

  private updateConnectionQuality(state: RTCPeerConnectionState): void {
    switch (state) {
      case 'connected':
        this.connectionQuality = 'good';
        break;
      case 'connecting':
        this.connectionQuality = 'fair';
        break;
      case 'disconnected':
      case 'failed':
        this.connectionQuality = 'poor';
        break;
      default:
        this.connectionQuality = 'unknown';
    }
  }

  private getRecentTranscript(): string {
    return this.fullTranscript
      .filter(t => t.source === 'speech')
      .slice(-5)
      .map(t => t.text)
      .join(' ');
  }

  private getRecentChatMessages(): any[] {
    const cutoffTime = new Date(Date.now() - 5 * 60 * 1000); // Last 5 minutes
    return this.chatMessages.filter(msg => msg.timestamp > cutoffTime);
  }

  private startTimer(): void {
    this.timerSubscription = interval(1000).subscribe(() => {
      this.timeElapsed++;
    });
  }

  private handleError(message: string): void {
    console.error(message);
    // Show user-friendly error message
    alert(message);
  }

  private cleanup(): void {
    // Clean up subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions = [];
    
    if (this.transcriptSubscription) {
      this.transcriptSubscription.unsubscribe();
    }
    
    if (this.timerSubscription) {
      this.timerSubscription.unsubscribe();
    }
    
    // Clean up WebRTC
    this.webrtcService.cleanup();
    
    // Clean up media stream
    if (this.localStream) {
      this.localStream.getTracks().forEach(track => track.stop());
    }
    
    // Clean up Monaco editor
    if (this.monacoEditor) {
      this.monacoEditor.dispose();
    }
    
    // Disconnect from transcript service
    this.transcriptService.disconnect();
  }

  // Getters for template
  get formattedTimeElapsed(): string {
    const hours = Math.floor(this.timeElapsed / 3600);
    const minutes = Math.floor((this.timeElapsed % 3600) / 60);
    const seconds = this.timeElapsed % 60;
    
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  get connectionStatus(): string {
    if (!this.isConnected) return 'Connecting...';
    return `Connected (${this.connectionQuality})`;
  }

  get progressPercentage(): number {
    if (!this.session) return 0;
    return Math.min((this.questionsAnswered / (this.session.maxQuestions || 20)) * 100, 100);
  }
}
