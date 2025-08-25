#!/usr/bin/env python3
"""
AI Voice Synthesis Service for ARIA Interview Platform
High-quality text-to-speech with emotional intelligence and real-time streaming
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
import numpy as np

# Text-to-Speech engines
try:
    import pyttsx3
    PYTTSX3_AVAILABLE = True
except ImportError:
    PYTTSX3_AVAILABLE = False
    logging.warning("pyttsx3 not available")

# Mozilla TTS imports
try:
    from TTS.api import TTS
    MOZILLA_TTS_AVAILABLE = True
except ImportError:
    MOZILLA_TTS_AVAILABLE = False
    logging.warning("Mozilla TTS not available. Install with: pip install TTS")

try:
    import azure.cognitiveservices.speech as speechsdk
    AZURE_TTS_AVAILABLE = True
except ImportError:
    AZURE_TTS_AVAILABLE = False
    logging.warning("Azure Cognitive Services Speech not available")

# Audio processing
try:
    import librosa
    import soundfile as sf
    AUDIO_PROCESSING_AVAILABLE = True
except ImportError:
    AUDIO_PROCESSING_AVAILABLE = False
    logging.warning("Audio processing libraries not available")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Redis client for coordination
redis_client = redis.Redis(host='localhost', port=6379, db=2, decode_responses=True)

# Thread pool for TTS processing
executor = ThreadPoolExecutor(max_workers=5)

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize services on startup"""
    logger.info("Starting AI Voice Synthesis Service...")
    
    # Initialize TTS engines
    app.state.tts_engines = {}
    app.state.voice_profiles = {}
    
    # Initialize pyttsx3 engine
    if PYTTSX3_AVAILABLE:
        try:
            engine = pyttsx3.init()
            app.state.tts_engines['pyttsx3'] = engine
            logger.info("pyttsx3 TTS engine initialized")
        except Exception as e:
            logger.warning(f"Failed to initialize pyttsx3: {e}")
    
    # Initialize Mozilla TTS
    if MOZILLA_TTS_AVAILABLE:
        try:
            # Initialize different Mozilla TTS models
            logger.info("Initializing Mozilla TTS models...")
            
            # High-quality model
            try:
                tts_model = TTS("tts_models/en/ljspeech/tacotron2-DDC")
                app.state.tts_engines['mozilla_tacotron2'] = tts_model
                logger.info("Mozilla TTS Tacotron2 model initialized")
            except Exception as e:
                logger.warning(f"Failed to load Tacotron2 model: {e}")
            
            # Fast model for real-time use
            try:
                tts_model_fast = TTS("tts_models/en/ljspeech/speedy_speech")
                app.state.tts_engines['mozilla_fast'] = tts_model_fast
                logger.info("Mozilla TTS Fast model initialized")
            except Exception as e:
                logger.warning(f"Failed to load Fast TTS model: {e}")
                
        except Exception as e:
            logger.warning(f"Failed to initialize Mozilla TTS: {e}")
    
    # Initialize Azure TTS
    if AZURE_TTS_AVAILABLE and os.getenv('AZURE_SPEECH_KEY'):
        try:
            speech_config = speechsdk.SpeechConfig(
                subscription=os.getenv('AZURE_SPEECH_KEY'),
                region=os.getenv('AZURE_SPEECH_REGION', 'eastus')
            )
            app.state.tts_engines['azure'] = speech_config
            logger.info("Azure TTS initialized")
        except Exception as e:
            logger.warning(f"Failed to initialize Azure TTS: {e}")
    
    # Initialize voice synthesis manager
    app.state.synthesis_manager = VoiceSynthesisManager(app.state.tts_engines)
    app.state.connection_manager = ConnectionManager()
    
    yield
    
    # Cleanup
    logger.info("Shutting down AI Voice Synthesis Service...")

