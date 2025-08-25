#!/usr/bin/env python3
"""
Voice Isolation Service for ARIA Interview Platform (Simplified)
Lightweight service optimized for Railway deployment
"""

import asyncio
import json
import logging
import time
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Dict, List, Optional, Any, Tuple
from dataclasses import dataclass, field
from enum import Enum

# Basic imports only
import numpy as np
from fastapi import FastAPI, WebSocket, HTTPException, BackgroundTasks, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import websockets
import aiohttp

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ==================== ENUMS AND DATA CLASSES ====================

class ParticipantType(Enum):
    CANDIDATE = "candidate"
    RECRUITER = "recruiter"
    AI_AVATAR = "ai_avatar"

class AudioChannelState(Enum):
    ACTIVE = "active"
    MUTED = "muted"
    PAUSED = "paused"
    ISOLATED = "isolated"

@dataclass
class AudioConfig:
    """Audio configuration for a participant"""
    sample_rate: int = 44100
    channels: int = 1
    chunk_size: int = 1024
    enable_aec: bool = True
    enable_noise_suppression: bool = True
    enable_gain_control: bool = True
    volume_level: float = 1.0

@dataclass
class ParticipantChannel:
    """Represents an audio channel for a participant"""
    participant_id: str
    participant_type: ParticipantType
    session_id: str
    config: AudioConfig
    state: AudioChannelState = AudioChannelState.ACTIVE
    audio_buffer: List[bytes] = field(default_factory=list)
    last_activity: datetime = field(default_factory=datetime.now)

@dataclass
class IsolationSession:
    """Manages voice isolation for an interview session"""
    session_id: str
    channels: Dict[str, ParticipantChannel] = field(default_factory=dict)
    is_active: bool = True
    created_at: datetime = field(default_factory=datetime.now)

# ==================== SIMPLIFIED PROCESSING CLASSES ====================

class BasicEchoCanceller:
    """Simplified echo cancellation for basic functionality"""
    
    def __init__(self):
        self.enabled = True
        self.suppress_factor = 0.5
        
    def process(self, audio_data: bytes, reference_data: bytes = None) -> bytes:
        """Basic echo suppression without heavy processing"""
        try:
            if not self.enabled or not reference_data:
                return audio_data
            
            # Simple volume reduction when reference signal is present
            # This is a placeholder for actual AEC which requires heavy libraries
            audio_array = np.frombuffer(audio_data, dtype=np.int16)
            suppressed = audio_array * self.suppress_factor
            return suppressed.astype(np.int16).tobytes()
            
        except Exception as e:
            logger.error(f"Error in echo cancellation: {e}")
            return audio_data

class BasicNoiseSupressor:
    """Simplified noise suppression"""
    
    def __init__(self):
        self.enabled = True
        self.noise_floor = 0.1
        
    def suppress_noise(self, audio_data: bytes) -> bytes:
        """Basic noise gate functionality"""
        try:
            if not self.enabled:
                return audio_data
            
            # Simple noise gate based on amplitude
            audio_array = np.frombuffer(audio_data, dtype=np.int16)
            normalized = audio_array / 32767.0
            
            # Apply noise gate
            mask = np.abs(normalized) > self.noise_floor
            gated = normalized * mask
            
            return (gated * 32767).astype(np.int16).tobytes()
            
        except Exception as e:
            logger.error(f"Error in noise suppression: {e}")
            return audio_data

# ==================== CORE SERVICE CLASSES ====================

