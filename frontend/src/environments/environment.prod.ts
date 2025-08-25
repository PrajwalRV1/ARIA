export const environment = {
  production: true,
  apiBaseUrl: 'https://api.aria.com',
  
  // Session Service Configuration (User Management Service)
  sessionServiceBaseUrl: 'https://api.aria.com/user/sessions',
  
  // AI Services Configuration
  aiServices: {
    // Interview Orchestrator Service
    orchestratorBaseUrl: 'https://ai-orchestrator.aria.com/api',
    orchestratorWsUrl: 'wss://ai-orchestrator.aria.com/ws',
    
    // Speech Processing Service (Open-source STT)
    speechServiceBaseUrl: 'https://speech.aria.com/api',
    speechServiceWsUrl: 'wss://speech.aria.com/ws',
    
    // Voice Synthesis Service (Mozilla TTS)
    voiceSynthesisBaseUrl: 'https://voice.aria.com/api',
    voiceSynthesisWsUrl: 'wss://voice.aria.com/ws',
    
    // Analytics Service
    analyticsServiceBaseUrl: 'https://analytics.aria.com/api',
    analyticsServiceWsUrl: 'wss://analytics.aria.com/ws',
    
    // Unified AI Avatar Service with Alex AI integration
    alexAiServiceUrl: 'https://alex.aria.com',  // Production unified AI Avatar Service
    aiAvatarServiceUrl: 'https://alex.aria.com', // Same service handles both ARIA Avatar + Alex AI
    
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
