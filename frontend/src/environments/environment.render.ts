export const environment = {
  production: true,
  apiBaseUrl: 'https://aria-user-management-v2-uq1g.onrender.com/api/auth',
  
  // Session Service Configuration (User Management Service)
  sessionServiceBaseUrl: 'https://aria-user-management-v2-uq1g.onrender.com/api/auth/api/user/sessions',
  
  // AI Services Configuration - Render Production URLs
  aiServices: {
    // Interview Orchestrator Service (Render)
    orchestratorBaseUrl: 'https://aria-interview-orchestrator-v2-sppr.onrender.com/api/interview',
    orchestratorWsUrl: 'wss://aria-interview-orchestrator-v2-sppr.onrender.com/ws',
    
    // Speech Processing Service (Render)
    speechServiceBaseUrl: 'https://aria-speech-service-l4cl.onrender.com',
    speechServiceWsUrl: 'wss://aria-speech-service-l4cl.onrender.com/ws',
    
    // Analytics Service (Render)
    analyticsServiceBaseUrl: 'https://aria-analytics-service-betb.onrender.com',
    analyticsServiceWsUrl: 'wss://aria-analytics-service-betb.onrender.com/ws',
    
    // Adaptive Engine Service (Render)
    adaptiveEngineBaseUrl: 'https://aria-adaptive-engine-ntsr.onrender.com',
    adaptiveEngineWsUrl: 'wss://aria-adaptive-engine-ntsr.onrender.com/ws',
    
    // AI Avatar Service (Railway - Update when Railway URLs are available)
    alexAiServiceUrl: 'https://ai-avatar-service-production.up.railway.app',
    aiAvatarServiceUrl: 'https://ai-avatar-service-production.up.railway.app',
    
    // Mozilla TTS Service (Railway - Update when Railway URLs are available)
    mozillaTtsServiceBaseUrl: 'https://mozilla-tts-service-production.up.railway.app',
    mozillaTtsServiceWsUrl: 'wss://mozilla-tts-service-production.up.railway.app/ws',
    
    // Voice Synthesis Service (Railway - Update when Railway URLs are available)
    voiceSynthesisBaseUrl: 'https://voice-synthesis-service-production.up.railway.app',
    voiceSynthesisWsUrl: 'wss://voice-synthesis-service-production.up.railway.app/ws',
    
    // Voice Isolation Service (Railway - Update when Railway URLs are available)
    voiceIsolationBaseUrl: 'https://voice-isolation-service-production.up.railway.app',
    voiceIsolationWsUrl: 'wss://voice-isolation-service-production.up.railway.app/ws',
    
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
    allowedOrigins: ['https://aria-frontend-tc4z.onrender.com'],
    enableSecureCookies: true
  },
  
  // Logging Configuration
  logging: {
    enableConsoleLogging: false,
    enableRemoteLogging: true,
    logLevel: 'warn'
  }
};
