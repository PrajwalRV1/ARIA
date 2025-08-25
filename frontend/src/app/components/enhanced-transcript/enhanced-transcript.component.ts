import { Component, OnInit, OnDestroy, Input, Output, EventEmitter, ViewChild, ElementRef, ChangeDetectorRef, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { trigger, state, style, transition, animate } from '@angular/animations';
import { BehaviorSubject, Subject, Observable, interval } from 'rxjs';
import { takeUntil, debounceTime, distinctUntilChanged } from 'rxjs/operators';

export interface TranscriptEntry {
  id: string;
  speaker: 'ai' | 'candidate' | 'system';
  text: string;
  timestamp: Date;
  confidence?: number;
  isInterim?: boolean;
  hasErrors?: boolean;
  errorFlags?: TranscriptError[];
  duration?: number;
  originalText?: string;
  correctedText?: string;
  speakerInfo?: SpeakerInfo;
}

export interface TranscriptError {
  type: 'transcription' | 'audio_quality' | 'unclear' | 'incomplete' | 'timing';
  severity: 'low' | 'medium' | 'high';
  description: string;
  position?: { start: number; end: number };
  suggestion?: string;
  confidence: number;
}

export interface SpeakerInfo {
  name: string;
  role: 'interviewer' | 'candidate' | 'system';
  color: string;
  avatar?: string;
  id: string;
}

export interface TranscriptSettings {
  showTimestamps: boolean;
  showConfidence: boolean;
  showSpeakerAvatars: boolean;
  enableErrorFlagging: boolean;
  enableCorrections: boolean;
  autoScroll: boolean;
  colorCoding: boolean;
  fontSize: 'small' | 'medium' | 'large';
  theme: 'light' | 'dark' | 'high-contrast';
}

export interface ErrorCorrectionRequest {
  entryId: string;
  originalText: string;
  correctedText: string;
  reason: string;
  priority: 'low' | 'medium' | 'high';
  timestamp: Date;
  errorType?: 'transcription' | 'audio_quality' | 'unclear' | 'incomplete' | 'timing';
}

@Component({
  selector: 'app-enhanced-transcript',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './enhanced-transcript.component.html',
  styleUrls: ['./enhanced-transcript.component.scss'],
  animations: [
    trigger('slideDown', [
      transition(':enter', [
        style({ height: '0', opacity: '0', overflow: 'hidden' }),
        animate('300ms ease-in-out', style({ height: '*', opacity: '1' }))
      ]),
      transition(':leave', [
        animate('300ms ease-in-out', style({ height: '0', opacity: '0', overflow: 'hidden' }))
      ])
    ]),
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: '0' }),
        animate('200ms ease-in', style({ opacity: '1' }))
      ]),
      transition(':leave', [
        animate('200ms ease-out', style({ opacity: '0' }))
      ])
    ]),
    trigger('slideIn', [
      transition(':enter', [
        style({ transform: 'translateY(-20px)', opacity: '0' }),
        animate('300ms ease-out', style({ transform: 'translateY(0)', opacity: '1' }))
      ]),
      transition(':leave', [
        animate('200ms ease-in', style({ transform: 'translateY(-20px)', opacity: '0' }))
      ])
    ])
  ]
})
export class EnhancedTranscriptComponent implements OnInit, OnDestroy {
  @ViewChild('transcriptContainer', { static: true }) transcriptContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('errorModal', { static: false }) errorModal!: ElementRef<HTMLDivElement>;
  
  @Input() sessionId: string = '';
  @Input() isRecruiterView: boolean = false;
  @Input() enableErrorCorrection: boolean = true;
  @Input() realTimeUpdates: boolean = true;
  
  @Output() errorReported = new EventEmitter<TranscriptError>();
  @Output() correctionSubmitted = new EventEmitter<ErrorCorrectionRequest>();
  @Output() transcriptExported = new EventEmitter<{ format: string; data: any }>();
  
  // State management
  private readonly transcriptEntriesSubject = new BehaviorSubject<TranscriptEntry[]>([]);
  public readonly transcriptEntries$ = this.transcriptEntriesSubject.asObservable();
  
