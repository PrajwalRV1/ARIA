#!/usr/bin/env python3
"""
ARIA AI Avatar Service - Simplified for Railway Deployment
Lightweight version without PyAudio dependency
"""

import asyncio
import json
import logging
import os
import time
import uuid
from contextlib import asynccontextmanager
from typing import Dict, List, Optional, Any
from dataclasses import dataclass
from datetime import datetime, timedelta
import aiohttp
import websockets
from fastapi import FastAPI, WebSocket, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import redis.asyncio as aioredis

# Text-to-Speech imports (lightweight)
try:
    import pyttsx3
    from gtts import gTTS
    TTS_AVAILABLE = True
except ImportError:
    TTS_AVAILABLE = False
    logging.warning("TTS libraries not available")

# OpenAI for advanced evaluation (optional)
try:
    import openai
    OPENAI_AVAILABLE = True
except ImportError:
    OPENAI_AVAILABLE = False
    logging.warning("OpenAI not available")

# Audio processing (simplified - removed PyAudio)
AUDIO_AVAILABLE = False
logging.info("Audio processing disabled for containerized deployment")

import threading
import queue

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ==================== CONFIGURATION ====================

class Settings:
    """AI Avatar Service Configuration - Simplified"""
    
    # Service Configuration
    SERVICE_NAME = "ARIA AI Avatar Service (Simplified)"
    SERVICE_VERSION = "2.0.0"
    HOST = "0.0.0.0"
    PORT = int(os.getenv('PORT', 8006))
    
    # External Service URLs
    INTERVIEW_ORCHESTRATOR_URL = os.getenv("INTERVIEW_ORCHESTRATOR_URL", "http://localhost:8081")
    SPEECH_SERVICE_URL = os.getenv("SPEECH_SERVICE_URL", "http://localhost:8002")
    ANALYTICS_SERVICE_URL = os.getenv("ANALYTICS_SERVICE_URL", "http://localhost:8003")
    ADAPTIVE_ENGINE_URL = os.getenv("ADAPTIVE_ENGINE_URL", "http://localhost:8001")
    
    # AI Avatar Configuration
    AVATAR_NAME = "Alex - ARIA AI Interviewer"
    AVATAR_EMAIL = "alex@aria-ai.com"
    VOICE_LANGUAGE = "en-US"
    VOICE_SPEED = 0.9
    VOICE_PITCH = 0.8
    
    # Redis Configuration
    REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/2")
    
    # Interview Flow Configuration
    INTERVIEW_STAGES = [
        "introduction_setup",
        "technical_theory",
        "coding_challenges",
        "cultural_behavioral",
        "candidate_qa",
        "interview_conclusion"
    ]

settings = Settings()

# ==================== DATA MODELS ====================

class InterviewSessionRequest(BaseModel):
    session_id: str
    candidate_profile: Dict[str, Any]
    job_role: str
    experience_level: int
    required_technologies: List[str]
    meeting_link: str

class AIAvatarResponse(BaseModel):
    session_id: str
    status: str
    message: str
    metadata: Optional[Dict[str, Any]] = None

class QuestionResponse(BaseModel):
    question_id: str
    question_text: str
    question_type: str
    expected_duration: int
    metadata: Dict[str, Any]

class SpeechSynthesisRequest(BaseModel):
    text: str
    voice_config: Optional[Dict[str, Any]] = None

@dataclass
class AIAvatarState:
    """Represents the current state of an AI avatar"""
    session_id: str
    status: str
    current_stage: str
    current_question_id: Optional[str]
    candidate_theta: float
    questions_asked: int
    start_time: datetime
    last_interaction: datetime
    metadata: Dict[str, Any]

# ==================== SIMPLIFIED AI AVATAR ====================

