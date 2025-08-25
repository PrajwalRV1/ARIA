import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';

export interface TranscriptUpdate {
  session_id: string;
  text: string;
  confidence: number;
  is_final: boolean;
  timestamp: string;
  source: 'speech' | 'code' | 'chat';
  engine_used?: string;
}

export interface CodeUpdate {
  type: 'code_update';
  session_id: string;
  code: string;
  timestamp: string;
  source: 'code';
}

export interface ChatMessage {
  type: 'chat_message';
  session_id: string;
  text: string;
  timestamp: string;
  source: 'chat';
}

export interface TranscriptMergeRequest {
  session_id: string;
  audio_transcript: string;
  code_content: string;
  chat_messages: any[];
  timestamp: string;
  merge_strategy: string;
}

@Injectable({
  providedIn: 'root'
})
export class TranscriptService {
  private wsUrl = 'wss://localhost:8002';
  private socket$: WebSocketSubject<any> | null = null;
  private isConnected = false;
  private currentSessionId = '';
  
  // Observables for real-time updates
  private transcriptUpdateSubject = new Subject<TranscriptUpdate>();
  private codeUpdateSubject = new Subject<CodeUpdate>();
  private chatMessageSubject = new Subject<ChatMessage>();
  private connectionStateSubject = new BehaviorSubject<boolean>(false);
  
  // Buffer for offline messages
  private messageBuffer: any[] = [];
  
  constructor(@Inject(PLATFORM_ID) private platformId: Object) {
    console.log('TranscriptService initialized');
  }

  /**
   * Connect to the transcript WebSocket for a session - SSR safe
   */
  connect(sessionId: string): Observable<TranscriptUpdate> {
    // Only create WebSocket connections in browser environment
    if (!isPlatformBrowser(this.platformId)) {
      console.log('Skipping WebSocket connection in SSR environment');
      return this.transcriptUpdateSubject.asObservable();
    }

    try {
      this.currentSessionId = sessionId;
      const wsEndpoint = `${this.wsUrl}/ws/transcript/${sessionId}`;
      
      // Create WebSocket connection
      this.socket$ = webSocket({
        url: wsEndpoint,
        openObserver: {
          next: () => {
            this.isConnected = true;
            this.connectionStateSubject.next(true);
            console.log(`Connected to transcript WebSocket for session ${sessionId}`);
            
            // Send any buffered messages
            this.flushMessageBuffer();
          }
        },
        closeObserver: {
          next: () => {
            this.isConnected = false;
            this.connectionStateSubject.next(false);
            console.log(`Disconnected from transcript WebSocket for session ${sessionId}`);
          }
        }
      });
      
      // Subscribe to incoming messages
      this.socket$.subscribe({
        next: (message: any) => {
          this.handleWebSocketMessage(message);
        },
        error: (error) => {
          console.error('WebSocket error:', error);
          this.isConnected = false;
          this.connectionStateSubject.next(false);
          
          // Auto-reconnect after delay
          setTimeout(() => {
            console.log('Attempting to reconnect...');
            this.connect(sessionId);
          }, 3000);
        }
      });
      
      return this.transcriptUpdateSubject.asObservable();
      
    } catch (error) {
      console.error('Failed to connect to transcript service:', error);
      throw error;
    }
  }
  
  /**
   * Disconnect from the WebSocket
   */
  disconnect(): void {
    if (this.socket$) {
      this.socket$.complete();
      this.socket$ = null;
    }
    this.isConnected = false;
    this.connectionStateSubject.next(false);
    console.log('Transcript WebSocket disconnected');
  }
  
  /**
   * Send code update to transcript service
   */
  sendCodeUpdate(sessionId: string, code: string): void {
    const message = {
      type: 'code_update',
      session_id: sessionId,
      code: code,
      timestamp: new Date().toISOString()
    };
    
    this.sendMessage(message);
  }
  
  /**
   * Send chat message to transcript service
   */
  sendChatMessage(sessionId: string, text: string): void {
    const message = {
      type: 'chat_message',
      session_id: sessionId,
      text: text,
      timestamp: new Date().toISOString()
    };
    
    this.sendMessage(message);
  }
  
  /**
   * Signal the end of a response (spacebar pressed)
   */
  async signalResponseEnd(sessionId: string): Promise<void> {
    const message = {
      type: 'response_end',
      session_id: sessionId,
      timestamp: new Date().toISOString()
    };
    
    this.sendMessage(message);
    
    // Also trigger transcript merge
    await this.mergeTranscripts(sessionId);
  }
  
  /**
   * Send audio data for transcription
   */
  sendAudioData(audioBlob: Blob): void {
    if (!this.socket$ || !this.isConnected) {
      console.warn('WebSocket not connected, cannot send audio data');
      return;
    }
    
    // Convert audio blob to array buffer and send
    const reader = new FileReader();
    reader.onload = () => {
      if (reader.result instanceof ArrayBuffer) {
        this.socket$!.next(new Uint8Array(reader.result));
      }
    };
    reader.readAsArrayBuffer(audioBlob);
  }
  