  private readonly settingsSubject = new BehaviorSubject<TranscriptSettings>({
    showTimestamps: true,
    showConfidence: false,
    showSpeakerAvatars: true,
    enableErrorFlagging: true,
    enableCorrections: true,
    autoScroll: true,
    colorCoding: true,
    fontSize: 'medium',
    theme: 'light'
  });
  public readonly settings$ = this.settingsSubject.asObservable();
  
  // Component state
  public transcriptEntries: TranscriptEntry[] = [];
  public settings: TranscriptSettings;
  public isScrolledToBottom = true;
  public totalEntries = 0;
  public totalErrors = 0;
  public selectedEntry: TranscriptEntry | null = null;
  public isEditingMode = false;
  public searchQuery = '';
  public filteredEntries: TranscriptEntry[] = [];
  
  // Speaker management
  public speakers: Map<string, SpeakerInfo> = new Map();
  public currentSpeaker: SpeakerInfo | null = null;
  
  // Error correction
  public showErrorModal = false;
  public currentErrorCorrection: Partial<ErrorCorrectionRequest & { errorType?: string }> = {};
  public errorTypes = [
    { value: 'transcription', label: 'Incorrect transcription' },
    { value: 'audio_quality', label: 'Poor audio quality' },
    { value: 'unclear', label: 'Unclear speech' },
    { value: 'incomplete', label: 'Incomplete sentence' },
    { value: 'timing', label: 'Wrong timing' }
  ];
  
  // WebSocket connections
  public transcriptWS: WebSocket | null = null;
  private errorReportingWS: WebSocket | null = null;
  
  // Lifecycle
  private destroy$ = new Subject<void>();
  private updateTimer: any = null;
  
  // Performance tracking
  private performanceMetrics = {
    totalUpdates: 0,
    averageLatency: 0,
    errorCount: 0,
    lastUpdate: new Date()
  };
  
  constructor(
    private cdr: ChangeDetectorRef,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    this.settings = this.settingsSubject.value;
  }
  
  ngOnInit(): void {
    console.log(`🎤 Enhanced Transcript Component initialized for session: ${this.sessionId}`);
    
    // Initialize speakers
    this.initializeSpeakers();
    
    // Set up subscriptions
    this.setupSubscriptions();
    
    // Connect to real-time updates (only in browser)
    if (this.realTimeUpdates && isPlatformBrowser(this.platformId)) {
      this.connectToRealTimeTranscript();
    }
    
    // Initialize error reporting (only in browser)
    if (this.enableErrorCorrection && isPlatformBrowser(this.platformId)) {
      this.connectToErrorReporting();
    }
    
    // Start performance monitoring
    this.startPerformanceMonitoring();
  }
  
  ngOnDestroy(): void {
    console.log('🛑 Enhanced Transcript Component cleanup');
    
    this.destroy$.next();
    this.destroy$.complete();
    
    // Disconnect WebSockets
    this.disconnectWebSockets();
    
    // Clear timers
    if (this.updateTimer) {
      clearInterval(this.updateTimer);
    }
  }
  
  private initializeSpeakers(): void {
    // AI Avatar speaker
    this.speakers.set('ai', {
      id: 'ai',
      name: 'AI Interviewer',
      role: 'interviewer',
      color: '#2196F3',
      avatar: '🤖'
    });
    
    // Candidate speaker
    this.speakers.set('candidate', {
      id: 'candidate',
      name: 'Candidate',
      role: 'candidate',
      color: '#4CAF50',
      avatar: '👤'
    });
    
    // System speaker
    this.speakers.set('system', {
      id: 'system',
      name: 'System',
      role: 'system',
      color: '#9E9E9E',
      avatar: '⚙️'
    });
  }
  