app = FastAPI(
    title="ARIA AI Voice Synthesis Service",
    description="High-quality text-to-speech with emotional intelligence and real-time streaming",
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
    volume: float = 0.0  # -96.0 to 16.0
    emotion: str = "neutral"  # neutral, happy, sad, excited, calm, professional
    style: str = "conversational"  # conversational, formal, friendly, authoritative

class SpeechRequest(BaseModel):
    """Text-to-speech synthesis request"""
    session_id: str
    participant_id: str = "ai_avatar"
    text: str
    voice_profile: Optional[VoiceProfile] = None
    priority: int = 1  # 1=high, 2=normal, 3=low
    stream: bool = False
    format: str = "wav"  # wav, mp3, pcm
    sample_rate: int = 16000

class EmotionalContext(BaseModel):
    """Emotional context for speech synthesis"""
    emotion: str = "neutral"
    intensity: float = 0.5  # 0.0 to 1.0
    context_type: str = "interview"  # interview, feedback, question, answer
    urgency: str = "normal"  # low, normal, high, urgent
    formality: str = "professional"  # casual, professional, formal

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
        logger.info(f"Voice synthesis WebSocket connected for session {session_id}")
    
    def disconnect(self, websocket: WebSocket, session_id: str):
        if session_id in self.active_connections:
            if websocket in self.active_connections[session_id]:
                self.active_connections[session_id].remove(websocket)
            if not self.active_connections[session_id]:
                del self.active_connections[session_id]
        logger.info(f"Voice synthesis WebSocket disconnected for session {session_id}")
    
    async def broadcast_to_session(self, session_id: str, message: dict):
        if session_id in self.active_connections:
            dead_connections = []
            for connection in self.active_connections[session_id]:
                try:
                    await connection.send_json(message)
                except Exception as e:
                    logger.error(f"Error sending to voice synthesis connection: {e}")
                    dead_connections.append(connection)
            
            # Remove dead connections
            for dead_connection in dead_connections:
                self.disconnect(dead_connection, session_id)

class VoiceSynthesisManager:
    """Manages voice synthesis operations with emotional intelligence"""
    
    def __init__(self, tts_engines: Dict[str, Any]):
        self.tts_engines = tts_engines
        self.synthesis_queue = asyncio.Queue()
        self.active_synthesis: Dict[str, asyncio.Task] = {}
        
        # Voice profiles for different contexts
        self.voice_profiles = {
            "ai_avatar_professional": VoiceProfile(
                voice_id="professional",
                speed=1.1,
                pitch=0.0,
                emotion="professional",
                style="conversational"
            ),
            "ai_avatar_friendly": VoiceProfile(
                voice_id="friendly",
                speed=1.0,
                pitch=2.0,
                emotion="friendly",
                style="conversational"
            ),
            "ai_avatar_empathetic": VoiceProfile(
                voice_id="empathetic",
                speed=0.9,
                pitch=-1.0,
                emotion="calm",
                style="supportive"
            )
        }
        
        # Emotional context mappings
        self.emotion_mappings = {
            "introduction": {"emotion": "friendly", "intensity": 0.7},
            "question": {"emotion": "professional", "intensity": 0.6},
            "encouragement": {"emotion": "supportive", "intensity": 0.8},
            "feedback": {"emotion": "neutral", "intensity": 0.5},
            "technical": {"emotion": "focused", "intensity": 0.6},
            "conclusion": {"emotion": "positive", "intensity": 0.7}
        }
        
        # Start synthesis worker
        asyncio.create_task(self._synthesis_worker())
    
    async def synthesize_speech(self, request: SpeechRequest, emotional_context: Optional[EmotionalContext] = None) -> SpeechResponse:
        """Synthesize speech with emotional intelligence"""
        start_time = time.time()
        
        try:
            # Determine voice profile
            voice_profile = request.voice_profile or self._get_contextual_voice_profile(emotional_context)
            
            # Choose the best TTS engine
            engine_name = self._select_best_engine(request, voice_profile)
            
            # Synthesize audio
            audio_data, duration_ms = await self._synthesize_with_engine(
                engine_name, request.text, voice_profile, request.format, request.sample_rate
            )
            
            synthesis_time = int((time.time() - start_time) * 1000)
            
            # Create response
            response = SpeechResponse(
                session_id=request.session_id,
                participant_id=request.participant_id,
                audio_data=base64.b64encode(audio_data).decode('utf-8') if audio_data else None,
                duration_ms=duration_ms,
                format=request.format,
                sample_rate=request.sample_rate,
                synthesis_time_ms=synthesis_time,
                engine_used=engine_name
            )
            
            # Store in Redis for caching
            await self._cache_synthesis_result(request, response)
            
            logger.info(f"Speech synthesized: {len(request.text)} chars in {synthesis_time}ms using {engine_name}")
            
            return response
        
        except Exception as e:
            logger.error(f"Error synthesizing speech: {e}")
            raise HTTPException(status_code=500, detail=f"Speech synthesis failed: {str(e)}")
    
    def _get_contextual_voice_profile(self, emotional_context: Optional[EmotionalContext]) -> VoiceProfile:
        """Get appropriate voice profile based on emotional context"""
        if not emotional_context:
            return self.voice_profiles["ai_avatar_professional"]
        
        # Map emotional context to voice profile
        if emotional_context.emotion in ["supportive", "empathetic", "calm"]:
            profile = self.voice_profiles["ai_avatar_empathetic"].copy()
        elif emotional_context.emotion in ["friendly", "excited", "positive"]:
            profile = self.voice_profiles["ai_avatar_friendly"].copy()
        else:
            profile = self.voice_profiles["ai_avatar_professional"].copy()
        
        # Adjust based on intensity and context
        if emotional_context.intensity > 0.7:
            profile.speed = min(profile.speed + 0.1, 2.0)
            profile.pitch = min(profile.pitch + 1.0, 20.0)
        elif emotional_context.intensity < 0.3:
            profile.speed = max(profile.speed - 0.1, 0.5)
            profile.pitch = max(profile.pitch - 1.0, -20.0)
        
        # Adjust for urgency
        if emotional_context.urgency == "urgent":
            profile.speed = min(profile.speed + 0.2, 2.0)
        elif emotional_context.urgency == "low":
            profile.speed = max(profile.speed - 0.1, 0.5)
        
        return profile
    
    def _select_best_engine(self, request: SpeechRequest, voice_profile: VoiceProfile) -> str:
        """Select the best TTS engine based on requirements"""
        # Prioritize based on quality and availability (Mozilla TTS first)
        if voice_profile.emotion != "neutral" and "azure" in self.tts_engines:
            return "azure"
        elif "mozilla_tacotron2" in self.tts_engines:
            return "mozilla_tacotron2"
        elif "mozilla_fast" in self.tts_engines:
            return "mozilla_fast"
        elif "azure" in self.tts_engines:
            return "azure"
        elif "pyttsx3" in self.tts_engines:
            return "pyttsx3"
        else:
            raise ValueError("No TTS engines available")
    
    async def _synthesize_with_engine(self, engine_name: str, text: str, voice_profile: VoiceProfile, 
                                     format: str, sample_rate: int) -> tuple[bytes, int]:
        """Synthesize speech using specified engine"""
        
        if engine_name.startswith("mozilla"):
            return await self._synthesize_mozilla(engine_name, text, voice_profile, format, sample_rate)
        elif engine_name == "azure":
            return await self._synthesize_azure(text, voice_profile, format, sample_rate)
        elif engine_name == "pyttsx3":
            return await self._synthesize_pyttsx3(text, voice_profile, format, sample_rate)
        else:
            raise ValueError(f"Unknown TTS engine: {engine_name}")
    
    async def _synthesize_mozilla(self, engine_name: str, text: str, voice_profile: VoiceProfile, 
                                 format: str, sample_rate: int) -> tuple[bytes, int]:
        """Synthesize using Mozilla TTS"""
        try:
            engine = self.tts_engines[engine_name]
            
            # Create temporary file for output
            temp_file = f"/tmp/mozilla_tts_{int(time.time())}_{hash(text)}.wav"
            
            def _synthesize():
                try:
                    # For multi-speaker models, choose speaker based on gender
                    if hasattr(engine, 'speakers') and engine.speakers:
                        speaker = None
                        if voice_profile.gender == "female":
                            speaker = next((s for s in engine.speakers if "female" in s.lower()), None)
                        elif voice_profile.gender == "male":
                            speaker = next((s for s in engine.speakers if "male" in s.lower()), None)
                        
                        if speaker:
                            engine.tts_to_file(text, file_path=temp_file, speaker=speaker)
                        else:
                            engine.tts_to_file(text, file_path=temp_file)
                    else:
                        engine.tts_to_file(text, file_path=temp_file)
                    
                except Exception as e:
                    logger.error(f"Mozilla TTS synthesis failed: {e}")
                    raise
            
            # Run synthesis in thread pool
            await asyncio.get_event_loop().run_in_executor(executor, _synthesize)
            
            # Post-process audio if needed
            if AUDIO_PROCESSING_AVAILABLE and (voice_profile.speed != 1.0 or voice_profile.pitch != 0.0):
                await self._post_process_audio(temp_file, voice_profile, sample_rate)
            
            # Read synthesized audio
            if AUDIO_PROCESSING_AVAILABLE:
                # Use librosa for better audio handling
                audio_data, sr = librosa.load(temp_file, sr=sample_rate)
                
                # Apply volume adjustment
                if voice_profile.volume != 0.0:
                    volume_factor = 10 ** (voice_profile.volume / 20)  # Convert dB to linear
                    audio_data = audio_data * volume_factor
                
                # Convert to bytes
                if format == "wav":
                    audio_int16 = (audio_data * 32767).astype(np.int16)
                    audio_bytes = audio_int16.tobytes()
                else:
                    # For other formats, convert using soundfile
                    with io.BytesIO() as buffer:
                        sf.write(buffer, audio_data, sr, format='wav')
                        audio_bytes = buffer.getvalue()
                
                duration_ms = int(len(audio_data) / sr * 1000)
            else:
                # Fallback: read raw file
                with open(temp_file, 'rb') as f:
                    audio_bytes = f.read()
                duration_ms = int(len(text) * 80)  # Approximate duration
            
            # Cleanup temporary file
            try:
                os.remove(temp_file)
            except Exception:
                pass
            
            return audio_bytes, duration_ms
        
        except Exception as e:
            logger.error(f"Mozilla TTS error: {e}")
            raise
    
    async def _post_process_audio(self, audio_file: str, voice_profile: VoiceProfile, target_sample_rate: int):
        """Post-process audio for speed and pitch adjustments"""
        try:
            # Load audio
            y, sr = librosa.load(audio_file, sr=None)
            
            # Apply speed change (time stretching)
            if voice_profile.speed != 1.0:
                y = librosa.effects.time_stretch(y, rate=voice_profile.speed)
            
            # Apply pitch shift
            if voice_profile.pitch != 0.0:
                # Convert pitch from Hz to semitones (approximate)
                n_steps = voice_profile.pitch / 20.0  # Rough conversion
                y = librosa.effects.pitch_shift(y, sr=sr, n_steps=n_steps)
            
            # Resample if needed
            if sr != target_sample_rate:
                y = librosa.resample(y, orig_sr=sr, target_sr=target_sample_rate)
                sr = target_sample_rate
            
            # Save processed audio
            sf.write(audio_file, y, sr)
            
        except Exception as e:
            logger.warning(f"Audio post-processing failed: {e}")
    
    async def _synthesize_azure(self, text: str, voice_profile: VoiceProfile, 
                               format: str, sample_rate: int) -> tuple[bytes, int]:
        """Synthesize using Azure Cognitive Services"""
        try:
            speech_config = self.tts_engines['azure']
            
            # Configure voice with emotional context
            voice_name = "en-US-AriaNeural"  # Default to Aria voice
            if voice_profile.gender == "male":
                voice_name = "en-US-GuyNeural"
            elif voice_profile.gender == "female":
                voice_name = "en-US-JennyNeural"
            
            speech_config.speech_synthesis_voice_name = voice_name
            
            # Configure audio format
            if format == "wav":
                speech_config.set_speech_synthesis_output_format(
                    speechsdk.SpeechSynthesisOutputFormat.Riff16Khz16BitMonoPcm
                )
            else:
                speech_config.set_speech_synthesis_output_format(
                    speechsdk.SpeechSynthesisOutputFormat.Audio16Khz128KBitRateMonoMp3
                )
            
            # Create synthesizer
            synthesizer = speechsdk.SpeechSynthesizer(speech_config=speech_config, audio_config=None)
            
            # Add SSML for emotional and prosodic control
            ssml_text = f'''
            <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="{voice_profile.language}">
                <voice name="{voice_name}">
                    <prosody rate="{voice_profile.speed}" pitch="{voice_profile.pitch:+.1f}Hz" volume="{voice_profile.volume:+.1f}dB">
                        <mstts:express-as style="{voice_profile.style}" styledegree="0.8">
                            {text}
                        </mstts:express-as>
                    </prosody>
                </voice>
            </speak>
            '''
            
            # Synthesize
            result = await asyncio.get_event_loop().run_in_executor(
                executor,
                lambda: synthesizer.speak_ssml(ssml_text)
            )
            
            if result.reason == speechsdk.ResultReason.SynthesizingAudioCompleted:
                duration_ms = int(len(text) * 60)  # Approximate duration
                return result.audio_data, duration_ms
            else:
                raise Exception(f"Azure TTS failed: {result.reason}")
        
        except Exception as e:
            logger.error(f"Azure TTS error: {e}")
            raise
    
    async def _synthesize_pyttsx3(self, text: str, voice_profile: VoiceProfile, 
                                 format: str, sample_rate: int) -> tuple[bytes, int]:
        """Synthesize using pyttsx3 (offline)"""
        try:
            engine = self.tts_engines['pyttsx3']
            
            # Configure voice properties
            engine.setProperty('rate', int(voice_profile.speed * 200))  # Convert to WPM
            engine.setProperty('volume', max(0.0, min(1.0, (voice_profile.volume + 96) / 112)))  # Convert to 0-1 range
            
            # Try to set voice gender
            voices = engine.getProperty('voices')
            if voices:
                for voice in voices:
                    if (voice_profile.gender == "male" and "male" in voice.name.lower()) or \
                       (voice_profile.gender == "female" and "female" in voice.name.lower()):
                        engine.setProperty('voice', voice.id)
                        break
            
            # Create temporary audio file
            temp_file = f"/tmp/tts_{int(time.time())}.wav"
            
            # Synthesize to file
            await asyncio.get_event_loop().run_in_executor(
                executor,
                lambda: self._pyttsx3_to_file(engine, text, temp_file)
            )
            
            # Read audio data
            if AUDIO_PROCESSING_AVAILABLE:
                # Use librosa for better audio processing
                audio_data, sr = librosa.load(temp_file, sr=sample_rate)
                
                # Convert to bytes
                if format == "wav":
                    # Convert to 16-bit PCM
                    audio_int16 = (audio_data * 32767).astype(np.int16)
                    audio_bytes = audio_int16.tobytes()
                else:
                    # For other formats, we'll stick with WAV for pyttsx3
                    audio_int16 = (audio_data * 32767).astype(np.int16)
                    audio_bytes = audio_int16.tobytes()
                
                duration_ms = int(len(audio_data) / sr * 1000)
            else:
                # Fallback: read raw file
                with open(temp_file, 'rb') as f:
                    audio_bytes = f.read()
                duration_ms = int(len(text) * 60)  # Approximate
            
            # Cleanup
            os.remove(temp_file)
            
            return audio_bytes, duration_ms
        
        except Exception as e:
            logger.error(f"pyttsx3 TTS error: {e}")
            raise
    
    def _pyttsx3_to_file(self, engine, text: str, filename: str):
        """Helper to synthesize pyttsx3 to file"""
        engine.save_to_file(text, filename)
        engine.runAndWait()
    
    async def _cache_synthesis_result(self, request: SpeechRequest, response: SpeechResponse):
        """Cache synthesis result in Redis"""
        try:
            cache_key = f"tts_cache:{hash(request.text)}:{request.voice_profile.voice_id if request.voice_profile else 'default'}"
            cache_data = {
                'audio_data': response.audio_data,
                'duration_ms': response.duration_ms,
                'format': response.format,
                'sample_rate': response.sample_rate,
                'engine_used': response.engine_used,
                'created_at': datetime.now().isoformat()
            }
            
            redis_client.setex(cache_key, 3600, json.dumps(cache_data))  # 1 hour TTL
        except Exception as e:
            logger.warning(f"Failed to cache TTS result: {e}")
    
    async def _synthesis_worker(self):
        """Background worker for processing synthesis queue"""
        while True:
            try:
                # This would handle queued synthesis requests
                await asyncio.sleep(0.1)
            except Exception as e:
                logger.error(f"Synthesis worker error: {e}")

# FastAPI endpoints

@app.get("/")
async def root():
    """Health check endpoint"""
    engines_status = {}
    for engine_name in ['pyttsx3', 'google', 'azure']:
        engines_status[f'{engine_name}_tts'] = engine_name in app.state.tts_engines
    
    return {
        "service": "ARIA AI Voice Synthesis Service",
        "status": "healthy",
        "version": "1.0.0",
        "engines": engines_status,
        "timestamp": datetime.now().isoformat()
    }

@app.post("/synthesize", response_model=SpeechResponse)
async def synthesize_speech(request: SpeechRequest, emotional_context: Optional[EmotionalContext] = None):
    """Synthesize speech from text with emotional intelligence"""
    try:
        response = await app.state.synthesis_manager.synthesize_speech(request, emotional_context)
        
        # Broadcast to WebSocket connections if streaming
        if request.stream:
            await app.state.connection_manager.broadcast_to_session(
                request.session_id,
                {
                    'type': 'speech_synthesized',
                    'participant_id': request.participant_id,
                    'audio_data': response.audio_data,
                    'duration_ms': response.duration_ms,
                    'timestamp': datetime.now().isoformat()
                }
            )
        
        return response
    
    except Exception as e:
        logger.error(f"Error in speech synthesis endpoint: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.websocket("/ws/voice/{session_id}")
async def websocket_voice_synthesis(websocket: WebSocket, session_id: str):
    """WebSocket endpoint for real-time voice synthesis streaming"""
    await app.state.connection_manager.connect(websocket, session_id)
    
    try:
        # Send welcome message
        await websocket.send_json({
            'type': 'voice_synthesis_connected',
            'session_id': session_id,
            'message': 'Voice synthesis WebSocket connected'
        })
        
        # Listen for synthesis requests
        while True:
            data = await websocket.receive_json()
            
            if data.get('type') == 'synthesize_request':
                # Create synthesis request
                request = SpeechRequest(
                    session_id=session_id,
                    participant_id=data.get('participant_id', 'ai_avatar'),
                    text=data.get('text', ''),
                    stream=True
                )
                
                # Add emotional context if provided
                emotional_context = None
                if 'emotional_context' in data:
                    emotional_context = EmotionalContext(**data['emotional_context'])
                
                # Process synthesis
                try:
                    response = await app.state.synthesis_manager.synthesize_speech(request, emotional_context)
                    
                    # Send response back
                    await websocket.send_json({
                        'type': 'synthesis_response',
                        'audio_data': response.audio_data,
                        'duration_ms': response.duration_ms,
                        'format': response.format,
                        'engine_used': response.engine_used
                    })
                
                except Exception as e:
                    await websocket.send_json({
                        'type': 'synthesis_error',
                        'error': str(e)
                    })
    
    except WebSocketDisconnect:
        logger.info(f"Voice synthesis WebSocket disconnected for session {session_id}")
    except Exception as e:
        logger.error(f"Voice synthesis WebSocket error: {e}")
    finally:
        app.state.connection_manager.disconnect(websocket, session_id)

@app.get("/voices")
async def list_available_voices():
    """List available voice profiles and engines"""
    available_engines = list(app.state.tts_engines.keys())
    voice_profiles = app.state.synthesis_manager.voice_profiles
    
    return {
        'available_engines': available_engines,
        'voice_profiles': {k: v.dict() for k, v in voice_profiles.items()},
        'supported_formats': ['wav', 'mp3', 'pcm'],
        'supported_languages': ['en-US', 'en-GB', 'es-US', 'fr-FR', 'de-DE']
    }

@app.post("/voices/profile", response_model=VoiceProfile)
async def create_voice_profile(profile: VoiceProfile):
    """Create or update a voice profile"""
    app.state.synthesis_manager.voice_profiles[profile.voice_id] = profile
    
    # Cache in Redis
    redis_client.setex(
        f"voice_profile:{profile.voice_id}",
        86400,  # 24 hours
        profile.json()
    )
    
    return profile

@app.get("/health")
async def health_check():
    """Detailed health check"""
    try:
        redis_status = "healthy" if redis_client.ping() else "unhealthy"
        
        components = {
            "redis": redis_status,
            "pyttsx3": "available" if 'pyttsx3' in app.state.tts_engines else "unavailable",
            "google_tts": "available" if 'google' in app.state.tts_engines else "unavailable",
            "azure_tts": "available" if 'azure' in app.state.tts_engines else "unavailable",
            "voice_synthesis_manager": "healthy",
            "connection_manager": "healthy"
        }
        
        overall_status = "healthy" if redis_status == "healthy" else "degraded"
        
        return {
            "status": overall_status,
            "components": components,
            "active_sessions": len(app.state.connection_manager.active_connections),
            "voice_profiles_count": len(app.state.synthesis_manager.voice_profiles),
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
    
    # Use port 8007 to avoid conflict with other services
    port = 8007
    
    # Check if SSL certificates exist
    ssl_cert_path = "../../ssl-certs/aria-cert.pem"
    ssl_key_path = "../../ssl-certs/aria-key.pem"
    
    if os.path.exists(ssl_cert_path) and os.path.exists(ssl_key_path):
        # Run with SSL
        uvicorn.run(
            app,
            host="0.0.0.0",
            port=port,
            log_level="info",
            ssl_keyfile=ssl_key_path,
            ssl_certfile=ssl_cert_path
        )
    else:
        # Fallback to HTTP
        print("Warning: SSL certificates not found, running with HTTP")
        uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")
