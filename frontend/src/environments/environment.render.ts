export const environment = {
  production: true,
  apiBaseUrl: 'https://aria-user-management-v2.onrender.com/api',
  
  // Session Service Configuration (User Management Service)
  sessionServiceBaseUrl: 'https://aria-user-management-v2.onrender.com/api/user/sessions',
  
  // AI Services Configuration - Render Production URLs
  aiServices: {
    // Interview Orchestrator Service (Render)
    orchestratorBaseUrl: 'https://aria-interview-orchestrator-v2.onrender.com/api/interview',
    orchestratorWsUrl: 'wss://aria-interview-orchestrator-v2.onrender.com/ws',
    
    // Speech Processing Service (Render)
    speechServiceBaseUrl: 'https://aria-speech-service.onrender.com',
    speechServiceWsUrl: 'wss://aria-speech-service.onrender.com/ws',
    
    // Analytics Service (Render)
    analyticsServiceBaseUrl: 'https://aria-analytics-service.onrender.com',
    analyticsServiceWsUrl: 'wss://aria-analytics-service.onrender.com/ws',
    
    // Adaptive Engine Service (Render)
    adaptiveEngineBaseUrl: 'https://aria-adaptive-engine.onrender.com',
    adaptiveEngineWsUrl: 'wss://aria-adaptive-engine.onrender.com/ws',
    
    // AI Avatar Service (Railway - Update when Railway URLs are available)
    alexAiServiceUrl: 'https://ai-avatar-service.railway.app',
    aiAvatarServiceUrl: 'https://ai-avatar-service.railway.app',
    
    // Mozilla TTS Service (Railway - Update when Railway URLs are available)
    mozillaTtsServiceBaseUrl: 'https://mozilla-tts-service.railway.app',
    mozillaTtsServiceWsUrl: 'wss://mozilla-tts-service.railway.app/ws',
    
    // Voice Synthesis Service (Railway - Update when Railway URLs are available)
    voiceSynthesisBaseUrl: 'https://voice-synthesis-service.railway.app',
    voiceSynthesisWsUrl: 'wss://voice-synthesis-service.railway.app/ws',
    
    // Voice Isolation Service (Railway - Update when Railway URLs are available)
    voiceIsolationBaseUrl: 'https://voice-isolation-service.railway.app',
    voiceIsolationWsUrl: 'wss://voice-isolation-service.railway.app/ws',
    
    // AI Avatar Configuration (Production)
    avatarConfig: {
      enableSpeechSynthesis: true,
      speechRate: 1.0,
      speechVolume: 0.9,
      preferredVoices: ['mozilla_tacotron2', 'mozilla_fast', 'pyttsx3'],
      fallbackToSpeechAPI: true,
      enableVisualFeedback: true,
      sttEngine: 'vosk_large_en',
      ttsEngine: 'mozilla_tacotron2',
      useOpenSource: true
    },
    
    // WebSocket Configuration
    websockets: {
      reconnectAttempts: 5,
      reconnectInterval: 3000,
      heartbeatInterval: 30000,
      connectionTimeout: 15000  // Increased for production
    }
  },
  
  // Session Management Configuration
  session: {
    tokenExpiryHours: 24,
    refreshThresholdMinutes: 5,
    refreshIntervalMinutes: 15,
    maxRetryAttempts: 3,
    storageKey: 'aria_session'
  },
  
  // WebRTC Configuration
  webrtc: {
    jitsiDomain: 'meet.jit.si',
    enableLocalVideo: true,
    enableAudioProcessing: true,
    jitsiConfig: {
      enableWelcomePage: false,
      enableCalendarIntegration: false,
      disableThirdPartyRequests: true
    }
  },
  
  // Security Configuration
  security: {
    enableCSRFProtection: true,
    allowedOrigins: ['https://aria-frontend-fs01.onrender.com'],
    enableSecureCookies: true
  },
  
  // Logging Configuration
  logging: {
    enableConsoleLogging: false,
    enableRemoteLogging: true,
    logLevel: 'warn'
  }
};