  private setupSubscriptions(): void {
    // Subscribe to transcript entries
    this.transcriptEntries$
      .pipe(takeUntil(this.destroy$))
      .subscribe(entries => {
        this.transcriptEntries = entries;
        this.updateFilteredEntries();
        this.updateStats();
        
        if (this.settings.autoScroll && this.isScrolledToBottom) {
          this.scrollToBottom();
        }
        
        this.cdr.detectChanges();
      });
    
    // Subscribe to settings changes
    this.settings$
      .pipe(takeUntil(this.destroy$))
      .subscribe(settings => {
        this.settings = settings;
        this.applyTheme();
        this.cdr.detectChanges();
      });
  }
  
  private connectToRealTimeTranscript(): void {
    try {
      const wsUrl = `wss://localhost:8004/ws/transcript/${this.sessionId}`;
      this.transcriptWS = new WebSocket(wsUrl);
      
      this.transcriptWS.onopen = () => {
        console.log('✅ Connected to real-time transcript service');
        
        // Request transcript configuration
        this.transcriptWS?.send(JSON.stringify({
          type: 'configure',
          settings: {
            enableErrorDetection: this.settings.enableErrorFlagging,
            enableSpeakerLabeling: true,
            enableTimestamps: this.settings.showTimestamps,
            confidenceThreshold: 0.7
          }
        }));
      };
      
      this.transcriptWS.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.handleTranscriptMessage(data);
        } catch (error) {
          console.error('Failed to parse transcript message:', error);
        }
      };
      
      this.transcriptWS.onerror = (error) => {
        console.error('Transcript WebSocket error:', error);
      };
      
