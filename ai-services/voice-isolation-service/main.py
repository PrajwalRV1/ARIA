#!/usr/bin/env python3
"""
Voice Isolation Service for ARIA Interview Platform

Prevents audio feedback when recruiter and AI avatar join from the same device by:
- Creating separate audio channels for each participant
- Implementing acoustic echo cancellation (AEC)
- Managing audio routing to prevent loops
- Providing real-time audio processing and filtering
- Supporting multiple simultaneous sessions
"""

import asyncio
import json
import logging
import numpy as np
import threading
import time
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Dict, List, Optional, Any, Tuple
from dataclasses import dataclass, field
from enum import Enum

# Audio processing imports
try:
    import pyaudio
    import wave
    import audioop
    from scipy import signal
    from scipy.fft import fft, ifft
    import librosa
    AUDIO_AVAILABLE = True
except ImportError:
    AUDIO_AVAILABLE = False
    logging.warning("Audio processing libraries not available")

# WebRTC and real-time communication
try:
    import websockets
    import aiohttp
    from fastapi import FastAPI, WebSocket, HTTPException, BackgroundTasks
    from fastapi.middleware.cors import CORSMiddleware
    from pydantic import BaseModel, Field
    WEB_AVAILABLE = True
except ImportError:
    WEB_AVAILABLE = False
    logging.warning("Web libraries not available")

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
    format: int = pyaudio.paInt16 if AUDIO_AVAILABLE else 0
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
    input_stream: Optional[Any] = None
    output_stream: Optional[Any] = None
    audio_buffer: List[bytes] = field(default_factory=list)
    echo_history: List[np.ndarray] = field(default_factory=list)
    noise_profile: Optional[np.ndarray] = None
    last_activity: datetime = field(default_factory=datetime.now)

@dataclass
class IsolationSession:
    """Manages voice isolation for an interview session"""
    session_id: str
    channels: Dict[str, ParticipantChannel] = field(default_factory=dict)
    mixing_matrix: Optional[np.ndarray] = None
    feedback_detectors: Dict[str, Any] = field(default_factory=dict)
    audio_router: Optional['AudioRouter'] = None
    is_active: bool = True
    created_at: datetime = field(default_factory=datetime.now)

# ==================== ACOUSTIC ECHO CANCELLATION ====================

class AcousticEchoCanceller:
    """Advanced AEC implementation for real-time echo cancellation"""
    
    def __init__(self, filter_length: int = 512):
        self.filter_length = filter_length
        self.adaptive_filter = np.zeros(filter_length)
        self.reference_buffer = np.zeros(filter_length)
        self.step_size = 0.01
        self.regularization = 1e-6
        
    def process(self, near_end: np.ndarray, far_end: np.ndarray) -> np.ndarray:
        """
        Process audio to cancel echo
        
        Args:
            near_end: Audio from microphone (with echo)
            far_end: Reference audio (loudspeaker signal)
            
        Returns:
            Echo-cancelled audio
        """
        try:
            # Ensure arrays are 1D
            near_end = np.asarray(near_end).flatten()
            far_end = np.asarray(far_end).flatten()
            
            output = np.zeros_like(near_end)
            
            for i in range(len(near_end)):
                # Update reference buffer
                self.reference_buffer[1:] = self.reference_buffer[:-1]
                self.reference_buffer[0] = far_end[i] if i < len(far_end) else 0.0
                
                # Estimate echo
                echo_estimate = np.dot(self.adaptive_filter, self.reference_buffer)
                
                # Calculate error signal (echo-cancelled output)
                error = near_end[i] - echo_estimate
                output[i] = error
                
                # Adapt filter using NLMS algorithm
                norm_factor = np.dot(self.reference_buffer, self.reference_buffer) + self.regularization
                self.adaptive_filter += (self.step_size * error / norm_factor) * self.reference_buffer
                
                # Prevent filter from becoming unstable
                self.adaptive_filter = np.clip(self.adaptive_filter, -1.0, 1.0)
            
            return output
            
        except Exception as e:
            logger.error(f"Error in AEC processing: {e}")
            return near_end  # Return original audio on error