  /**
   * Start transcription for the session with engine selection
   */
  startTranscription(engine: string = 'vosk_large_en'): void {
    const message = {
      type: 'start_transcription',
      audio_config: {
        sample_rate: 16000,
        channels: 1,
        format: 'pcm',
        engine: engine
      }
    };
    
    this.sendMessage(message);
    console.log(`Started transcription with ${engine} engine`);
  }
  
  /**
   * Stop transcription for the session
   */
  stopTranscription(): void {
    const message = {
      type: 'stop_transcription'
    };
    
    this.sendMessage(message);
  }
  
  /**
   * Get merged transcript from the server
   */
  async getTranscript(sessionId: string): Promise<any> {
    try {
      const response = await fetch(`https://localhost:8002/transcript/${sessionId}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return await response.json();
    } catch (error) {
      console.error('Failed to get transcript:', error);
      throw error;
    }
  }
  
  /**
   * Merge transcripts with code and chat content
   */
  async mergeTranscripts(sessionId: string, additionalData?: Partial<TranscriptMergeRequest>): Promise<any> {
    try {
      const mergeRequest: TranscriptMergeRequest = {
        session_id: sessionId,
        audio_transcript: '',
        code_content: '',
        chat_messages: [],
        timestamp: new Date().toISOString(),
        merge_strategy: 'chronological',
        ...additionalData
      };
      
      const response = await fetch('https://localhost:8002/transcript/merge', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(mergeRequest)
      });
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      return await response.json();
      
    } catch (error) {
      console.error('Failed to merge transcripts:', error);
      throw error;
    }
  }
  
  // Observable getters
  onTranscriptUpdate(): Observable<TranscriptUpdate> {
    return this.transcriptUpdateSubject.asObservable();
  }
  
  onCodeUpdate(): Observable<CodeUpdate> {
    return this.codeUpdateSubject.asObservable();
  }
  
  onChatMessage(): Observable<ChatMessage> {
    return this.chatMessageSubject.asObservable();
  }
  
  onConnectionStateChange(): Observable<boolean> {
    return this.connectionStateSubject.asObservable();
  }
  
  // Getters
  get connected(): boolean {
    return this.isConnected;
  }
  
  get sessionId(): string {
    return this.currentSessionId;
  }
  
  /**
   * Send message to WebSocket with error handling
   */
  private sendMessage(message: any): void {
    if (this.socket$ && this.isConnected) {
      try {
        this.socket$.next(message);
      } catch (error) {
        console.error('Error sending message:', error);
        // Buffer the message for retry
        this.messageBuffer.push(message);
      }
    } else {
      console.warn('WebSocket not connected, buffering message');
      this.messageBuffer.push(message);
    }
  }
  
  /**
   * Handle incoming WebSocket messages
   */
  private handleWebSocketMessage(message: any): void {
    try {
      console.log('Received transcript message:', message);
      
      switch (message.type) {
        case 'connection_established':
          console.log('Transcript connection established:', message.message);
          break;
          
        case 'transcript_update':
          const transcriptUpdate: TranscriptUpdate = {
            session_id: message.session_id,
            text: message.text,
            confidence: message.confidence,
            is_final: message.is_final,
            timestamp: message.timestamp,
            source: message.source || 'speech',
            engine_used: message.engine_used
          };
          this.transcriptUpdateSubject.next(transcriptUpdate);
          break;
          
        case 'code_update':
          const codeUpdate: CodeUpdate = {
            type: 'code_update',
            session_id: message.session_id,
            code: message.code,
            timestamp: message.timestamp,
            source: 'code'
          };
          this.codeUpdateSubject.next(codeUpdate);
          break;
          
        case 'chat_message':
          const chatMessage: ChatMessage = {
            type: 'chat_message',
            session_id: message.session_id,
            text: message.text,
            timestamp: message.timestamp,
            source: 'chat'
          };
          this.chatMessageSubject.next(chatMessage);
          break;
          
        default:
          console.log('Unknown message type:', message.type);
      }
      
    } catch (error) {
      console.error('Error handling WebSocket message:', error);
    }
  }
  
  /**
   * Submit transcript correction
   */
  async submitCorrection(
    sessionId: string,
    originalText: string,
    correctedText: string,
    timestamp: Date
  ): Promise<void> {
    try {
      const correctionRequest = {
        session_id: sessionId,
        original_text: originalText,
        corrected_text: correctedText,
        timestamp: timestamp.toISOString()
      };
      
      const response = await fetch('https://localhost:8002/transcript/correction', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(correctionRequest)
      });
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      console.log('Transcript correction submitted successfully');
      
    } catch (error) {
      console.error('Failed to submit transcript correction:', error);
      throw error;
    }
  }

  /**
   * Send buffered messages when connection is restored
   */
  private flushMessageBuffer(): void {
    if (this.messageBuffer.length > 0) {
      console.log(`Sending ${this.messageBuffer.length} buffered messages`);
      
      this.messageBuffer.forEach(message => {
        if (this.socket$ && this.isConnected) {
          this.socket$.next(message);
        }
      });
      
      this.messageBuffer = [];
    }
  }
}