      this.transcriptWS.onclose = () => {
        console.log('🔌 Transcript WebSocket disconnected');
        // Attempt reconnection after 5 seconds
        setTimeout(() => {
          if (!this.destroy$.closed) {
            this.connectToRealTimeTranscript();
          }
        }, 5000);
      };
      
    } catch (error) {
      console.error('Failed to connect to transcript service:', error);
    }
  }
  
  private connectToErrorReporting(): void {
    try {
      const wsUrl = `wss://localhost:8003/ws/transcript-errors/${this.sessionId}`;
      this.errorReportingWS = new WebSocket(wsUrl);
      
      this.errorReportingWS.onopen = () => {
        console.log('✅ Connected to error reporting service');
      };
      
      this.errorReportingWS.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.handleErrorReportingMessage(data);
        } catch (error) {
          console.error('Failed to parse error reporting message:', error);
        }
      };
      
      this.errorReportingWS.onerror = (error) => {
        console.error('Error reporting WebSocket error:', error);
      };
      
    } catch (error) {
      console.error('Failed to connect to error reporting service:', error);
    }
  }
  
  private handleTranscriptMessage(data: any): void {
    const startTime = performance.now();
    
    switch (data.type) {
      case 'transcript_entry':
        this.addTranscriptEntry(data.entry);
        break;
        
      case 'transcript_update':
        this.updateTranscriptEntry(data.entryId, data.updates);
        break;
        
      case 'speaker_change':
        this.handleSpeakerChange(data.speaker);
        break;
        
      case 'error_detection':
        this.handleErrorDetection(data.entryId, data.errors);
        break;
        
      case 'confidence_update':
        this.updateEntryConfidence(data.entryId, data.confidence);
        break;
        
      default:
        console.log('Unknown transcript message type:', data.type);
    }
    
    // Track performance
    const endTime = performance.now();
    this.updatePerformanceMetrics(endTime - startTime);
  }
  
  private handleErrorReportingMessage(data: any): void {
    switch (data.type) {
      case 'error_acknowledged':
        console.log('✅ Error report acknowledged:', data.reportId);
        break;
        
      case 'correction_applied':
        this.applyCorrectionUpdate(data.entryId, data.correctedText);
        break;
        
      case 'error_analysis_complete':
        this.handleErrorAnalysisResults(data.entryId, data.analysis);
        break;
        
      default:
        console.log('Unknown error reporting message type:', data.type);
    }
  }
  
  private addTranscriptEntry(entryData: any): void {
    const entry: TranscriptEntry = {
      id: entryData.id || `entry_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      speaker: entryData.speaker || 'system',
      text: entryData.text || '',
      timestamp: new Date(entryData.timestamp || Date.now()),
      confidence: entryData.confidence,
      isInterim: entryData.isInterim || false,
      hasErrors: entryData.hasErrors || false,
      errorFlags: entryData.errorFlags || [],
      duration: entryData.duration,
      originalText: entryData.originalText,
      speakerInfo: this.speakers.get(entryData.speaker)
    };
    
    // Detect potential errors
    if (this.settings.enableErrorFlagging) {
      entry.errorFlags = this.detectPotentialErrors(entry);
      entry.hasErrors = entry.errorFlags.length > 0;
    }
    
    // Update entries
    const currentEntries = this.transcriptEntriesSubject.value;
    const updatedEntries = [...currentEntries, entry];
    this.transcriptEntriesSubject.next(updatedEntries);
    
    console.log(`📝 Added transcript entry from ${entry.speaker}: ${entry.text.substring(0, 50)}...`);
  }
  
  private updateTranscriptEntry(entryId: string, updates: Partial<TranscriptEntry>): void {
    const currentEntries = this.transcriptEntriesSubject.value;
    const entryIndex = currentEntries.findIndex(e => e.id === entryId);
    
    if (entryIndex >= 0) {
      const updatedEntry = { ...currentEntries[entryIndex], ...updates };
      
      // Re-check for errors if text changed
      if (updates.text && this.settings.enableErrorFlagging) {
        updatedEntry.errorFlags = this.detectPotentialErrors(updatedEntry);
        updatedEntry.hasErrors = updatedEntry.errorFlags.length > 0;
      }
      
      const updatedEntries = [...currentEntries];
      updatedEntries[entryIndex] = updatedEntry;
      this.transcriptEntriesSubject.next(updatedEntries);
      
      console.log(`📝 Updated transcript entry ${entryId}`);
    }
  }
  
  private handleSpeakerChange(speakerData: any): void {
    this.currentSpeaker = this.speakers.get(speakerData.id) || null;
    console.log(`🔊 Speaker changed to: ${this.currentSpeaker?.name}`);
  }
  
  private handleErrorDetection(entryId: string, errors: TranscriptError[]): void {
    this.updateTranscriptEntry(entryId, {
      hasErrors: errors.length > 0,
      errorFlags: errors
    });
    
    console.log(`⚠️ Detected ${errors.length} errors in entry ${entryId}`);
  }
  
  private updateEntryConfidence(entryId: string, confidence: number): void {
    this.updateTranscriptEntry(entryId, { confidence });
  }
  
  private applyCorrectionUpdate(entryId: string, correctedText: string): void {
    this.updateTranscriptEntry(entryId, {
      correctedText,
      hasErrors: false,
      errorFlags: []
    });
    
    console.log(`✅ Applied correction to entry ${entryId}`);
  }
  
  private handleErrorAnalysisResults(entryId: string, analysis: any): void {
    console.log(`📊 Error analysis complete for entry ${entryId}:`, analysis);
  }
  
  private detectPotentialErrors(entry: TranscriptEntry): TranscriptError[] {
    const errors: TranscriptError[] = [];
    const text = entry.text.toLowerCase();
    
    // Low confidence detection
    if (entry.confidence !== undefined && entry.confidence < 0.6) {
      errors.push({
        type: 'transcription',
        severity: entry.confidence < 0.3 ? 'high' : 'medium',
        description: 'Low transcription confidence',
        confidence: 1.0 - entry.confidence
      });
    }
    
    // Incomplete sentence detection
    if (!text.match(/[.!?]$/) && text.length > 10 && !entry.isInterim) {
      errors.push({
        type: 'incomplete',
        severity: 'low',
        description: 'Possibly incomplete sentence',
        confidence: 0.7
      });
    }
    
    // Unclear speech patterns
    if (text.includes('[unclear]') || text.includes('[inaudible]')) {
      errors.push({
        type: 'unclear',
        severity: 'medium',
        description: 'Contains unclear or inaudible portions',
        confidence: 0.9
      });
    }
    
    // Excessive filler words (may indicate transcription issues)
    const fillerWords = ['um', 'uh', 'ah', 'er'];
    const fillerCount = fillerWords.reduce((count, word) => {
      return count + (text.match(new RegExp(`\\\\b${word}\\\\b`, 'g'))?.length || 0);
    }, 0);
    
    const wordCount = text.split(' ').length;
    if (fillerCount > wordCount * 0.3) { // More than 30% filler words
      errors.push({
        type: 'audio_quality',
        severity: 'medium',
        description: 'High filler word ratio may indicate poor audio quality',
        confidence: 0.8
      });
    }
    
    // Timing anomalies
    if (entry.duration && entry.duration < 500 && text.length > 100) {
      errors.push({
        type: 'timing',
        severity: 'low',
        description: 'Unusually fast speech for text length',
        confidence: 0.6
      });
    }
    
    return errors;
  }
  
  private updateFilteredEntries(): void {
    if (!this.searchQuery.trim()) {
      this.filteredEntries = this.transcriptEntries;
    } else {
      const query = this.searchQuery.toLowerCase();
      this.filteredEntries = this.transcriptEntries.filter(entry =>
        entry.text.toLowerCase().includes(query) ||
        entry.speaker.toLowerCase().includes(query) ||
        entry.speakerInfo?.name.toLowerCase().includes(query)
      );
    }
  }
  
  private updateStats(): void {
    this.totalEntries = this.transcriptEntries.length;
    this.totalErrors = this.transcriptEntries.reduce((count, entry) => {
      return count + (entry.errorFlags?.length || 0);
    }, 0);
  }
  
  public scrollToBottom(): void {
    if (this.transcriptContainer?.nativeElement) {
      setTimeout(() => {
        const element = this.transcriptContainer.nativeElement;
        element.scrollTop = element.scrollHeight;
      }, 100);
    }
  }
  
  private applyTheme(): void {
    if (this.transcriptContainer?.nativeElement) {
      const element = this.transcriptContainer.nativeElement;
      element.className = `transcript-container theme-${this.settings.theme} font-${this.settings.fontSize}`;
    }
  }
  
  private startPerformanceMonitoring(): void {
    this.updateTimer = setInterval(() => {
      this.performanceMetrics.lastUpdate = new Date();
      
      // Log performance if degraded
      if (this.performanceMetrics.averageLatency > 100) {
        console.warn(`⚠️ High transcript update latency: ${this.performanceMetrics.averageLatency}ms`);
      }
    }, 10000); // Every 10 seconds
  }
  
  private updatePerformanceMetrics(latency: number): void {
    this.performanceMetrics.totalUpdates++;
    this.performanceMetrics.averageLatency = 
      (this.performanceMetrics.averageLatency * (this.performanceMetrics.totalUpdates - 1) + latency) / 
      this.performanceMetrics.totalUpdates;
  }
  
  private disconnectWebSockets(): void {
    if (this.transcriptWS) {
      this.transcriptWS.close();
      this.transcriptWS = null;
    }
    
    if (this.errorReportingWS) {
      this.errorReportingWS.close();
      this.errorReportingWS = null;
    }
  }
  
  // Public methods for user interactions
  
  public onEntryClick(entry: TranscriptEntry): void {
    this.selectedEntry = entry;
    
    if (this.enableErrorCorrection && entry.hasErrors) {
      this.openErrorCorrectionModal(entry);
    }
  }
  
  public onSearchQueryChange(query: string): void {
    this.searchQuery = query;
    this.updateFilteredEntries();
  }
  
  public onSettingsChange(settings: Partial<TranscriptSettings>): void {
    const updatedSettings = { ...this.settings, ...settings };
    this.settingsSubject.next(updatedSettings);
  }

  // Event handler methods for template
  public onCheckboxChange(event: Event, settingName: keyof TranscriptSettings): void {
    const target = event.target as HTMLInputElement;
    this.onSettingsChange({ [settingName]: target.checked });
  }

  public onSelectChange(event: Event, settingName: keyof TranscriptSettings): void {
    const target = event.target as HTMLSelectElement;
    this.onSettingsChange({ [settingName]: target.value });
  }
  
  public reportError(entry: TranscriptEntry): void {
    if (!this.enableErrorCorrection) return;
    
    this.openErrorCorrectionModal(entry);
  }
  
  public openErrorCorrectionModal(entry: TranscriptEntry): void {
    this.currentErrorCorrection = {
      entryId: entry.id,
      originalText: entry.text,
      correctedText: entry.correctedText || entry.text,
      reason: '',
      priority: 'medium'
    };
    
    this.showErrorModal = true;
  }
  
  public submitErrorCorrection(): void {
    if (!this.currentErrorCorrection.entryId || !this.currentErrorCorrection.correctedText) {
      return;
    }
    
    const correction: ErrorCorrectionRequest = {
      entryId: this.currentErrorCorrection.entryId,
      originalText: this.currentErrorCorrection.originalText || '',
      correctedText: this.currentErrorCorrection.correctedText,
      reason: this.currentErrorCorrection.reason || 'User correction',
      priority: this.currentErrorCorrection.priority || 'medium',
      timestamp: new Date(),
      errorType: this.currentErrorCorrection.errorType as any
    };
    
    // Emit correction event
    this.correctionSubmitted.emit(correction);
    
    // Send to WebSocket if connected
    if (this.errorReportingWS && this.errorReportingWS.readyState === WebSocket.OPEN) {
      this.errorReportingWS.send(JSON.stringify({
        type: 'submit_correction',
        correction
      }));
    }
    
    // Update local entry
    this.updateTranscriptEntry(correction.entryId, {
      correctedText: correction.correctedText,
      hasErrors: false
    });
    
    this.closeErrorModal();
    
    console.log('📝 Submitted error correction:', correction);
  }
  
  public closeErrorModal(): void {
    this.showErrorModal = false;
    this.currentErrorCorrection = {};
  }
  
  public exportTranscript(format: 'txt' | 'json' | 'csv' | 'srt'): void {
    let data: any;
    
    switch (format) {
      case 'txt':
        data = this.transcriptEntries
          .map(entry => {
            const timestamp = this.settings.showTimestamps ? 
              `[${entry.timestamp.toLocaleTimeString()}] ` : '';
            const speaker = `${entry.speakerInfo?.name || entry.speaker}: `;
            const text = entry.correctedText || entry.text;
            return `${timestamp}${speaker}${text}`;
          })
          .join('\n\n');
        break;
        
      case 'json':
        data = {
          sessionId: this.sessionId,
          exportedAt: new Date().toISOString(),
          totalEntries: this.transcriptEntries.length,
          totalErrors: this.totalErrors,
          entries: this.transcriptEntries.map(entry => ({
            ...entry,
            timestamp: entry.timestamp.toISOString()
          }))
        };
        break;
        
      case 'csv':
        const csvHeaders = 'Timestamp,Speaker,Text,Confidence,Has Errors,Error Count';
        const csvRows = this.transcriptEntries.map(entry => {
          const timestamp = entry.timestamp.toISOString();
          const speaker = entry.speakerInfo?.name || entry.speaker;
          const text = `"${(entry.correctedText || entry.text).replace(/"/g, '""')}"`;
          const confidence = entry.confidence?.toFixed(2) || 'N/A';
          const hasErrors = entry.hasErrors ? 'Yes' : 'No';
          const errorCount = entry.errorFlags?.length || 0;
          return `${timestamp},${speaker},${text},${confidence},${hasErrors},${errorCount}`;
        });
        data = [csvHeaders, ...csvRows].join('\n');
        break;
        
      case 'srt':
        data = this.transcriptEntries
          .map((entry, index) => {
            const startTime = entry.timestamp;
            const endTime = new Date(startTime.getTime() + (entry.duration || 3000));
            
            const formatTime = (date: Date) => {
              const hours = String(date.getHours()).padStart(2, '0');
              const minutes = String(date.getMinutes()).padStart(2, '0');
              const seconds = String(date.getSeconds()).padStart(2, '0');
              const ms = String(date.getMilliseconds()).padStart(3, '0');
              return `${hours}:${minutes}:${seconds},${ms}`;
            };
            
            return [
              index + 1,
              `${formatTime(startTime)} --> ${formatTime(endTime)}`,
              `${entry.speakerInfo?.name || entry.speaker}: ${entry.correctedText || entry.text}`,
              ''
            ].join('\n');
          })
          .join('\n');
        break;
        
      default:
        console.error('Unsupported export format:', format);
        return;
    }
    
    this.transcriptExported.emit({ format, data });
    
    // Trigger download
    this.downloadTranscript(data, format);
  }
  
  private downloadTranscript(data: any, format: string): void {
    const fileName = `interview_transcript_${this.sessionId}_${new Date().toISOString().split('T')[0]}.${format}`;
    const blob = new Blob([typeof data === 'string' ? data : JSON.stringify(data, null, 2)], {
      type: format === 'json' ? 'application/json' : 'text/plain'
    });
    
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    
    window.URL.revokeObjectURL(url);
    
    console.log(`📥 Downloaded transcript as ${format.toUpperCase()}: ${fileName}`);
  }
  
  public clearTranscript(): void {
    if (confirm('Are you sure you want to clear the transcript? This action cannot be undone.')) {
      this.transcriptEntriesSubject.next([]);
      this.selectedEntry = null;
      console.log('🗑️ Transcript cleared');
    }
  }
  
  public onScroll(): void {
    if (this.transcriptContainer?.nativeElement) {
      const element = this.transcriptContainer.nativeElement;
      const isAtBottom = element.scrollHeight - element.scrollTop === element.clientHeight;
      this.isScrolledToBottom = Math.abs(element.scrollHeight - element.scrollTop - element.clientHeight) < 5;
    }
  }
  
  public scrollToEntry(entryId: string): void {
    const entryElement = document.getElementById(`transcript-entry-${entryId}`);
    if (entryElement) {
      entryElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }
  
  public getSpeakerColor(speaker: string): string {
    const speakerInfo = this.speakers.get(speaker);
    return speakerInfo?.color || '#757575';
  }
  
  public getSpeakerName(speaker: string): string {
    const speakerInfo = this.speakers.get(speaker);
    return speakerInfo?.name || speaker;
  }
  
  public getSpeakerAvatar(speaker: string): string {
    const speakerInfo = this.speakers.get(speaker);
    return speakerInfo?.avatar || '💬';
  }
  
  public getErrorSeverityIcon(severity: string): string {
    switch (severity) {
      case 'high': return '🔴';
      case 'medium': return '🟡';
      case 'low': return '🔵';
      default: return '⚠️';
    }
  }
  
  public formatTimestamp(timestamp: Date): string {
    return timestamp.toLocaleTimeString('en-US', {
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }
  
  public getConfidenceClass(confidence?: number): string {
    if (!confidence) return '';
    
    if (confidence >= 0.8) return 'confidence-high';
    if (confidence >= 0.6) return 'confidence-medium';
    return 'confidence-low';
  }
  
  public getPerformanceReport(): any {
    return {
      ...this.performanceMetrics,
      totalEntries: this.totalEntries,
      totalErrors: this.totalErrors,
      errorRate: this.totalErrors / Math.max(1, this.totalEntries),
      isConnected: {
        transcript: this.transcriptWS?.readyState === WebSocket.OPEN,
        errorReporting: this.errorReportingWS?.readyState === WebSocket.OPEN
      }
    };
  }
  
  // Additional methods for template support
  public showSettings = false;
  public showExportMenu = false;
  
  public trackByEntryId(index: number, entry: TranscriptEntry): string {
    return entry.id;
  }
  
  public copyEntryText(entry: TranscriptEntry): void {
    const textToCopy = entry.correctedText || entry.text;
    navigator.clipboard.writeText(textToCopy).then(() => {
      console.log('📋 Entry text copied to clipboard');
    }).catch(error => {
      console.error('Failed to copy text:', error);
    });
  }
  
  public setErrorType(errorType: string): void {
    this.currentErrorCorrection.errorType = errorType as any;
  }
}
