export const environment = {
  production: true,
  apiBaseUrl: 'https://aria-user-management-v2.onrender.com/api',
  
  // Session Service Configuration (User Management Service)
  sessionServiceBaseUrl: 'https://aria-user-management-v2.onrender.com/api/user/sessions',
  
  // AI Services Configuration - Production URLs
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
    
    // AI Avatar Service (Railway - Update with actual URLs when available)
    alexAiServiceUrl: 'https://ai-avatar-service.railway.app',
    aiAvatarServiceUrl: 'https://ai-avatar-service.railway.app',
    
    // Mozilla TTS Service (Railway)
    mozillaTtsServiceBaseUrl: 'https://mozilla-tts-service.railway.app',
    mozillaTtsServiceWsUrl: 'wss://mozilla-tts-service.railway.app/ws',
    
    // AI Avatar Configuration (Open-source)
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
      connectionTimeout: 10000
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
    jitsiDomain: 'meet.jit.si', // Free Jitsi Meet server
    enableLocalVideo: true,
    enableAudioProcessing: true,
    // Jitsi Meet configuration options
    jitsiConfig: {
      enableWelcomePage: false,
      enableCalendarIntegration: false,
      disableThirdPartyRequests: true
    }
  },
  
  // Security Configuration
  security: {
    enableCSRFProtection: true,
    allowedOrigins: ['https://aria.com', 'https://www.aria.com'],
    enableSecureCookies: true
  },
  
  // Logging Configuration
  logging: {
    enableConsoleLogging: false,
    enableRemoteLogging: true,
    logLevel: 'warn'
  }
};