class NoiseSupressor:
    """Real-time noise suppression using spectral subtraction"""
    
    def __init__(self, alpha: float = 2.0, beta: float = 0.01):
        self.alpha = alpha  # Over-subtraction factor
        self.beta = beta    # Spectral floor factor
        self.noise_spectrum = None
        self.frame_size = 512
        self.hop_size = 256
        
    def estimate_noise(self, audio: np.ndarray, frames_for_estimation: int = 10):
        """Estimate noise spectrum from initial frames"""
        try:
            if len(audio) < self.frame_size * frames_for_estimation:
                return
            
            # Use initial frames to estimate noise
            noise_frames = []
            for i in range(frames_for_estimation):
                start = i * self.hop_size
                end = start + self.frame_size
                if end <= len(audio):
                    frame = audio[start:end]
                    # Apply window
                    windowed = frame * np.hanning(len(frame))
                    spectrum = np.abs(fft(windowed))
                    noise_frames.append(spectrum[:len(spectrum)//2])
            
            if noise_frames:
                self.noise_spectrum = np.mean(noise_frames, axis=0)
                
        except Exception as e:
            logger.error(f"Error estimating noise: {e}")
    
    def suppress_noise(self, audio: np.ndarray) -> np.ndarray:
        """Apply noise suppression to audio"""
        try:
            if self.noise_spectrum is None:
                self.estimate_noise(audio)
                return audio
            
            # Process audio in overlapping frames
            output = np.zeros_like(audio)
            
            for i in range(0, len(audio) - self.frame_size, self.hop_size):
                frame = audio[i:i + self.frame_size]
                windowed = frame * np.hanning(self.frame_size)
                
                # FFT
                spectrum = fft(windowed)
                magnitude = np.abs(spectrum)
                phase = np.angle(spectrum)
                
                # Only process first half (positive frequencies)
                half_size = len(magnitude) // 2
                magnitude_half = magnitude[:half_size]
                
                # Spectral subtraction
                if len(self.noise_spectrum) == half_size:
                    # Subtract noise spectrum
                    clean_magnitude = magnitude_half - self.alpha * self.noise_spectrum
                    
                    # Apply spectral floor
                    clean_magnitude = np.maximum(clean_magnitude, 
                                                self.beta * magnitude_half)
                    
                    # Reconstruct full spectrum
                    clean_spectrum = np.concatenate([clean_magnitude, 
                                                   np.flip(clean_magnitude[1:-1])])
                    
                    # Apply phase and inverse FFT
                    clean_complex = clean_spectrum[:len(phase)] * np.exp(1j * phase)
                    clean_frame = np.real(ifft(clean_complex))
                    
                    # Overlap-add
                    output[i:i + self.frame_size] += clean_frame * np.hanning(self.frame_size)
                else:
                    # If noise spectrum size doesn't match, return original
                    output[i:i + self.frame_size] += windowed
            
            return output
            
        except Exception as e:
            logger.error(f"Error in noise suppression: {e}")
            return audio

class FeedbackDetector:
    """Detects audio feedback loops in real-time"""
    
    def __init__(self, threshold: float = 0.8, window_size: int = 2048):
        self.threshold = threshold
        self.window_size = window_size
        self.correlation_history = []
        self.max_history = 10
        
    def detect_feedback(self, input_audio: np.ndarray, output_audio: np.ndarray) -> bool:
        """
        Detect if feedback loop is occurring
        
        Args:
            input_audio: Audio being recorded
            output_audio: Audio being played
            
        Returns:
            True if feedback detected
        """
        try:
            if len(input_audio) != len(output_audio) or len(input_audio) < self.window_size:
                return False
            
            # Calculate cross-correlation
            correlation = np.corrcoef(input_audio, output_audio)[0, 1]
            
            if not np.isnan(correlation):
                self.correlation_history.append(abs(correlation))
                
                # Keep history limited
                if len(self.correlation_history) > self.max_history:
                    self.correlation_history.pop(0)
                
                # Check if recent correlations are high
                recent_avg = np.mean(self.correlation_history[-5:])
                if recent_avg > self.threshold:
                    logger.warning(f"Feedback detected! Correlation: {recent_avg:.3f}")
                    return True
            
            return False
            
        except Exception as e:
            logger.error(f"Error in feedback detection: {e}")
            return False
    
    def reset(self):
        """Reset feedback detection history"""
        self.correlation_history.clear()

# ==================== AUDIO ROUTER ====================

class AudioRouter:
    """Routes audio between participants while preventing feedback"""
    
    def __init__(self, session: IsolationSession):
        self.session = session
        self.routing_rules: Dict[str, List[str]] = {}
        self.volume_matrix: Dict[Tuple[str, str], float] = {}
        self.mute_states: Dict[str, bool] = {}
        
        # Initialize default routing rules
        self._setup_default_routing()
    
    def _setup_default_routing(self):
        """Set up default audio routing rules"""
        
        # Find participant channels
        candidate_id = None
        recruiter_id = None
        ai_avatar_id = None
        
        for pid, channel in self.session.channels.items():
            if channel.participant_type == ParticipantType.CANDIDATE:
                candidate_id = pid
            elif channel.participant_type == ParticipantType.RECRUITER:
                recruiter_id = pid
            elif channel.participant_type == ParticipantType.AI_AVATAR:
                ai_avatar_id = pid
        
        # Set up routing rules
        if candidate_id:
            # Candidate hears recruiter and AI avatar
            self.routing_rules[candidate_id] = []
            if recruiter_id:
                self.routing_rules[candidate_id].append(recruiter_id)
            if ai_avatar_id:
                self.routing_rules[candidate_id].append(ai_avatar_id)
        
        if recruiter_id:
            # Recruiter hears candidate and AI avatar (but reduced AI volume)
            self.routing_rules[recruiter_id] = []
            if candidate_id:
                self.routing_rules[recruiter_id].append(candidate_id)
            if ai_avatar_id:
                self.routing_rules[recruiter_id].append(ai_avatar_id)
                # Reduce AI avatar volume for recruiter to prevent feedback
                self.volume_matrix[(ai_avatar_id, recruiter_id)] = 0.3
        
        if ai_avatar_id:
            # AI avatar only hears candidate (not recruiter to prevent feedback)
            self.routing_rules[ai_avatar_id] = []
            if candidate_id:
                self.routing_rules[ai_avatar_id].append(candidate_id)
        
        logger.info(f"Set up audio routing for session {self.session.session_id}")
    
    def route_audio(self, source_id: str, audio_data: np.ndarray) -> Dict[str, np.ndarray]:
        """
        Route audio from source to appropriate destinations
        
        Args:
            source_id: ID of audio source participant
            audio_data: Audio data to route
            
        Returns:
            Dictionary mapping destination IDs to processed audio
        """
        routed_audio = {}
        
        try:
            # Check if source is muted
            if self.mute_states.get(source_id, False):
                return routed_audio
            
            # Route to each destination
            for dest_id, dest_channel in self.session.channels.items():
                if dest_id == source_id:
                    continue  # Don't route to self
                
                if dest_id in self.routing_rules.get(source_id, []):
                    # Get volume for this route
                    volume = self.volume_matrix.get((source_id, dest_id), 1.0)
                    
                    # Apply volume
                    processed_audio = audio_data * volume
                    
                    # Apply additional processing based on destination type
                    if dest_channel.participant_type == ParticipantType.RECRUITER:
                        # Additional processing for recruiter to prevent feedback
                        processed_audio = self._apply_recruiter_processing(processed_audio)
                    
                    routed_audio[dest_id] = processed_audio
            
            return routed_audio
            
        except Exception as e:
            logger.error(f"Error routing audio from {source_id}: {e}")
            return {}
    
    def _apply_recruiter_processing(self, audio: np.ndarray) -> np.ndarray:
        """Apply special processing for audio sent to recruiter"""
        try:
            # Apply light compression to prevent peaks
            compressed = np.tanh(audio * 0.8) * 0.9
            
            # Apply slight high-pass filter to reduce low-frequency rumble
            if len(compressed) > 256:
                # Simple high-pass filter
                b, a = signal.butter(2, 0.02, 'high')
                filtered = signal.filtfilt(b, a, compressed)
                return filtered
            
            return compressed
            
        except Exception as e:
            logger.error(f"Error in recruiter audio processing: {e}")
            return audio
    
    def set_mute(self, participant_id: str, muted: bool):
        """Mute or unmute a participant"""
        self.mute_states[participant_id] = muted
        logger.info(f"Participant {participant_id} {'muted' if muted else 'unmuted'}")
    
    def set_volume(self, source_id: str, dest_id: str, volume: float):
        """Set volume for a specific audio route"""
        volume = max(0.0, min(1.0, volume))  # Clamp to [0, 1]
        self.volume_matrix[(source_id, dest_id)] = volume
        logger.info(f"Set volume from {source_id} to {dest_id}: {volume}")

# ==================== VOICE ISOLATION SERVICE ====================

class VoiceIsolationService:
    """Main voice isolation service"""
    
    def __init__(self):
        self.sessions: Dict[str, IsolationSession] = {}
        self.audio_processors: Dict[str, Any] = {}
        self.pyaudio_instance = None
        
        if AUDIO_AVAILABLE:
            self.pyaudio_instance = pyaudio.PyAudio()
            logger.info("Voice Isolation Service initialized with audio support")
        else:
            logger.warning("Voice Isolation Service initialized without audio support")
    
    async def create_isolation_session(
        self,
        session_id: str,
        participants: List[Dict[str, Any]]
    ) -> IsolationSession:
        """Create a new voice isolation session"""
        
        try:
            logger.info(f"Creating voice isolation session: {session_id}")
            
            # Create isolation session
            isolation_session = IsolationSession(session_id=session_id)
            
            # Create participant channels
            for participant in participants:
                channel = ParticipantChannel(
                    participant_id=participant['id'],
                    participant_type=ParticipantType(participant['type']),
                    session_id=session_id,
                    config=AudioConfig(**participant.get('audio_config', {}))
                )
                
                # Initialize audio processing components
                await self._initialize_channel_processing(channel)
                
                isolation_session.channels[participant['id']] = channel
            
            # Set up audio router
            isolation_session.audio_router = AudioRouter(isolation_session)
            
            # Store session
            self.sessions[session_id] = isolation_session
            
            logger.info(f"Created isolation session with {len(participants)} participants")
            return isolation_session
            
        except Exception as e:
            logger.error(f"Error creating isolation session: {e}")
            raise
    
    async def _initialize_channel_processing(self, channel: ParticipantChannel):
        """Initialize audio processing for a channel"""
        
        try:
            # Create AEC if enabled
            if channel.config.enable_aec:
                aec = AcousticEchoCanceller()
                self.audio_processors[f"{channel.participant_id}_aec"] = aec
            
            # Create noise suppressor if enabled
            if channel.config.enable_noise_suppression:
                noise_suppressor = NoiseSupressor()
                self.audio_processors[f"{channel.participant_id}_ns"] = noise_suppressor
            
            # Create feedback detector
            feedback_detector = FeedbackDetector()
            self.audio_processors[f"{channel.participant_id}_fd"] = feedback_detector
            
            logger.debug(f"Initialized processing for channel {channel.participant_id}")
            
        except Exception as e:
            logger.error(f"Error initializing channel processing: {e}")
    
    async def process_audio_frame(
        self,
        session_id: str,
        participant_id: str,
        audio_data: bytes
    ) -> Dict[str, bytes]:
        """
        Process incoming audio frame and route to appropriate participants
        
        Args:
            session_id: Interview session ID
            participant_id: ID of participant sending audio
            audio_data: Raw audio data
            
        Returns:
            Dictionary mapping participant IDs to processed audio for them
        """
        
        try:
            if session_id not in self.sessions:
                logger.error(f"Session {session_id} not found")
                return {}
            
            session = self.sessions[session_id]
            if participant_id not in session.channels:
                logger.error(f"Participant {participant_id} not found in session")
                return {}
            
            channel = session.channels[participant_id]
            
            # Convert bytes to numpy array
            audio_array = self._bytes_to_array(audio_data, channel.config)
            
            # Apply audio processing
            processed_audio = await self._apply_audio_processing(
                participant_id, audio_array, session
            )
            
            # Route audio to other participants
            routed_audio = session.audio_router.route_audio(participant_id, processed_audio)
            
            # Convert back to bytes for transmission
            output_audio = {}
            for dest_id, audio_array in routed_audio.items():
                dest_channel = session.channels[dest_id]
                audio_bytes = self._array_to_bytes(audio_array, dest_channel.config)
                output_audio[dest_id] = audio_bytes
            
            return output_audio
            
        except Exception as e:
            logger.error(f"Error processing audio frame: {e}")
            return {}
    
    async def _apply_audio_processing(
        self,
        participant_id: str,
        audio_array: np.ndarray,
        session: IsolationSession
    ) -> np.ndarray:
        """Apply audio processing to incoming audio"""
        
        try:
            processed = audio_array.copy()
            
            # Apply noise suppression
            if f"{participant_id}_ns" in self.audio_processors:
                noise_suppressor = self.audio_processors[f"{participant_id}_ns"]
                processed = noise_suppressor.suppress_noise(processed)
            
            # Apply AEC (requires reference signal)
            if f"{participant_id}_aec" in self.audio_processors:
                aec = self.audio_processors[f"{participant_id}_aec"]
                # For simplicity, we'll skip AEC here since it requires reference signal
                # In a full implementation, you'd get the reference from the output mixer
                pass
            
            # Check for feedback
            if f"{participant_id}_fd" in self.audio_processors:
                feedback_detector = self.audio_processors[f"{participant_id}_fd"]
                # This would require output audio for comparison
                # feedback_detector.detect_feedback(processed, reference_audio)
                pass
            
            return processed
            
        except Exception as e:
            logger.error(f"Error in audio processing: {e}")
            return audio_array
    
    def _bytes_to_array(self, audio_bytes: bytes, config: AudioConfig) -> np.ndarray:
        """Convert audio bytes to numpy array"""
        try:
            if config.format == pyaudio.paInt16 if AUDIO_AVAILABLE else 0:
                audio_array = np.frombuffer(audio_bytes, dtype=np.int16)
                # Convert to float [-1, 1]
                return audio_array.astype(np.float32) / 32768.0
            else:
                # Default handling
                return np.frombuffer(audio_bytes, dtype=np.float32)
        except Exception as e:
            logger.error(f"Error converting bytes to array: {e}")
            return np.array([])
    
    def _array_to_bytes(self, audio_array: np.ndarray, config: AudioConfig) -> bytes:
        """Convert numpy array to audio bytes"""
        try:
            if config.format == pyaudio.paInt16 if AUDIO_AVAILABLE else 0:
                # Convert from float [-1, 1] to int16
                int16_array = (audio_array * 32767).astype(np.int16)
                return int16_array.tobytes()
            else:
                # Default handling
                return audio_array.astype(np.float32).tobytes()
        except Exception as e:
            logger.error(f"Error converting array to bytes: {e}")
            return b''
    
    async def set_participant_mute(self, session_id: str, participant_id: str, muted: bool):
        """Mute or unmute a participant"""
        
        if session_id in self.sessions:
            session = self.sessions[session_id]
            if session.audio_router:
                session.audio_router.set_mute(participant_id, muted)
                return True
        return False
    
    async def adjust_volume(self, session_id: str, source_id: str, dest_id: str, volume: float):
        """Adjust volume between specific participants"""
        
        if session_id in self.sessions:
            session = self.sessions[session_id]
            if session.audio_router:
                session.audio_router.set_volume(source_id, dest_id, volume)
                return True
        return False
    
    async def get_session_status(self, session_id: str) -> Dict[str, Any]:
        """Get status of a voice isolation session"""
        
        if session_id not in self.sessions:
            return {"error": "Session not found"}
        
        session = self.sessions[session_id]
        
        status = {
            "session_id": session_id,
            "is_active": session.is_active,
            "participant_count": len(session.channels),
            "participants": {},
            "created_at": session.created_at.isoformat()
        }
        
        # Add participant status
        for pid, channel in session.channels.items():
            status["participants"][pid] = {
                "type": channel.participant_type.value,
                "state": channel.state.value,
                "muted": session.audio_router.mute_states.get(pid, False) if session.audio_router else False,
                "last_activity": channel.last_activity.isoformat()
            }
        
        return status
    
    async def cleanup_session(self, session_id: str):
        """Clean up a voice isolation session"""
        
        if session_id in self.sessions:
            session = self.sessions[session_id]
            
            # Close audio streams
            for channel in session.channels.values():
                if channel.input_stream:
                    try:
                        channel.input_stream.close()
                    except:
                        pass
                if channel.output_stream:
                    try:
                        channel.output_stream.close()
                    except:
                        pass
            
            # Remove from active sessions
            del self.sessions[session_id]
            
            # Clean up audio processors
            processors_to_remove = [key for key in self.audio_processors.keys() 
                                  if any(pid in key for pid in session.channels.keys())]
            for key in processors_to_remove:
                del self.audio_processors[key]
            
            logger.info(f"Cleaned up voice isolation session: {session_id}")
    
    def __del__(self):
        """Cleanup on destruction"""
        if self.pyaudio_instance:
            try:
                self.pyaudio_instance.terminate()
            except:
                pass

# ==================== FASTAPI APPLICATION ====================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager"""
    # Startup
    logger.info("Starting Voice Isolation Service")
    
    # Initialize voice isolation service
    app.state.voice_isolation = VoiceIsolationService()
    
    logger.info("Voice Isolation Service startup complete")
    yield
    
    # Shutdown
    logger.info("Shutting down Voice Isolation Service")
    
    # Clean up all sessions
    for session_id in list(app.state.voice_isolation.sessions.keys()):
        await app.state.voice_isolation.cleanup_session(session_id)
    
    logger.info("Voice Isolation Service shutdown complete")

# Create FastAPI application
app = FastAPI(
    title="ARIA Voice Isolation Service",
    version="1.0.0",
    description="Voice isolation and feedback prevention for ARIA interview platform",
    lifespan=lifespan
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Request/Response models
class CreateSessionRequest(BaseModel):
    session_id: str
    participants: List[Dict[str, Any]]

class AudioFrameRequest(BaseModel):
    session_id: str
    participant_id: str
    audio_data: bytes

class MuteRequest(BaseModel):
    session_id: str
    participant_id: str
    muted: bool

class VolumeRequest(BaseModel):
    session_id: str
    source_id: str
    dest_id: str
    volume: float = Field(..., ge=0.0, le=1.0)

# API Endpoints

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "service": "Voice Isolation Service",
        "version": "1.0.0",
        "status": "healthy",
        "audio_support": AUDIO_AVAILABLE,
        "active_sessions": len(app.state.voice_isolation.sessions),
        "timestamp": datetime.now().isoformat()
    }

@app.post("/sessions")
async def create_session(request: CreateSessionRequest):
    """Create a new voice isolation session"""
    
    try:
        session = await app.state.voice_isolation.create_isolation_session(
            request.session_id,
            request.participants
        )
        
        return {
            "status": "success",
            "session_id": session.session_id,
            "participant_count": len(session.channels),
            "created_at": session.created_at.isoformat()
        }
        
    except Exception as e:
        logger.error(f"Error creating session: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/sessions/{session_id}/status")
async def get_session_status(session_id: str):
    """Get status of a voice isolation session"""
    
    status = await app.state.voice_isolation.get_session_status(session_id)
    
    if "error" in status:
        raise HTTPException(status_code=404, detail=status["error"])
    
    return status

@app.post("/sessions/{session_id}/mute")
async def set_mute(session_id: str, request: MuteRequest):
    """Mute or unmute a participant"""
    
    success = await app.state.voice_isolation.set_participant_mute(
        session_id, request.participant_id, request.muted
    )
    
    if not success:
        raise HTTPException(status_code=404, detail="Session or participant not found")
    
    return {
        "status": "success",
        "participant_id": request.participant_id,
        "muted": request.muted
    }

@app.post("/sessions/{session_id}/volume")
async def set_volume(session_id: str, request: VolumeRequest):
    """Adjust volume between participants"""
    
    success = await app.state.voice_isolation.adjust_volume(
        session_id, request.source_id, request.dest_id, request.volume
    )
    
    if not success:
        raise HTTPException(status_code=404, detail="Session not found")
    
    return {
        "status": "success",
        "source_id": request.source_id,
        "dest_id": request.dest_id,
        "volume": request.volume
    }

@app.delete("/sessions/{session_id}")
async def cleanup_session(session_id: str):
    """Clean up a voice isolation session"""
    
    await app.state.voice_isolation.cleanup_session(session_id)
    
    return {
        "status": "success",
        "session_id": session_id,
        "message": "Session cleaned up"
    }

@app.get("/sessions")
async def list_sessions():
    """List all active voice isolation sessions"""
    
    sessions = []
    for session_id, session in app.state.voice_isolation.sessions.items():
        sessions.append({
            "session_id": session_id,
            "participant_count": len(session.channels),
            "is_active": session.is_active,
            "created_at": session.created_at.isoformat()
        })
    
    return {
        "active_sessions": sessions,
        "total_count": len(sessions)
    }

# WebSocket endpoint for real-time audio processing
@app.websocket("/ws/audio/{session_id}/{participant_id}")
async def websocket_audio(websocket: WebSocket, session_id: str, participant_id: str):
    """WebSocket endpoint for real-time audio streaming"""
    
    await websocket.accept()
    
    try:
        logger.info(f"Audio WebSocket connected: {session_id}/{participant_id}")
        
        while True:
            # Receive audio data
            audio_data = await websocket.receive_bytes()
            
            # Process audio frame
            routed_audio = await app.state.voice_isolation.process_audio_frame(
                session_id, participant_id, audio_data
            )
            
            # Send processed audio back (if any)
            if routed_audio:
                await websocket.send_json({
                    "type": "routed_audio",
                    "destinations": list(routed_audio.keys()),
                    "timestamp": datetime.now().isoformat()
                })
                
                # In a real implementation, you'd send the actual audio data
                # to the appropriate destinations via their WebSocket connections
    
    except Exception as e:
        logger.error(f"WebSocket error for {session_id}/{participant_id}: {e}")
    finally:
        logger.info(f"Audio WebSocket disconnected: {session_id}/{participant_id}")

if __name__ == "__main__":
    import uvicorn
    import os
    
    # Use port 8008 to avoid conflict with Voice Synthesis Service on 8007
    port = 8008
    
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