class SimpleAIAvatar:
    """Simplified AI Avatar without heavy audio processing"""
    
    def __init__(self, session_id: str, session_request: InterviewSessionRequest):
        self.session_id = session_id
        self.session_request = session_request
        self.state = AIAvatarState(
            session_id=session_id,
            status="initializing",
            current_stage="introduction_setup",
            current_question_id=None,
            candidate_theta=0.0,
            questions_asked=0,
            start_time=datetime.now(),
            last_interaction=datetime.now(),
            metadata={}
        )
        self.active_websockets: List[WebSocket] = []
        
    async def initialize(self):
        """Initialize the avatar"""
        try:
            self.state.status = "ready"
            logger.info(f"✅ AI Avatar initialized for session {self.session_id}")
            return True
        except Exception as e:
            logger.error(f"❌ Failed to initialize avatar: {e}")
            self.state.status = "error"
            return False
    
    async def start_interview(self):
        """Start the interview process"""
        try:
            self.state.status = "active"
            self.state.current_stage = "introduction_setup"
            
            welcome_message = f"Hello! I'm Alex, your AI interviewer for today. Welcome to your {self.session_request.job_role} interview. I'm excited to learn more about your background and technical expertise. Shall we begin?"
            
            await self.speak(welcome_message)
            
            logger.info(f"✅ Interview started for session {self.session_id}")
            return AIAvatarResponse(
                session_id=self.session_id,
                status="success",
                message="Interview started successfully"
            )
        except Exception as e:
            logger.error(f"❌ Failed to start interview: {e}")
            return AIAvatarResponse(
                session_id=self.session_id,
                status="error",
                message=str(e)
            )
    
    async def speak(self, text: str):
        """Generate and broadcast speech"""
        try:
            # Broadcast to connected WebSockets
            message = {
                'type': 'avatar_speech',
                'session_id': self.session_id,
                'text': text,
                'timestamp': datetime.now().isoformat()
            }
            
            await self.broadcast_to_websockets(message)
            
            logger.info(f"🔊 Avatar speaking: {text[:50]}...")
        except Exception as e:
            logger.error(f"❌ Speech synthesis failed: {e}")
    
    async def broadcast_to_websockets(self, message: dict):
        """Broadcast message to all connected WebSockets"""
        if not self.active_websockets:
            return
        
        dead_connections = []
        for websocket in self.active_websockets:
            try:
                await websocket.send_json(message)
            except Exception as e:
                logger.error(f"❌ WebSocket send failed: {e}")
                dead_connections.append(websocket)
        
        # Remove dead connections
        for dead_websocket in dead_connections:
            if dead_websocket in self.active_websockets:
                self.active_websockets.remove(dead_websocket)
    
    async def add_websocket(self, websocket: WebSocket):
        """Add a WebSocket connection"""
        self.active_websockets.append(websocket)
        logger.info(f"✅ WebSocket connected for session {self.session_id}")
    
    async def remove_websocket(self, websocket: WebSocket):
        """Remove a WebSocket connection"""
        if websocket in self.active_websockets:
            self.active_websockets.remove(websocket)
        logger.info(f"❌ WebSocket disconnected for session {self.session_id}")

# ==================== AI AVATAR MANAGER ====================

class AIAvatarManager:
    """Manages AI Avatar instances and their lifecycle"""
    
    def __init__(self):
        self.active_avatars: Dict[str, SimpleAIAvatar] = {}
        self.redis: Optional[aioredis.Redis] = None
        
    async def initialize(self):
        """Initialize the avatar manager"""
        try:
            self.redis = aioredis.from_url(settings.REDIS_URL)
            logger.info("✅ AI Avatar Manager initialized")
        except Exception as e:
            logger.warning(f"⚠️ Redis connection failed: {e}")
            # Continue without Redis
    
    async def create_avatar(self, session_request: InterviewSessionRequest) -> AIAvatarResponse:
        """Create and initialize a new AI avatar"""
        try:
            session_id = session_request.session_id
            
            if session_id in self.active_avatars:
                return AIAvatarResponse(
                    session_id=session_id,
                    status="error",
                    message="Avatar already exists for this session"
                )
            
            # Create new avatar
            avatar = SimpleAIAvatar(session_id, session_request)
            success = await avatar.initialize()
            
            if success:
                self.active_avatars[session_id] = avatar
                return AIAvatarResponse(
                    session_id=session_id,
                    status="success",
                    message="Avatar created successfully"
                )
            else:
                return AIAvatarResponse(
                    session_id=session_id,
                    status="error",
                    message="Failed to initialize avatar"
                )
        
        except Exception as e:
            logger.error(f"❌ Failed to create avatar: {e}")
            return AIAvatarResponse(
                session_id=session_id,
                status="error",
                message=str(e)
            )
    
    async def get_avatar(self, session_id: str) -> Optional[SimpleAIAvatar]:
        """Get an avatar by session ID"""
        return self.active_avatars.get(session_id)
    
    async def remove_avatar(self, session_id: str):
        """Remove an avatar"""
        if session_id in self.active_avatars:
            del self.active_avatars[session_id]
            logger.info(f"✅ Avatar removed for session {session_id}")

# ==================== FASTAPI APPLICATION ====================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize and cleanup application lifecycle"""
    # Startup
    logger.info("🚀 Starting ARIA AI Avatar Service (Simplified)...")
    
    app.state.avatar_manager = AIAvatarManager()
    await app.state.avatar_manager.initialize()
    
    yield
    
    # Shutdown
    logger.info("🛑 Shutting down ARIA AI Avatar Service...")