class AudioRouter:
    """Manages audio routing between participants"""
    
    def __init__(self):
        self.sessions: Dict[str, IsolationSession] = {}
        self.echo_canceller = BasicEchoCanceller()
        self.noise_suppressor = BasicNoiseSupressor()
        
    def create_session(self, session_id: str) -> IsolationSession:
        """Create a new isolation session"""
        session = IsolationSession(session_id=session_id)
        self.sessions[session_id] = session
        logger.info(f"Created isolation session: {session_id}")
        return session
    
    def add_participant(self, session_id: str, participant_id: str, 
                       participant_type: ParticipantType, config: AudioConfig) -> bool:
        """Add a participant to a session"""
        if session_id not in self.sessions:
            self.create_session(session_id)
        
        session = self.sessions[session_id]
        channel = ParticipantChannel(
            participant_id=participant_id,
            participant_type=participant_type,
            session_id=session_id,
            config=config
        )
        
        session.channels[participant_id] = channel
        logger.info(f"Added participant {participant_id} to session {session_id}")
        return True
    
    def process_audio(self, session_id: str, participant_id: str, 
                     audio_data: bytes) -> Dict[str, bytes]:
        """Process and route audio to appropriate participants"""
        try:
            if session_id not in self.sessions:
                return {}
            
            session = self.sessions[session_id]
            if participant_id not in session.channels:
                return {}
            
            # Apply basic processing
            processed_audio = self.noise_suppressor.suppress_noise(audio_data)
            processed_audio = self.echo_canceller.process(processed_audio)
            
            # Route to other participants (excluding sender)
            routed_audio = {}
            for channel_id, channel in session.channels.items():
                if channel_id != participant_id and channel.state == AudioChannelState.ACTIVE:
                    routed_audio[channel_id] = processed_audio
            
            return routed_audio
            
        except Exception as e:
            logger.error(f"Error processing audio: {e}")
            return {}
    
    def remove_participant(self, session_id: str, participant_id: str):
        """Remove a participant from a session"""
        if session_id in self.sessions:
            session = self.sessions[session_id]
            if participant_id in session.channels:
                del session.channels[participant_id]
                logger.info(f"Removed participant {participant_id} from session {session_id}")
                
            # Clean up empty sessions
            if not session.channels:
                del self.sessions[session_id]
                logger.info(f"Cleaned up empty session: {session_id}")

class ConnectionManager:
    """Manages WebSocket connections for voice isolation"""
    
    def __init__(self):
        self.active_connections: Dict[str, Dict[str, WebSocket]] = {}
        
    async def connect(self, websocket: WebSocket, session_id: str, participant_id: str):
        await websocket.accept()
        if session_id not in self.active_connections:
            self.active_connections[session_id] = {}
        self.active_connections[session_id][participant_id] = websocket
        logger.info(f"Voice isolation WebSocket connected: {session_id}/{participant_id}")
    
    def disconnect(self, session_id: str, participant_id: str):
        if session_id in self.active_connections:
            if participant_id in self.active_connections[session_id]:
                del self.active_connections[session_id][participant_id]
            if not self.active_connections[session_id]:
                del self.active_connections[session_id]
        logger.info(f"Voice isolation WebSocket disconnected: {session_id}/{participant_id}")
    
    async def send_audio(self, session_id: str, participant_id: str, audio_data: bytes):
        """Send audio data to a specific participant"""
        try:
            if (session_id in self.active_connections and 
                participant_id in self.active_connections[session_id]):
                
                websocket = self.active_connections[session_id][participant_id]
                await websocket.send_bytes(audio_data)
                
        except Exception as e:
            logger.error(f"Error sending audio to {participant_id}: {e}")

# ==================== FASTAPI APPLICATION ====================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize services on startup"""
    logger.info("Starting Voice Isolation Service (Simplified)...")
    
    # Initialize services
    app.state.audio_router = AudioRouter()
    app.state.connection_manager = ConnectionManager()
    
    yield
    
    # Cleanup
    logger.info("Shutting down Voice Isolation Service...")

