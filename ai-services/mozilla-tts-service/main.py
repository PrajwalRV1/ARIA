#!/usr/bin/env python3
"""
Open-Source Mozilla TTS Service for ARIA Interview Platform
High-quality text-to-speech using Mozilla TTS with Tacotron 2 and HiFi-GAN
Replacement for Google Cloud Text-to-Speech
"""

import asyncio
import json
import logging
import time
import base64
import io
import os
import sys
import subprocess
from datetime import datetime
from typing import Dict, List, Optional, Any, Union
from contextlib import asynccontextmanager
from concurrent.futures import ThreadPoolExecutor
import tempfile
import hashlib
import shutil

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
import redis
import numpy as np
import wave
import struct

# Mozilla TTS imports
try:
    from TTS.api import TTS
    MOZILLA_TTS_AVAILABLE = True
except ImportError:
    MOZILLA_TTS_AVAILABLE = False
    logging.warning("Mozilla TTS not available. Install with: pip install TTS")

# Audio processing
try:
    import librosa
    import soundfile as sf
    AUDIO_PROCESSING_AVAILABLE = True
except ImportError:
    AUDIO_PROCESSING_AVAILABLE = False
    logging.warning("Audio processing libraries not available")

# pyttsx3 as fallback
try:
    import pyttsx3
    PYTTSX3_AVAILABLE = True
except ImportError:
    PYTTSX3_AVAILABLE = False
    logging.warning("pyttsx3 not available")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Redis client for coordination
redis_client = redis.Redis(host='localhost', port=6379, db=2, decode_responses=True)

# Thread pool for TTS processing
executor = ThreadPoolExecutor(max_workers=5)

