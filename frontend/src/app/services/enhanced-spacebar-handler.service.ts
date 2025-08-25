import { Injectable, EventEmitter } from '@angular/core';
import { fromEvent, Subject, BehaviorSubject, Observable, timer } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, takeUntil, tap } from 'rxjs/operators';

export enum ResponseState {
  READY = 'ready',
  LISTENING = 'listening',
  PROCESSING = 'processing',
  COMPLETED = 'completed',
  ERROR = 'error'
}

export interface SpaceBarTriggerEvent {
  timestamp: Date;
  responseState: ResponseState;
  triggerId: string;
  duration: number;
}

export interface UIFlowState {
  isResponseActive: boolean;
  canTriggerEnd: boolean;
  isProcessing: boolean;
  currentTrigger: string | null;
  lastTriggerTime: Date | null;
}

@Injectable({
  providedIn: 'root'
})
export class EnhancedSpaceBarHandlerService {
  
  // Event emitters for UI coordination
  public readonly spaceBarTriggered = new EventEmitter<SpaceBarTriggerEvent>();
  public readonly responseStateChanged = new EventEmitter<ResponseState>();
  public readonly uiFlowStateChanged = new EventEmitter<UIFlowState>();
  
  // State management
  private readonly responseStateSubject = new BehaviorSubject<ResponseState>(ResponseState.READY);
  private readonly uiFlowStateSubject = new BehaviorSubject<UIFlowState>({
    isResponseActive: false,
    canTriggerEnd: false,
    isProcessing: false,
    currentTrigger: null,
    lastTriggerTime: null
  });
  
  // Configuration
  private readonly DOUBLE_TRIGGER_PREVENTION_MS = 1000;
  private readonly DEBOUNCE_DELAY_MS = 300;
  private readonly PROCESSING_TIMEOUT_MS = 10000;
  
  // Internal state
  private isActive = false;
  private currentSessionId: string | null = null;
  private destroy$ = new Subject<void>();
  private processingTimer: any = null;
  
  // Response timing tracking
  private responseStartTime: Date | null = null;
  private lastTriggerTime: Date | null = null;
  
  constructor() {
    this.initializeSpaceBarDetection();
  }
  
  /**
   * Initialize enhanced space bar detection with comprehensive flow control
   */
  private initializeSpaceBarDetection(): void {
    fromEvent<KeyboardEvent>(document, 'keydown')
      .pipe(
        filter(event => event.code === 'Space'),
        filter(() => this.canProcessSpaceBar()),
        debounceTime(this.DEBOUNCE_DELAY_MS),
        distinctUntilChanged((prev, curr) => 
          Math.abs(curr.timeStamp - prev.timeStamp) < this.DEBOUNCE_DELAY_MS
        ),
        tap(event => event.preventDefault()),
        takeUntil(this.destroy$)
      )
      .subscribe(event => {
        this.handleSpaceBarPress(event);
      });
  }
  
  /**
   * Activate space bar handler for a specific interview session
   */
  public activateForSession(sessionId: string): void {
    console.log(`🎯 Activating enhanced space bar handler for session: ${sessionId}`);
    
    this.currentSessionId = sessionId;
    this.isActive = true;
    
    // Reset state for new session
    this.updateResponseState(ResponseState.READY);
    this.resetUIFlowState();
    
    console.log(`✅ Space bar handler activated and ready`);
  }
  
  /**
   * Deactivate space bar handler
   */
  public deactivate(): void {
    console.log(`🛑 Deactivating space bar handler`);
    
    this.isActive = false;
    this.currentSessionId = null;
    this.responseStartTime = null;
    this.lastTriggerTime = null;
    
    // Clear any active timers
    if (this.processingTimer) {
      clearTimeout(this.processingTimer);
      this.processingTimer = null;
    }
    
    this.resetUIFlowState();
  }
  
  /**
   * Start response capture phase
   */
  public startResponseCapture(): void {
    console.log(`🎤 Starting response capture phase`);
    
    this.responseStartTime = new Date();
    this.updateResponseState(ResponseState.LISTENING);
    
    const uiState = this.uiFlowStateSubject.value;
    this.updateUIFlowState({
      ...uiState,
      isResponseActive: true,
      canTriggerEnd: true,
      isProcessing: false,
      currentTrigger: null
    });
  }
  
  /**
   * Complete response processing
   */
  public completeResponseProcessing(): void {
    console.log(`✅ Completing response processing`);
    
    this.updateResponseState(ResponseState.COMPLETED);
    
    const uiState = this.uiFlowStateSubject.value;
    this.updateUIFlowState({
      ...uiState,
      isResponseActive: false,
      canTriggerEnd: false,
      isProcessing: false,
      currentTrigger: null
    });
    
    // Clear processing timer
    if (this.processingTimer) {
      clearTimeout(this.processingTimer);
      this.processingTimer = null;
    }
  }
  
