import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { environment } from '../../environments/environment';

interface WebRTCConfig {
  roomUrl?: string;
  token?: string;
  iceServers?: RTCIceServer[];
  userName?: string;
}

@Injectable({
  providedIn: 'root'
})
export class WebRTCService {
  private isInitialized = false;
  
  // Observables for component communication
  private connectionStateSubject = new BehaviorSubject<RTCPeerConnectionState>('new');
  private remoteStreamSubject = new Subject<MediaStream>();
  private dataChannelMessageSubject = new Subject<any>();
  private participantUpdateSubject = new Subject<any>();
  private errorSubject = new Subject<Error>();
  
  // Connection state management
  private localStream: MediaStream | null = null;
  private isVideoEnabled = true;
  private isAudioEnabled = true;
  private sessionId: string = '';
  
  constructor(@Inject(PLATFORM_ID) private platformId: Object) {
    console.log('WebRTC Service initialized - works with embedded Jitsi Meet');
  }

  /**
   * Initialize WebRTC service (works with embedded Jitsi Meet)
   */
  async initialize(sessionId: string, localStream: MediaStream, config?: Partial<WebRTCConfig>): Promise<void> {
    try {
      this.sessionId = sessionId;
      this.localStream = localStream;
      
      // Update track states based on stream
      if (localStream) {
        const videoTrack = localStream.getVideoTracks()[0];
        const audioTrack = localStream.getAudioTracks()[0];
        
        this.isVideoEnabled = videoTrack ? videoTrack.enabled : false;
        this.isAudioEnabled = audioTrack ? audioTrack.enabled : true;
      }
      
      this.isInitialized = true;
      this.connectionStateSubject.next('new');
      
      console.log('✅ WebRTC service initialized for embedded Jitsi Meet:', {
        sessionId: this.sessionId,
        hasVideo: this.isVideoEnabled,
        hasAudio: this.isAudioEnabled
      });
      
    } catch (error) {
      console.error('❌ Failed to initialize WebRTC service:', error);
      this.errorSubject.next(new Error('Failed to initialize WebRTC'));
      throw error;
    }
  }

  /**
   * Join room (simulated for embedded Jitsi Meet)
   */
  async joinRoom(roomUrl: string, token?: string): Promise<void> {
    try {
      console.log('🔗 Joining room (handled by embedded Jitsi Meet):', roomUrl);
      
      // Simulate connection process
      this.connectionStateSubject.next('connecting');
      
      // Simulate successful connection after a short delay
      setTimeout(() => {
        this.connectionStateSubject.next('connected');
        console.log('✅ Room joined successfully (via embedded Jitsi Meet)');
      }, 1000);
      
    } catch (error) {
      console.error('❌ Failed to join room:', error);
      this.connectionStateSubject.next('failed');
      this.errorSubject.next(new Error('Failed to join video call'));
      throw error;
    }
  }

  /**
   * Leave the current room
   */
  async leaveRoom(): Promise<void> {
    try {
      this.connectionStateSubject.next('disconnected');
      console.log('👋 Left room (handled by embedded Jitsi Meet)');
    } catch (error) {
      console.error('❌ Error leaving room:', error);
    }
  }

  /**
   * Toggle video on/off
   */
  toggleVideo(): boolean {
    this.isVideoEnabled = !this.isVideoEnabled;
    
    // Control local stream tracks
    if (this.localStream) {
      const videoTrack = this.localStream.getVideoTracks()[0];
      if (videoTrack) {
        videoTrack.enabled = this.isVideoEnabled;
      }
    }
    
    console.log('📹 Video toggled:', this.isVideoEnabled ? 'ON' : 'OFF');
    return this.isVideoEnabled;
  }

  /**
   * Toggle audio on/off
   */
  toggleAudio(): boolean {
    this.isAudioEnabled = !this.isAudioEnabled;
    
    // Control local stream tracks
    if (this.localStream) {
      const audioTrack = this.localStream.getAudioTracks()[0];
      if (audioTrack) {
        audioTrack.enabled = this.isAudioEnabled;
      }
    }
    
    console.log('🎤 Audio toggled:', this.isAudioEnabled ? 'ON' : 'OFF');
    return this.isAudioEnabled;
  }

  /**
   * Send data message (simulated - Jitsi Meet handles actual messaging)
   */
  sendDataMessage(data: any): void {
    console.log('📨 Sending data message (handled by embedded Jitsi Meet):', data);
    
    // Simulate message being sent
    setTimeout(() => {
      this.dataChannelMessageSubject.next({
        type: 'message_sent',
        data: data,
        timestamp: new Date()
      });
    }, 100);
  }

  /**
   * Get current participants (simulated)
   */
  getParticipants(): { [key: string]: any } {
    return {
      local: {
        sessionId: this.sessionId,
        video: this.isVideoEnabled,
        audio: this.isAudioEnabled
      }
    };
  }

  /**
   * Get current meeting state
   */
  getMeetingState(): string {
    const state = this.connectionStateSubject.value;
    switch (state) {
      case 'connected':
        return 'joined';
      case 'connecting':
        return 'joining';
      case 'disconnected':
        return 'left';
      default:
        return 'new';
    }
  }

  // Observable getters
  onConnectionStateChange(): Observable<RTCPeerConnectionState> {
    return this.connectionStateSubject.asObservable();
  }

  onRemoteStream(): Observable<MediaStream> {
    return this.remoteStreamSubject.asObservable();
  }

  onDataChannelMessage(): Observable<any> {
    return this.dataChannelMessageSubject.asObservable();
  }

  onParticipantUpdate(): Observable<any> {
    return this.participantUpdateSubject.asObservable();
  }

  onError(): Observable<Error> {
    return this.errorSubject.asObservable();
  }

  // Getters
  get videoEnabled(): boolean {
    return this.isVideoEnabled;
  }

  get audioEnabled(): boolean {
    return this.isAudioEnabled;
  }

  get initialized(): boolean {
    return this.isInitialized;
  }

  /**
   * Clean up resources
   */
  cleanup(): void {
    // Stop local stream tracks
    if (this.localStream) {
      this.localStream.getTracks().forEach(track => track.stop());
      this.localStream = null;
    }
    
    // Reset state
    this.isInitialized = false;
    this.connectionStateSubject.next('new');
    
    console.log('🧹 WebRTC service cleaned up (embedded Jitsi Meet)');
  }
}
