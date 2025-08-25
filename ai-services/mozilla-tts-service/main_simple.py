#!/usr/bin/env python3
"""
Mozilla TTS Service for ARIA Interview Platform (Simplified)
Lightweight TTS service optimized for Railway deployment
"""

import asyncio
import json
import logging
import time
import base64
import io
import os
from datetime import datetime
from typing import Dict, List, Optional, Any, Union
from contextlib import asynccontextmanager
from concurrent.futures import ThreadPoolExecutor

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import redis
import httpx
import numpy as np

# TTS engines (lightweight alternatives)
try:
    import pyttsx3
    PYTTSX3_AVAILABLE = True
except ImportError:
    PYTTSX3_AVAILABLE = False
    logging.warning("pyttsx3 not available")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Redis client for caching
try:
    redis_client = redis.Redis(
        host=os.getenv('REDIS_HOST', 'localhost'),
        port=int(os.getenv('REDIS_PORT', 6379)),
        db=3,
        decode_responses=True
    )
except Exception as e:
    logger.warning(f"Redis connection failed: {e}")
    redis_client = None

# Thread pool for TTS processing
executor = ThreadPoolExecutor(max_workers=3)

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize services on startup"""
    logger.info("Starting Mozilla TTS Service (Simplified)...")
    
    # Initialize TTS engines
    app.state.tts_engines = {}
    
    # Initialize lightweight TTS engine
    if PYTTSX3_AVAILABLE:
        try:
            engine = pyttsx3.init()
            app.state.tts_engines['pyttsx3'] = engine
            logger.info("pyttsx3 TTS engine initialized")
        except Exception as e:
            logger.warning(f"Failed to initialize pyttsx3: {e}")
    
    # Initialize TTS manager
    app.state.tts_manager = SimpleTTSManager(app.state.tts_engines)
    app.state.connection_manager = ConnectionManager()
    
    yield
    
    # Cleanup
    logger.info("Shutting down Mozilla TTS Service...")

app = FastAPI(
    title="ARIA Mozilla TTS Service (Simplified)",
    description="Lightweight TTS service with Mozilla-compatible API",
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

class TTSRequest(BaseModel):
    """Text-to-speech synthesis request"""
    text: str
    voice: Optional[str] = "default"
    speed: float = 1.0
    language: str = "en"
    format: str = "wav"
    session_id: Optional[str] = None
    participant_id: Optional[str] = None

class TTSResponse(BaseModel):
    """Text-to-speech synthesis response"""
    audio_data: Optional[str] = None  # Base64 encoded
    duration_ms: int
    format: str
    synthesis_time_ms: int
    engine_used: str
    voice_used: str

class VoiceInfo(BaseModel):
    """Information about available voices"""
    voice_id: str
    name: str
    language: str
    gender: str
    quality: str
    engine: str

class ConnectionManager:
    """Manages WebSocket connections for TTS streaming"""
    
    def __init__(self):
        self.active_connections: Dict[str, List[WebSocket]] = {}
        
    async def connect(self, websocket: WebSocket, session_id: str):
        await websocket.accept()
        if session_id not in self.active_connections:
            self.active_connections[session_id] = []
        self.active_connections[session_id].append(websocket)
        logger.info(f"TTS WebSocket connected for session {session_id}")
    
    def disconnect(self, websocket: WebSocket, session_id: str):
        if session_id in self.active_connections:
            if websocket in self.active_connections[session_id]:
                self.active_connections[session_id].remove(websocket)
            if not self.active_connections[session_id]:
                del self.active_connections[session_id]
        logger.info(f"TTS WebSocket disconnected for session {session_id}")
    
    async def broadcast_to_session(self, session_id: str, message: dict):
        if session_id in self.active_connections:
            dead_connections = []
            for connection in self.active_connections[session_id]:
                try:
                    await connection.send_json(message)
                except Exception as e:
                    logger.error(f"Error sending to TTS connection: {e}")
                    dead_connections.append(connection)
            
            # Remove dead connections
            for dead_connection in dead_connections:
                self.disconnect(dead_connection, session_id)

class SimpleTTSManager:
    """Simplified TTS manager without heavy ML dependencies"""
    
    def __init__(self, tts_engines: Dict[str, Any]):
        self.tts_engines = tts_engines
        self.voice_cache = {}
        
        # Available voices (simulated)
        self.available_voices = {
            "default": VoiceInfo(
                voice_id="default",
                name="Default Voice",
                language="en",
                gender="neutral",
                quality="good",
                engine="pyttsx3"
            ),
            "male": VoiceInfo(
                voice_id="male",
                name="Male Voice",
                language="en",
                gender="male",
                quality="good",
                engine="pyttsx3"
            ),
            "female": VoiceInfo(
                voice_id="female",
                name="Female Voice", 
                language="en",
                gender="female",
                quality="good",
                engine="pyttsx3"
            )
        }
    
    async def synthesize_speech(self, request: TTSRequest) -> TTSResponse:
        """Synthesize speech using available engines"""
        start_time = time.time()
        
        try:
            # Check cache first
            cache_key = f"tts:{hash(request.text)}:{request.voice}:{request.speed}"
            if redis_client:
                try:
                    cached_result = redis_client.get(cache_key)
                    if cached_result:
                        cached_data = json.loads(cached_result)
                        logger.info(f"Using cached TTS result for: {request.text[:50]}...")
                        return TTSResponse(**cached_data)
                except Exception as e:
                    logger.warning(f"Cache lookup failed: {e}")
            
            # Select best available engine
            engine_name = self._select_best_engine(request)
            
            # Synthesize speech
            audio_data, duration_ms = await self._synthesize_with_engine(
                engine_name, request.text, request.voice, request.speed, request.format
            )
            
            synthesis_time = int((time.time() - start_time) * 1000)
            
            # Create response
            response = TTSResponse(
                audio_data=base64.b64encode(audio_data).decode('utf-8') if audio_data else None,
                duration_ms=duration_ms,
                format=request.format,
                synthesis_time_ms=synthesis_time,
                engine_used=engine_name,
                voice_used=request.voice or "default"
            )
            
            # Cache result
            if redis_client and audio_data:
                try:
                    cache_data = response.dict()
                    redis_client.setex(cache_key, 3600, json.dumps(cache_data))  # 1 hour TTL
                except Exception as e:
                    logger.warning(f"Failed to cache result: {e}")
            
            logger.info(f"TTS completed: {len(request.text)} chars in {synthesis_time}ms using {engine_name}")
            return response
            
        except Exception as e:
            logger.error(f"Error in speech synthesis: {e}")
            return await self._create_fallback_response(request, str(e))
    
    def _select_best_engine(self, request: TTSRequest) -> str:
        """Select the best available TTS engine"""
        if "pyttsx3" in self.tts_engines:
            return "pyttsx3"
        else:
            return "fallback"
    
    async def _synthesize_with_engine(self, engine_name: str, text: str, voice: str, 
                                     speed: float, format: str) -> tuple[bytes, int]:
        """Synthesize speech using specified engine"""
        
        if engine_name == "pyttsx3":
            return await self._synthesize_pyttsx3(text, voice, speed, format)
        else:
            return await self._synthesize_fallback(text, voice, speed, format)
    
    async def _synthesize_pyttsx3(self, text: str, voice: str, speed: float, format: str) -> tuple[bytes, int]:
        """Synthesize using pyttsx3 engine"""
        try:
            engine = self.tts_engines['pyttsx3']
            
            # Configure engine
            engine.setProperty('rate', int(speed * 200))  # Convert to WPM
            engine.setProperty('volume', 0.9)
            
            # Set voice based on request
            voices = engine.getProperty('voices')
            if voices and voice in ["male", "female"]:
                for available_voice in voices:
                    if voice == "male" and "male" in available_voice.name.lower():
                        engine.setProperty('voice', available_voice.id)
                        break
                    elif voice == "female" and "female" in available_voice.name.lower():
                        engine.setProperty('voice', available_voice.id)
                        break
            
            # Create temporary file
            temp_file = f"/tmp/tts_{int(time.time())}_{hash(text)}.wav"
            
            # Synthesize to file
            await asyncio.get_event_loop().run_in_executor(
                executor,
                lambda: self._pyttsx3_to_file(engine, text, temp_file)
            )
            
            # Read audio data
            with open(temp_file, 'rb') as f:
                audio_data = f.read()
            
            # Calculate duration (approximate)
            duration_ms = max(len(text) * 60, 1000)  # Rough estimate
            
            # Cleanup
            try:
                os.remove(temp_file)
            except Exception:
                pass
            
            return audio_data, duration_ms
            
        except Exception as e:
            logger.error(f"pyttsx3 synthesis failed: {e}")
            raise
    
    def _pyttsx3_to_file(self, engine, text: str, filename: str):
        """Helper to synthesize pyttsx3 to file"""
        engine.save_to_file(text, filename)
        engine.runAndWait()
    
    async def _synthesize_fallback(self, text: str, voice: str, speed: float, format: str) -> tuple[bytes, int]:
        """Fallback synthesis - returns silence"""
        logger.warning(f"Using fallback synthesis for: {text[:50]}...")
        
        # Generate silence as fallback
        duration_ms = max(len(text) * 80, 2000)  # Minimum 2 seconds
        sample_rate = 16000
        samples = int(sample_rate * duration_ms / 1000)
        
        # Create silence (16-bit PCM)
        silence_data = bytes(samples * 2)  # 2 bytes per 16-bit sample
        
        return silence_data, duration_ms
    
    async def _create_fallback_response(self, request: TTSRequest, error: str) -> TTSResponse:
        """Create fallback response when synthesis fails"""
        logger.warning(f"Creating fallback TTS response for error: {error}")
        
        audio_data, duration_ms = await self._synthesize_fallback(
            request.text, request.voice or "default", request.speed, request.format
        )
        
        return TTSResponse(
            audio_data=base64.b64encode(audio_data).decode('utf-8'),
            duration_ms=duration_ms,
            format=request.format,
            synthesis_time_ms=100,
            engine_used="fallback",
            voice_used=request.voice or "default"
        )

# ==================== API ENDPOINTS ====================

@app.get("/")
async def root():
    """Health check endpoint"""
    engines_status = {}
    for engine_name in ['pyttsx3', 'mozilla_tts']:
        engines_status[f'{engine_name}'] = engine_name in app.state.tts_engines
    
    return {
        "service": "ARIA Mozilla TTS Service (Simplified)",
        "status": "healthy",
        "version": "1.0.0",
        "engines": engines_status,
        "available_voices": list(app.state.tts_manager.available_voices.keys()),
        "timestamp": datetime.now().isoformat()
    }

@app.post("/tts/synthesize", response_model=TTSResponse)
async def synthesize_text_to_speech(request: TTSRequest):
    """Synthesize text to speech"""
    try:
        response = await app.state.tts_manager.synthesize_speech(request)
        return response
    except Exception as e:
        logger.error(f"Error in TTS synthesis endpoint: {e}")
        return await app.state.tts_manager._create_fallback_response(request, str(e))

@app.get("/tts/voices", response_model=List[VoiceInfo])
async def list_voices():
    """List available TTS voices"""
    return list(app.state.tts_manager.available_voices.values())

@app.get("/tts/voice/{voice_id}")
async def get_voice_info(voice_id: str):
    """Get information about a specific voice"""
    if voice_id in app.state.tts_manager.available_voices:
        return app.state.tts_manager.available_voices[voice_id]
    else:
        raise HTTPException(status_code=404, detail="Voice not found")

@app.websocket("/ws/tts/{session_id}")
async def websocket_tts(websocket: WebSocket, session_id: str):
    """WebSocket endpoint for real-time TTS"""
    await app.state.connection_manager.connect(websocket, session_id)
    
    try:
        # Send welcome message
        await websocket.send_json({
            'type': 'tts_connected',
            'session_id': session_id,
            'message': 'TTS WebSocket connected',
            'available_voices': list(app.state.tts_manager.available_voices.keys())
        })
        
        # Listen for TTS requests
        while True:
            data = await websocket.receive_json()
            
            if data.get('type') == 'synthesize_request':
                # Create TTS request
                tts_request = TTSRequest(
                    text=data.get('text', ''),
                    voice=data.get('voice', 'default'),
                    speed=data.get('speed', 1.0),
                    language=data.get('language', 'en'),
                    format=data.get('format', 'wav'),
                    session_id=session_id
                )
                
                try:
                    # Process TTS
                    response = await app.state.tts_manager.synthesize_speech(tts_request)
                    
                    # Send response
                    await websocket.send_json({
                        'type': 'synthesis_response',
                        'audio_data': response.audio_data,
                        'duration_ms': response.duration_ms,
                        'format': response.format,
                        'engine_used': response.engine_used,
                        'voice_used': response.voice_used
                    })
                
                except Exception as e:
                    await websocket.send_json({
                        'type': 'synthesis_error',
                        'error': str(e)
                    })
    
    except WebSocketDisconnect:
        logger.info(f"TTS WebSocket disconnected for session {session_id}")
    except Exception as e:
        logger.error(f"TTS WebSocket error: {e}")
    finally:
        app.state.connection_manager.disconnect(websocket, session_id)

@app.get("/health")
async def health_check():
    """Detailed health check"""
    try:
        redis_status = "healthy"
        try:
            if redis_client:
                redis_client.ping()
            else:
                redis_status = "unavailable"
        except:
            redis_status = "unhealthy"
        
        components = {
            "redis": redis_status,
            "pyttsx3": "available" if 'pyttsx3' in app.state.tts_engines else "unavailable",
            "tts_manager": "healthy",
            "connection_manager": "healthy"
        }
        
        overall_status = "healthy" if any(comp == "available" for comp in components.values() if "tts" not in comp or comp == "available") else "degraded"
        
        return {
            "status": overall_status,
            "components": components,
            "active_sessions": len(app.state.connection_manager.active_connections),
            "available_voices": len(app.state.tts_manager.available_voices),
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
    
    # Use port 8007 for Mozilla TTS service
    port = int(os.getenv('PORT', 8007))
    
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")
