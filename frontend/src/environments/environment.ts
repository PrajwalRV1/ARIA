export const environment = {
  production: false,
  apiBaseUrl: 'https://aria-user-management-v2.onrender.com/api',
  
  // Session Service Configuration (User Management Service)
  sessionServiceBaseUrl: 'https://aria-user-management-v2.onrender.com/api/user/sessions',
  
  // AI Services Configuration
  aiServices: {
    // Interview Orchestrator Service
    orchestratorBaseUrl: 'https://localhost:8081/api',
    orchestratorWsUrl: 'wss://localhost:8081/ws',
    
    // Speech Processing Service (Open-source STT) - SSL enabled on port 8002
    speechServiceBaseUrl: 'https://localhost:8002/api',
    speechServiceWsUrl: 'wss://localhost:8002/ws',
    
    // Analytics Service - SSL enabled on port 8003
    analyticsServiceBaseUrl: 'https://localhost:8003/api',
    analyticsServiceWsUrl: 'wss://localhost:8003/ws',
    
    // Mozilla TTS Service - SSL enabled on port 8004  
    mozillaTtsServiceBaseUrl: 'https://localhost:8004/api',
    mozillaTtsServiceWsUrl: 'wss://localhost:8004/ws',
    
    // Job Description Analyzer - SSL enabled on port 8005
    jobAnalyzerServiceBaseUrl: 'https://localhost:8005/api',
    jobAnalyzerServiceWsUrl: 'wss://localhost:8005/ws',
    
    // AI Avatar Service with Alex AI integration - SSL enabled on port 8006
    alexAiServiceUrl: 'https://localhost:8006',
    aiAvatarServiceUrl: 'https://localhost:8006',
    
    // Voice Synthesis Service - SSL enabled on port 8007
    voiceSynthesisBaseUrl: 'https://localhost:8007/api',
    voiceSynthesisWsUrl: 'wss://localhost:8007/ws',
    
    // Voice Isolation Service - SSL enabled on port 8008
    voiceIsolationBaseUrl: 'https://localhost:8008/api',
    voiceIsolationWsUrl: 'wss://localhost:8008/ws',
    
    // AI Avatar Configuration (Open-source)
    avatarConfig: {
      enableSpeechSynthesis: true,
      speechRate: 1.0,
      speechVolume: 0.9,
      // Preferred female voices (browser voice names)
      preferredVoices: [
        'Microsoft Zira', // Windows female voice
        'Microsoft Hazel', // Windows UK female voice
        'Google US English Female', // Chrome/Android
        'Microsoft Sara', // Windows mobile female voice
        'Alex (Enhanced)', // macOS (Alex is male but enhanced version can be configured)
        'Samantha', // macOS female voice
        'Victoria', // macOS female voice
        'Allison', // macOS female voice
        'Ava', // macOS female voice
        'Susan', // macOS female voice
        'Vicki', // macOS female voice
        'Veena', // macOS female voice
        'Google UK English Female', // Chrome UK
        'Google US English Male' // fallback if no female available
      ],
      // Voice selection preferences
      voiceGender: 'female', // preferred gender
      voiceLanguage: 'en-US', // preferred language
      voiceFallbackLanguage: 'en', // fallback language
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
    enableCSRFProtection: false,
    allowedOrigins: ['https://localhost:4200', 'http://localhost:4200', 'http://localhost:3000', 'https://aria-frontend.onrender.com'],
    enableSecureCookies: true
  },
  
  // Logging Configuration
  logging: {
    enableConsoleLogging: true,
    enableRemoteLogging: false,
    logLevel: 'debug'
  }
};