app = FastAPI(
    title="ARIA Voice Isolation Service (Simplified)",
    description="Lightweight voice isolation service for preventing audio feedback",
    version="1.0.0",
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

# ==================== PYDANTIC MODELS ====================

class ParticipantRequest(BaseModel):
    session_id: str
    participant_id: str
    participant_type: str  # candidate, recruiter, ai_avatar
    audio_config: Optional[Dict] = None

class AudioProcessingRequest(BaseModel):
    session_id: str
    participant_id: str
    audio_data: str  # Base64 encoded audio

class IsolationStatus(BaseModel):
    session_id: str
    participant_count: int
    active_channels: List[str]
    processing_enabled: bool

# ==================== API ENDPOINTS ====================

@app.get("/")
async def root():
    """Health check endpoint"""
    return {
        "service": "ARIA Voice Isolation Service (Simplified)",
        "status": "healthy",
        "version": "1.0.0",
        "features": {
            "echo_cancellation": "basic",
            "noise_suppression": "basic",
            "audio_routing": "enabled",
            "websocket_support": "enabled"
        },
        "timestamp": datetime.now().isoformat()
    }

@app.post("/session/{session_id}/join")
async def join_session(session_id: str, request: ParticipantRequest):
    """Add a participant to an isolation session"""
    try:
        participant_type = ParticipantType(request.participant_type)
        config = AudioConfig(**(request.audio_config or {}))
        
        success = app.state.audio_router.add_participant(
            session_id, request.participant_id, participant_type, config
        )
        
        if success:
            return {
                "status": "joined",
                "session_id": session_id,
                "participant_id": request.participant_id,
                "message": "Successfully joined isolation session"
            }
        else:
            raise HTTPException(status_code=400, detail="Failed to join session")
            
    except ValueError as e:
        raise HTTPException(status_code=400, detail=f"Invalid participant type: {e}")
    except Exception as e:
        logger.error(f"Error joining session: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

@app.delete("/session/{session_id}/leave/{participant_id}")
async def leave_session(session_id: str, participant_id: str):
    """Remove a participant from an isolation session"""
    try:
        app.state.audio_router.remove_participant(session_id, participant_id)
        return {
            "status": "left",
            "session_id": session_id,
            "participant_id": participant_id,
            "message": "Successfully left isolation session"
        }
    except Exception as e:
        logger.error(f"Error leaving session: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

@app.get("/session/{session_id}/status")
async def get_session_status(session_id: str):
    """Get the status of an isolation session"""
    try:
        sessions = app.state.audio_router.sessions
        if session_id not in sessions:
            raise HTTPException(status_code=404, detail="Session not found")
        
        session = sessions[session_id]
        return IsolationStatus(
            session_id=session_id,
            participant_count=len(session.channels),
            active_channels=list(session.channels.keys()),
            processing_enabled=session.is_active
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error getting session status: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")

@app.websocket("/ws/audio/{session_id}/{participant_id}")
async def websocket_audio_isolation(websocket: WebSocket, session_id: str, participant_id: str):
    """WebSocket endpoint for real-time audio isolation"""
    await app.state.connection_manager.connect(websocket, session_id, participant_id)
    
    try:
        # Send welcome message
        await websocket.send_json({
            'type': 'isolation_connected',
            'session_id': session_id,
            'participant_id': participant_id,
            'message': 'Voice isolation WebSocket connected'
        })
        
        # Handle incoming audio data
        while True:
            data = await websocket.receive()
            
            if 'bytes' in data:
                # Process audio data
                audio_data = data['bytes']
                routed_audio = app.state.audio_router.process_audio(
                    session_id, participant_id, audio_data
                )
                
                # Send processed audio to other participants
                for target_participant, processed_audio in routed_audio.items():
                    await app.state.connection_manager.send_audio(
                        session_id, target_participant, processed_audio
                    )
            
            elif 'json' in data:
                # Handle control messages
                message = data['json']
                if message.get('type') == 'control':
                    # Handle control commands (mute, unmute, etc.)
                    await websocket.send_json({
                        'type': 'control_ack',
                        'message': 'Control command received'
                    })
    
    except WebSocketDisconnect:
        logger.info(f"Voice isolation WebSocket disconnected: {session_id}/{participant_id}")
    except Exception as e:
        logger.error(f"Voice isolation WebSocket error: {e}")
    finally:
        app.state.connection_manager.disconnect(session_id, participant_id)
        app.state.audio_router.remove_participant(session_id, participant_id)

@app.get("/health")
async def health_check():
    """Detailed health check"""
    try:
        sessions_count = len(app.state.audio_router.sessions)
        active_connections = sum(
            len(participants) 
            for participants in app.state.connection_manager.active_connections.values()
        )
        
        return {
            "status": "healthy",
            "components": {
                "audio_router": "healthy",
                "connection_manager": "healthy",
                "echo_cancellation": "basic",
                "noise_suppression": "basic"
            },
            "metrics": {
                "active_sessions": sessions_count,
                "active_connections": active_connections
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
    import os
    
    # Use port 8008 for voice isolation service
    port = int(os.getenv('PORT', 8008))
    
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")