# Global TTS models cache
tts_models_cache = {}
audio_cache = {}

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize services on startup"""
    logger.info("Starting Mozilla TTS Service...")
    
    # Initialize TTS models
    app.state.tts_engines = {}
    app.state.voice_profiles = {}
    app.state.audio_cache_dir = os.path.join(tempfile.gettempdir(), "aria_tts_cache")
    
    # Create audio cache directory
    os.makedirs(app.state.audio_cache_dir, exist_ok=True)
    
    # Initialize Mozilla TTS
    if MOZILLA_TTS_AVAILABLE:
        try:
            # Initialize different TTS models
            logger.info("Initializing Mozilla TTS models...")
            
            # Model 1: High-quality English model (Tacotron2 + HiFi-GAN)
            try:
                tts_model = TTS("tts_models/en/ljspeech/tacotron2-DDC")
                app.state.tts_engines['mozilla_tacotron2'] = tts_model
                tts_models_cache['mozilla_tacotron2'] = tts_model
                logger.info("Mozilla TTS Tacotron2 model initialized")
            except Exception as e:
                logger.warning(f"Failed to load Tacotron2 model: {e}")
            
            # Model 2: Fast English model for real-time use
            try:
                tts_model_fast = TTS("tts_models/en/ljspeech/speedy_speech")
                app.state.tts_engines['mozilla_fast'] = tts_model_fast
                tts_models_cache['mozilla_fast'] = tts_model_fast
                logger.info("Mozilla TTS Fast model initialized")
            except Exception as e:
                logger.warning(f"Failed to load Fast TTS model: {e}")
                
            # Model 3: Multi-speaker model for voice variety
            try:
                tts_model_multispeaker = TTS("tts_models/en/vctk/vits")
                app.state.tts_engines['mozilla_multispeaker'] = tts_model_multispeaker
                tts_models_cache['mozilla_multispeaker'] = tts_model_multispeaker
                logger.info("Mozilla TTS Multi-speaker model initialized")
            except Exception as e:
                logger.warning(f"Failed to load Multi-speaker model: {e}")
                
        except Exception as e:
            logger.warning(f"Failed to initialize Mozilla TTS: {e}")
    
    # Initialize pyttsx3 as fallback
    if PYTTSX3_AVAILABLE:
        try:
            engine = pyttsx3.init()
            # Configure pyttsx3
            voices = engine.getProperty('voices')
            if voices:
                # Use first available voice
                engine.setProperty('voice', voices[0].id)
                engine.setProperty('rate', 150)  # Speaking rate
                engine.setProperty('volume', 0.9)  # Volume
            app.state.tts_engines['pyttsx3'] = engine
            logger.info("pyttsx3 TTS engine initialized as fallback")
        except Exception as e:
            logger.warning(f"Failed to initialize pyttsx3: {e}")
    
    # Initialize synthesis manager
    app.state.synthesis_manager = VoiceSynthesisManager(app.state.tts_engines, app.state.audio_cache_dir)
    app.state.connection_manager = ConnectionManager()
    
    # Setup audio serving
    app.mount("/audio", StaticFiles(directory=app.state.audio_cache_dir), name="audio")
    
    yield
    
    # Cleanup
    logger.info("Shutting down Mozilla TTS Service...")
    # Clean up cache directory
    try:
        shutil.rmtree(app.state.audio_cache_dir)
    except Exception as e:
        logger.warning(f"Failed to clean up cache directory: {e}")

app = FastAPI(
    title="ARIA Mozilla TTS Service",
    description="Open-source text-to-speech using Mozilla TTS with Tacotron 2 and HiFi-GAN",
    version="1.0.0",
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Pydantic models
class VoiceProfile(BaseModel):
    """Voice profile configuration"""
    voice_id: str = "default"
    language: str = "en-US"
    gender: str = "neutral"  # male, female, neutral
    age: str = "adult"  # child, adult, elderly
    speed: float = 1.0  # 0.5 to 2.0
    pitch: float = 0.0  # -20.0 to 20.0
    volume: float = 0.9  # 0.0 to 1.0
    emotion: str = "neutral"  # neutral, happy, sad, excited, calm, professional
    style: str = "conversational"  # conversational, formal, friendly, authoritative
    model: str = "mozilla_tacotron2"  # mozilla_tacotron2, mozilla_fast, mozilla_multispeaker, pyttsx3

class SpeechRequest(BaseModel):
    """Text-to-speech synthesis request"""
    session_id: str
    participant_id: str = "ai_avatar"
    text: str
    voice_profile: Optional[VoiceProfile] = None
    priority: int = 1  # 1=high, 2=normal, 3=low
    stream: bool = False
    format: str = "wav"  # wav, mp3, pcm
    sample_rate: int = 22050
    use_cache: bool = True

class SpeechResponse(BaseModel):
    """Response from text-to-speech synthesis"""
    session_id: str
    participant_id: str
    audio_data: Optional[str] = None  # Base64 encoded audio
    audio_url: Optional[str] = None
    duration_ms: int
    format: str
    sample_rate: int
    synthesis_time_ms: int
    engine_used: str
    cached: bool = False

class ConnectionManager:
    """Manages WebSocket connections for voice synthesis streaming"""
    
    def __init__(self):
        self.active_connections: Dict[str, List[WebSocket]] = {}
        self.session_metadata: Dict[str, Dict] = {}
    
    async def connect(self, websocket: WebSocket, session_id: str):
        await websocket.accept()
        if session_id not in self.active_connections:
            self.active_connections[session_id] = []
        self.active_connections[session_id].append(websocket)
        logger.info(f"Mozilla TTS WebSocket connected for session {session_id}")
    
    def disconnect(self, websocket: WebSocket, session_id: str):
        if session_id in self.active_connections:
            if websocket in self.active_connections[session_id]:
                self.active_connections[session_id].remove(websocket)
            if not self.active_connections[session_id]:
                del self.active_connections[session_id]
        logger.info(f"Mozilla TTS WebSocket disconnected for session {session_id}")
    
    async def broadcast_to_session(self, session_id: str, message: dict):
        if session_id in self.active_connections:
            dead_connections = []
            for connection in self.active_connections[session_id]:
                try:
                    await connection.send_json(message)
                except Exception as e:
                    logger.error(f"Error sending to connection: {e}")
                    dead_connections.append(connection)
            
            # Remove dead connections
            for dead_connection in dead_connections:
                self.disconnect(dead_connection, session_id)

class VoiceSynthesisManager:
    """Manages voice synthesis with caching and optimization"""
    
    def __init__(self, tts_engines: Dict, cache_dir: str):
        self.tts_engines = tts_engines
        self.cache_dir = cache_dir
        self.synthesis_queue = {}
    
    def generate_cache_key(self, text: str, voice_profile: VoiceProfile) -> str:
        """Generate cache key for audio files"""
        profile_str = f"{voice_profile.model}_{voice_profile.speed}_{voice_profile.pitch}_{voice_profile.volume}"
        content = f"{text}_{profile_str}"
        return hashlib.md5(content.encode()).hexdigest()
    
    def get_cached_audio(self, cache_key: str, format: str = "wav") -> Optional[str]:
        """Get cached audio file path"""
        cache_file = os.path.join(self.cache_dir, f"{cache_key}.{format}")
        if os.path.exists(cache_file):
            return cache_file
        return None
    
    async def synthesize_speech(self, text: str, voice_profile: VoiceProfile, session_id: str) -> Dict:
        """Synthesize speech with the specified voice profile"""
        start_time = time.time()
        
        # Generate cache key
        cache_key = self.generate_cache_key(text, voice_profile)
        
        # Check cache first
        if voice_profile.use_cache:
            cached_file = self.get_cached_audio(cache_key)
            if cached_file:
                duration = self.get_audio_duration(cached_file)
                return {
                    "audio_file": cached_file,
                    "audio_url": f"/audio/{os.path.basename(cached_file)}",
                    "duration_ms": int(duration * 1000),
                    "synthesis_time_ms": int((time.time() - start_time) * 1000),
                    "engine_used": voice_profile.model,
                    "cached": True
                }
        
        # Choose TTS engine
        engine_name = voice_profile.model
        if engine_name not in self.tts_engines:
            logger.warning(f"TTS engine {engine_name} not available, using fallback")
            engine_name = self.get_best_available_engine()
        
        engine = self.tts_engines.get(engine_name)
        if not engine:
            raise HTTPException(status_code=500, detail="No TTS engine available")
        
        # Generate audio file path
        audio_file = os.path.join(self.cache_dir, f"{cache_key}.wav")
        
        try:
            if engine_name.startswith('mozilla'):
                # Use Mozilla TTS
                await self.synthesize_with_mozilla_tts(engine, text, voice_profile, audio_file)
            elif engine_name == 'pyttsx3':
                # Use pyttsx3
                await self.synthesize_with_pyttsx3(engine, text, voice_profile, audio_file)
            else:
                raise ValueError(f"Unknown engine: {engine_name}")
            
            # Get audio duration
            duration = self.get_audio_duration(audio_file)
            
            return {
                "audio_file": audio_file,
                "audio_url": f"/audio/{os.path.basename(audio_file)}",
                "duration_ms": int(duration * 1000),
                "synthesis_time_ms": int((time.time() - start_time) * 1000),
                "engine_used": engine_name,
                "cached": False
            }
            
        except Exception as e:
            logger.error(f"Speech synthesis failed: {e}")
            # Try fallback engine
            return await self.synthesize_with_fallback(text, voice_profile, session_id, start_time)
    
    async def synthesize_with_mozilla_tts(self, engine, text: str, voice_profile: VoiceProfile, output_file: str):
        """Synthesize speech using Mozilla TTS"""
        def _synthesize():
            try:
                # For multi-speaker models, we can specify a speaker
                if hasattr(engine, 'speakers') and engine.speakers:
                    # Choose speaker based on gender preference
                    speaker = None
                    if voice_profile.gender == "female" and "female" in str(engine.speakers).lower():
                        speaker = next((s for s in engine.speakers if "female" in s.lower()), None)
                    elif voice_profile.gender == "male" and "male" in str(engine.speakers).lower():
                        speaker = next((s for s in engine.speakers if "male" in s.lower()), None)
                    
                    if speaker:
                        engine.tts_to_file(text, file_path=output_file, speaker=speaker)
                    else:
                        engine.tts_to_file(text, file_path=output_file)
                else:
                    engine.tts_to_file(text, file_path=output_file)
                
                # Post-process audio if needed (speed, pitch adjustments)
                if voice_profile.speed != 1.0 or voice_profile.pitch != 0.0:
                    self.post_process_audio(output_file, voice_profile)
                
            except Exception as e:
                logger.error(f"Mozilla TTS synthesis failed: {e}")
                raise
        
        # Run in thread pool to avoid blocking
        await asyncio.get_event_loop().run_in_executor(executor, _synthesize)
    
    async def synthesize_with_pyttsx3(self, engine, text: str, voice_profile: VoiceProfile, output_file: str):
        """Synthesize speech using pyttsx3"""
        def _synthesize():
            try:
                # Configure engine
                engine.setProperty('rate', int(150 * voice_profile.speed))
                engine.setProperty('volume', voice_profile.volume)
                
                # Save to file
                engine.save_to_file(text, output_file)
                engine.runAndWait()
                
            except Exception as e:
                logger.error(f"pyttsx3 synthesis failed: {e}")
                raise
        
        # Run in thread pool to avoid blocking
        await asyncio.get_event_loop().run_in_executor(executor, _synthesize)
    
    def post_process_audio(self, audio_file: str, voice_profile: VoiceProfile):
        """Post-process audio for speed and pitch adjustments"""
        if not AUDIO_PROCESSING_AVAILABLE:
            return
        
        try:
            # Load audio
            y, sr = librosa.load(audio_file, sr=None)
            
            # Apply speed change (time stretching)
            if voice_profile.speed != 1.0:
                y = librosa.effects.time_stretch(y, rate=voice_profile.speed)
            
            # Apply pitch shift
            if voice_profile.pitch != 0.0:
                # Convert pitch from semitones to shift factor
                n_steps = voice_profile.pitch
                y = librosa.effects.pitch_shift(y, sr=sr, n_steps=n_steps)
            
            # Save processed audio
            sf.write(audio_file, y, sr)
            
        except Exception as e:
            logger.warning(f"Audio post-processing failed: {e}")
    
    async def synthesize_with_fallback(self, text: str, voice_profile: VoiceProfile, session_id: str, start_time: float) -> Dict:
        """Fallback synthesis using available engines"""
        fallback_engines = ['mozilla_fast', 'mozilla_tacotron2', 'pyttsx3']
        
        for engine_name in fallback_engines:
            if engine_name in self.tts_engines:
                try:
                    voice_profile.model = engine_name
                    return await self.synthesize_speech(text, voice_profile, session_id)
                except Exception as e:
                    logger.warning(f"Fallback engine {engine_name} failed: {e}")
                    continue
        
        # Ultimate fallback: return error
        raise HTTPException(status_code=500, detail="All TTS engines failed")
    
    def get_best_available_engine(self) -> str:
        """Get the best available TTS engine"""
        preferred_order = ['mozilla_tacotron2', 'mozilla_fast', 'mozilla_multispeaker', 'pyttsx3']
        
        for engine_name in preferred_order:
            if engine_name in self.tts_engines:
                return engine_name
        
        raise ValueError("No TTS engine available")
    
    def get_audio_duration(self, audio_file: str) -> float:
        """Get duration of audio file in seconds"""
        try:
            with wave.open(audio_file, 'rb') as wf:
                frames = wf.getnframes()
                rate = wf.getframerate()
                duration = frames / float(rate)
                return duration
        except Exception:
            # Fallback: estimate based on file size (very rough)
            try:
                file_size = os.path.getsize(audio_file)
                # Rough estimate: 16-bit, 22050 Hz mono
                estimated_duration = file_size / (2 * 22050)
                return estimated_duration
            except Exception:
                return 1.0  # Default fallback

# API Endpoints

@app.post("/synthesize", response_model=SpeechResponse)
async def synthesize_speech(request: SpeechRequest) -> SpeechResponse:
    """Synthesize speech from text"""
    try:
        logger.info(f"Synthesizing speech for session {request.session_id}: {request.text[:100]}...")
        
        # Use default voice profile if not provided
        voice_profile = request.voice_profile or VoiceProfile()
        
        # Get synthesis manager
        synthesis_manager = app.state.synthesis_manager
        
        # Synthesize speech
        result = await synthesis_manager.synthesize_speech(request.text, voice_profile, request.session_id)
        
        # Prepare response
        response = SpeechResponse(
            session_id=request.session_id,
            participant_id=request.participant_id,
            audio_url=result["audio_url"],
            duration_ms=result["duration_ms"],
            format=request.format,
            sample_rate=request.sample_rate,
            synthesis_time_ms=result["synthesis_time_ms"],
            engine_used=result["engine_used"],
            cached=result["cached"]
        )
        
        # Optionally include audio data as base64
        if request.stream or not request.use_cache:
            try:
                with open(result["audio_file"], 'rb') as f:
                    audio_data = f.read()
                    response.audio_data = base64.b64encode(audio_data).decode('utf-8')
            except Exception as e:
                logger.warning(f"Failed to include audio data: {e}")
        
        # Broadcast to WebSocket connections
        connection_manager = app.state.connection_manager
        await connection_manager.broadcast_to_session(request.session_id, {
            "type": "speech_synthesis_ready",
            "audio_url": result["audio_url"],
            "duration_ms": result["duration_ms"],
            "engine_used": result["engine_used"]
        })
        
        return response
        
    except Exception as e:
        logger.error(f"Speech synthesis error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/audio/{filename}")
async def get_audio_file(filename: str):
    """Serve audio files"""
    audio_file = os.path.join(app.state.audio_cache_dir, filename)
    if os.path.exists(audio_file):
        return FileResponse(
            audio_file, 
            media_type="audio/wav",
            headers={"Cache-Control": "public, max-age=3600"}
        )
    else:
        raise HTTPException(status_code=404, detail="Audio file not found")

@app.get("/voices")
async def list_available_voices():
    """List available voice profiles and engines"""
    available_engines = list(app.state.tts_engines.keys())
    
    voice_profiles = []
    for engine in available_engines:
        if engine.startswith('mozilla'):
            # Add different voice configurations for Mozilla TTS
            voices = [
                {"id": f"{engine}_neutral", "name": f"Neutral ({engine})", "gender": "neutral", "model": engine},
                {"id": f"{engine}_professional", "name": f"Professional ({engine})", "gender": "neutral", "model": engine},
            ]
            voice_profiles.extend(voices)
        elif engine == 'pyttsx3':
            voice_profiles.append({
                "id": "pyttsx3_default", "name": "Default (pyttsx3)", "gender": "neutral", "model": engine
            })
    
    return {
        "available_engines": available_engines,
        "voice_profiles": voice_profiles,
        "default_profile": {
            "model": app.state.synthesis_manager.get_best_available_engine() if hasattr(app.state, 'synthesis_manager') else "pyttsx3",
            "speed": 1.0,
            "volume": 0.9,
            "format": "wav",
            "sample_rate": 22050
        }
    }

@app.websocket("/ws/tts/{session_id}")
async def websocket_tts_endpoint(websocket: WebSocket, session_id: str):
    """WebSocket endpoint for real-time TTS streaming"""
    connection_manager = app.state.connection_manager
    await connection_manager.connect(websocket, session_id)
    
    try:
        while True:
            # Wait for text to synthesize
            data = await websocket.receive_json()
            
            if data.get("type") == "synthesize_request":
                try:
                    # Create synthesis request
                    request = SpeechRequest(
                        session_id=session_id,
                        text=data["text"],
                        voice_profile=VoiceProfile(**data.get("voice_profile", {}))
                    )
                    
                    # Synthesize speech
                    response = await synthesize_speech(request)
                    
                    # Send response back
                    await websocket.send_json({
                        "type": "synthesis_complete",
                        "audio_url": response.audio_url,
                        "duration_ms": response.duration_ms,
                        "engine_used": response.engine_used
                    })
                    
                except Exception as e:
                    await websocket.send_json({
                        "type": "synthesis_error",
                        "error": str(e)
                    })
            
    except WebSocketDisconnect:
        connection_manager.disconnect(websocket, session_id)
        logger.info(f"WebSocket disconnected for session {session_id}")
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        connection_manager.disconnect(websocket, session_id)

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    available_engines = list(app.state.tts_engines.keys())
    
    return {
        "status": "healthy",
        "service": "Mozilla TTS Service",
        "available_engines": available_engines,
        "mozilla_tts_available": MOZILLA_TTS_AVAILABLE,
        "pyttsx3_available": PYTTSX3_AVAILABLE,
        "audio_processing_available": AUDIO_PROCESSING_AVAILABLE,
        "cache_directory": app.state.audio_cache_dir,
        "timestamp": datetime.now().isoformat()
    }

@app.delete("/cache")
async def clear_cache():
    """Clear audio cache"""
    try:
        cache_dir = app.state.audio_cache_dir
        for filename in os.listdir(cache_dir):
            if filename.endswith(('.wav', '.mp3')):
                os.remove(os.path.join(cache_dir, filename))
        
        return {"status": "success", "message": "Audio cache cleared"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to clear cache: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    import os
    
    # Use port 8004 to avoid conflict with Analytics Service on 8003
    port = 8004
    
    # Check if SSL certificates exist
    ssl_cert_path = "../../ssl-certs/aria-cert.pem"
    ssl_key_path = "../../ssl-certs/aria-key.pem"
    
    if os.path.exists(ssl_cert_path) and os.path.exists(ssl_key_path):
        # Run with SSL
        uvicorn.run(
            "main:app",
            host="0.0.0.0",
            port=port,
            reload=True,
            ssl_keyfile=ssl_key_path,
            ssl_certfile=ssl_cert_path
        )
    else:
        # Fallback to HTTP
        print("Warning: SSL certificates not found, running with HTTP")
        uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)
