export const environment = {
  production: false,
  apiBaseUrl: 'https://aria-user-management-v2.onrender.com',
  
  // Session Service Configuration (User Management Service)
  sessionServiceBaseUrl: 'https://aria-user-management-v2.onrender.com/api/user/sessions',
  
  // AI Services Configuration - Updated for Production URLs
  aiServices: {
    // Interview Orchestrator Service (Render)
    orchestratorBaseUrl: 'https://aria-interview-orchestrator-v2.onrender.com/api/interview',
    orchestratorWsUrl: 'wss://aria-interview-orchestrator-v2.onrender.com/ws',
    
    // Speech Processing Service (Render) - Open-source STT
    speechServiceBaseUrl: 'https://aria-speech-service.onrender.com',
    speechServiceWsUrl: 'wss://aria-speech-service.onrender.com/ws',
    
    // Analytics Service (Render)
    analyticsServiceBaseUrl: 'https://aria-analytics-service.onrender.com',
    analyticsServiceWsUrl: 'wss://aria-analytics-service.onrender.com/ws',
    
    // Adaptive Engine Service (Render)
    adaptiveEngineBaseUrl: 'https://aria-adaptive-engine.onrender.com',
    adaptiveEngineWsUrl: 'wss://aria-adaptive-engine.onrender.com/ws',
    
    // AI Avatar Service (Railway - TBD)
    alexAiServiceUrl: 'https://ai-avatar-service.railway.app', // Placeholder - update with actual Railway URL
    aiAvatarServiceUrl: 'https://ai-avatar-service.railway.app',
    
    // Mozilla TTS Service (Railway - TBD)
    mozillaTtsServiceBaseUrl: 'https://mozilla-tts-service.railway.app', // Placeholder
    mozillaTtsServiceWsUrl: 'wss://mozilla-tts-service.railway.app/ws',
    
    // Voice Synthesis Service (Railway - TBD)
    voiceSynthesisBaseUrl: 'https://voice-synthesis-service.railway.app', // Placeholder
    voiceSynthesisWsUrl: 'wss://voice-synthesis-service.railway.app/ws',
    
    // Voice Isolation Service (Railway - TBD)
    voiceIsolationBaseUrl: 'https://voice-isolation-service.railway.app', // Placeholder
    voiceIsolationWsUrl: 'wss://voice-isolation-service.railway.app/ws',
    
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
    allowedOrigins: ['https://localhost:4200', 'http://localhost:4200', 'http://localhost:3000', 'https://aria-frontend-fs01.onrender.com'],
    enableSecureCookies: true
  },
  
  // Logging Configuration
  logging: {
    enableConsoleLogging: true,
    enableRemoteLogging: false,
    logLevel: 'debug'
  }
};