  /**
   * Handle space bar press with comprehensive flow control
   */
  private handleSpaceBarPress(event: KeyboardEvent): void {
    const now = new Date();
    const triggerId = `trigger_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    console.log(`⌨️ Space bar pressed (ID: ${triggerId})`);
    
    // Prevent double triggers
    if (this.isWithinDoubleTriggerWindow(now)) {
      console.log(`⚠️ Space bar press ignored - within double trigger window`);
      return;
    }
    
    // Calculate response duration
    const duration = this.responseStartTime ? 
      now.getTime() - this.responseStartTime.getTime() : 0;
    
    // Update state immediately
    this.updateResponseState(ResponseState.PROCESSING);
    this.lastTriggerTime = now;
    
    const uiState = this.uiFlowStateSubject.value;
    this.updateUIFlowState({
      ...uiState,
      isResponseActive: false,
      canTriggerEnd: false,
      isProcessing: true,
      currentTrigger: triggerId,
      lastTriggerTime: now
    });
    
    // Create trigger event
    const triggerEvent: SpaceBarTriggerEvent = {
      timestamp: now,
      responseState: ResponseState.PROCESSING,
      triggerId,
      duration
    };
    
    // Emit trigger event
    this.spaceBarTriggered.emit(triggerEvent);
    
    // Set processing timeout
    this.startProcessingTimeout(triggerId);
    
    console.log(`📤 Space bar trigger event emitted:`, triggerEvent);
  }
  
  /**
   * Check if space bar can be processed
   */
  private canProcessSpaceBar(): boolean {
    if (!this.isActive || !this.currentSessionId) {
      return false;
    }
    
    const currentState = this.responseStateSubject.value;
    const uiState = this.uiFlowStateSubject.value;
    
    // Can only trigger during listening state and when UI allows
    return currentState === ResponseState.LISTENING && 
           uiState.canTriggerEnd && 
           !uiState.isProcessing &&
           !this.isTypingInActiveElement();
  }
  
  /**
   * Check if user is typing in an input element
   */
  private isTypingInActiveElement(): boolean {
    const activeElement = document.activeElement;
    
    if (!activeElement) {
      return false;
    }
    
    const tagName = activeElement.tagName.toLowerCase();
    const inputTypes = ['input', 'textarea', 'select'];
    
    // Check for contentEditable elements
    if (activeElement.getAttribute('contenteditable') === 'true') {
      return true;
    }
    
    // Check for Monaco editor
    if (activeElement.classList.contains('monaco-editor') || 
        activeElement.closest('.monaco-editor')) {
      return true;
    }
    
    return inputTypes.includes(tagName);
  }
  
  /**
   * Check if current time is within double trigger prevention window
   */
  private isWithinDoubleTriggerWindow(currentTime: Date): boolean {
    if (!this.lastTriggerTime) {
      return false;
    }
    
    const timeSinceLastTrigger = currentTime.getTime() - this.lastTriggerTime.getTime();
    return timeSinceLastTrigger < this.DOUBLE_TRIGGER_PREVENTION_MS;
  }
  
  /**
   * Start processing timeout to handle stuck processing state
   */
  private startProcessingTimeout(triggerId: string): void {
    this.processingTimer = setTimeout(() => {
      console.log(`⏰ Processing timeout for trigger: ${triggerId}`);
      
      const uiState = this.uiFlowStateSubject.value;
      if (uiState.currentTrigger === triggerId) {
        this.updateResponseState(ResponseState.ERROR);
        this.updateUIFlowState({
          ...uiState,
          isProcessing: false,
          currentTrigger: null,
          canTriggerEnd: false
        });
        
        console.log(`❌ Space bar trigger processing timed out`);
      }
    }, this.PROCESSING_TIMEOUT_MS);
  }
  
  /**
   * Update response state and emit change
   */
  private updateResponseState(newState: ResponseState): void {
    const currentState = this.responseStateSubject.value;
    if (currentState !== newState) {
      this.responseStateSubject.next(newState);
      this.responseStateChanged.emit(newState);
      
      console.log(`🔄 Response state changed: ${currentState} → ${newState}`);
    }
  }
  
  /**
   * Update UI flow state and emit change
   */
  private updateUIFlowState(newState: UIFlowState): void {
    this.uiFlowStateSubject.next(newState);
    this.uiFlowStateChanged.emit(newState);
  }
  
  /**
   * Reset UI flow state to initial values
   */
  private resetUIFlowState(): void {
    const resetState: UIFlowState = {
      isResponseActive: false,
      canTriggerEnd: false,
      isProcessing: false,
      currentTrigger: null,
      lastTriggerTime: null
    };
    
    this.updateUIFlowState(resetState);
  }
  
  /**
   * Get current response state as observable
   */
  public getResponseState$(): Observable<ResponseState> {
    return this.responseStateSubject.asObservable();
  }
  
  /**
   * Get current UI flow state as observable
   */
  public getUIFlowState$(): Observable<UIFlowState> {
    return this.uiFlowStateSubject.asObservable();
  }
  
  /**
   * Get current response state value
   */
  public getCurrentResponseState(): ResponseState {
    return this.responseStateSubject.value;
  }
  
  /**
   * Get current UI flow state value
   */
  public getCurrentUIFlowState(): UIFlowState {
    return this.uiFlowStateSubject.value;
  }
  
  /**
   * Force end response (for emergency situations)
   */
  public forceEndResponse(reason: string = 'manual'): void {
    console.log(`🚨 Force ending response: ${reason}`);
    
    const triggerId = `force_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const duration = this.responseStartTime ? 
      Date.now() - this.responseStartTime.getTime() : 0;
    
    const triggerEvent: SpaceBarTriggerEvent = {
      timestamp: new Date(),
      responseState: ResponseState.PROCESSING,
      triggerId,
      duration
    };
    
    this.spaceBarTriggered.emit(triggerEvent);
  }
  
  /**
   * Get response timing information
   */
  public getResponseTiming(): { startTime: Date | null, duration: number } {
    return {
      startTime: this.responseStartTime,
      duration: this.responseStartTime ? Date.now() - this.responseStartTime.getTime() : 0
    };
  }
  
  /**
   * Check if handler is currently active
   */
  public isActiveForSession(): boolean {
    return this.isActive && this.currentSessionId !== null;
  }
  
  /**
   * Cleanup resources
   */
  public ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    
    if (this.processingTimer) {
      clearTimeout(this.processingTimer);
    }
  }
}