app = FastAPI(
    title="ARIA AI Avatar Service (Simplified)",
    description="Lightweight AI Avatar service for ARIA Interview Platform",
    version="2.0.0",
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==================== API ENDPOINTS ====================

@app.get("/")
async def root():
    """Health check endpoint"""
    return {
        "service": settings.SERVICE_NAME,
        "version": settings.SERVICE_VERSION,
        "status": "healthy",
        "features": {
            "tts_available": TTS_AVAILABLE,
            "openai_available": OPENAI_AVAILABLE,
            "audio_available": AUDIO_AVAILABLE
        },
        "active_avatars": len(app.state.avatar_manager.active_avatars),
        "timestamp": datetime.now().isoformat()
    }

@app.post("/avatar/create", response_model=AIAvatarResponse)
async def create_avatar(session_request: InterviewSessionRequest):
    """Create a new AI avatar for an interview session"""
    return await app.state.avatar_manager.create_avatar(session_request)

@app.post("/avatar/{session_id}/start", response_model=AIAvatarResponse)
async def start_interview(session_id: str):
    """Start the interview for a specific avatar"""
    avatar = await app.state.avatar_manager.get_avatar(session_id)
    if not avatar:
        raise HTTPException(status_code=404, detail="Avatar not found")
    
    return await avatar.start_interview()

@app.post("/avatar/{session_id}/speak")
async def avatar_speak(session_id: str, speech_request: SpeechSynthesisRequest):
    """Make the avatar speak specific text"""
    avatar = await app.state.avatar_manager.get_avatar(session_id)
    if not avatar:
        raise HTTPException(status_code=404, detail="Avatar not found")
    
    await avatar.speak(speech_request.text)
    return {"status": "success", "message": "Speech synthesized"}

@app.get("/avatar/{session_id}/status")
async def get_avatar_status(session_id: str):
    """Get the current status of an avatar"""
    avatar = await app.state.avatar_manager.get_avatar(session_id)
    if not avatar:
        raise HTTPException(status_code=404, detail="Avatar not found")
    
    return {
        "session_id": session_id,
        "status": avatar.state.status,
        "current_stage": avatar.state.current_stage,
        "questions_asked": avatar.state.questions_asked,
        "start_time": avatar.state.start_time.isoformat(),
        "last_interaction": avatar.state.last_interaction.isoformat(),
        "connected_clients": len(avatar.active_websockets)
    }

@app.delete("/avatar/{session_id}")
async def remove_avatar(session_id: str):
    """Remove an avatar and clean up resources"""
    await app.state.avatar_manager.remove_avatar(session_id)
    return {"status": "success", "message": "Avatar removed"}

@app.websocket("/ws/avatar/{session_id}")
async def websocket_avatar(websocket: WebSocket, session_id: str):
    """WebSocket endpoint for real-time avatar communication"""
    await websocket.accept()
    
    avatar = await app.state.avatar_manager.get_avatar(session_id)
    if not avatar:
        await websocket.send_json({
            'type': 'error',
            'message': 'Avatar not found'
        })
        await websocket.close()
        return
    
    await avatar.add_websocket(websocket)
    
    try:
        # Send welcome message
        await websocket.send_json({
            'type': 'connected',
            'session_id': session_id,
            'message': 'Connected to AI Avatar'
        })
        
        # Listen for messages
        while True:
            data = await websocket.receive_json()
            
            # Handle different message types
            if data.get('type') == 'candidate_response':
                # Process candidate response
                response_text = data.get('text', '')
                logger.info(f"📝 Candidate response: {response_text[:100]}...")
                
                # Echo back confirmation (simplified)
                await websocket.send_json({
                    'type': 'response_received',
                    'message': 'Thank you for your response. Let me process that...'
                })
    
    except Exception as e:
        logger.error(f"❌ WebSocket error: {e}")
    finally:
        await avatar.remove_websocket(websocket)

@app.get("/health")
async def health_check():
    """Detailed health check"""
    try:
        return {
            "status": "healthy",
            "components": {
                "avatar_manager": "healthy",
                "tts_engine": "available" if TTS_AVAILABLE else "unavailable",
                "openai": "available" if OPENAI_AVAILABLE else "unavailable",
                "redis": "connected" if app.state.avatar_manager.redis else "disconnected"
            },
            "metrics": {
                "active_avatars": len(app.state.avatar_manager.active_avatars),
                "total_websockets": sum(len(avatar.active_websockets) for avatar in app.state.avatar_manager.active_avatars.values())
            },
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        return {
            "status": "unhealthy",
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=settings.HOST, port=settings.PORT, log_level="info")
