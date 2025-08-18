#!/usr/bin/env python3
"""
Speech & Transcript Service for ARIA Interview Platform
Real-time speech-to-text with WebSocket streaming and transcript merging
Supports Google STT with Vosk fallback for offline processing
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Dict, Optional, Any
import asyncio
import json
import logging
import redis
import base64
from datetime import datetime, timedelta
import uuid
from contextlib import asynccontextmanager
import os
from concurrent.futures import ThreadPoolExecutor

# Speech recognition imports
try:
    from google.cloud import speech
    GOOGLE_STT_AVAILABLE = True
except ImportError:
    GOOGLE_STT_AVAILABLE = False
    logging.warning("Google Cloud Speech-to-Text not available, using Vosk only")

try:
    import vosk
    import wave
    import json as json_module
    VOSK_AVAILABLE = True
except ImportError:
    VOSK_AVAILABLE = False
    logging.warning("Vosk not available, using Google STT only")

# Audio processing
import webrtcvad
import collections
import audioop
import threading
import queue

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Redis client for session management
redis_client = redis.Redis(host='localhost', port=6379, db=1, decode_responses=True)

# Thread pool for background processing
executor = ThreadPoolExecutor(max_workers=10)

# Initialize speech recognition engines
speech_engines = {}

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize services on startup"""
    logger.info("Starting Speech & Transcript Service...")
    
    # Initialize Google STT if available
    if GOOGLE_STT_AVAILABLE and os.getenv('GOOGLE_APPLICATION_CREDENTIALS'):
        speech_engines['google'] = speech.SpeechClient()
        logger.info("Google Speech-to-Text initialized")
    
    # Initialize Vosk models if available
    if VOSK_AVAILABLE:
        model_path = os.getenv('VOSK_MODEL_PATH', '/models/vosk-model-en-us-0.22')
        if os.path.exists(model_path):
            speech_engines['vosk'] = vosk.Model(model_path)
            logger.info(f"Vosk model loaded from {model_path}")
        else:
            logger.warning(f"Vosk model not found at {model_path}")
    
    # Initialize WebSocket connection manager
    app.state.connection_manager = ConnectionManager()
    app.state.transcription_manager = TranscriptionManager()
    
    yield
    
    # Cleanup
    logger.info("Shutting down Speech & Transcript Service...")

app = FastAPI(
    title="ARIA Speech & Transcript Service",
    description="Real-time speech-to-text with WebSocket streaming and transcript merging",
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
class TranscriptMergeRequest(BaseModel):
    session_id: str
    audio_transcript: str
    code_content: str = ""
    chat_messages: List[Dict[str, Any]] = []
    timestamp: datetime
    merge_strategy: str = "chronological"  # chronological, priority, smart

class TranscriptMergeResponse(BaseModel):
    session_id: str
    merged_transcript: str
    segments: List[Dict[str, Any]]
    merge_metadata: Dict[str, Any]

class AudioChunk(BaseModel):
    session_id: str
    audio_data: str  # Base64 encoded
    format: str = "webm"
    sample_rate: int = 16000
    channels: int = 1

class TranscriptUpdate(BaseModel):
    session_id: str
    text: str
    confidence: float
    is_final: bool
    timestamp: datetime
    source: str  # "speech", "code", "chat"

class ConnectionManager:
    """Manages WebSocket connections for real-time transcript streaming"""
    
    def __init__(self):
        self.active_connections: Dict[str, List[WebSocket]] = {}
        self.session_metadata: Dict[str, Dict] = {}
    
    async def connect(self, websocket: WebSocket, session_id: str):
        await websocket.accept()
        if session_id not in self.active_connections:
            self.active_connections[session_id] = []
        self.active_connections[session_id].append(websocket)
        logger.info(f"WebSocket connected for session {session_id}")
    
    def disconnect(self, websocket: WebSocket, session_id: str):
        if session_id in self.active_connections:
            if websocket in self.active_connections[session_id]:
                self.active_connections[session_id].remove(websocket)
            if not self.active_connections[session_id]:
                del self.active_connections[session_id]
        logger.info(f"WebSocket disconnected for session {session_id}")
    
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

class TranscriptionManager:
    """Manages real-time transcription processing"""
    
    def __init__(self):
        self.audio_buffers: Dict[str, queue.Queue] = {}
        self.transcription_tasks: Dict[str, asyncio.Task] = {}
        self.vad = webrtcvad.Vad(2)  # Aggressiveness level 0-3
    
    async def start_transcription(self, session_id: str, audio_config: Dict):
        """Start transcription for a session"""
        if session_id not in self.audio_buffers:
            self.audio_buffers[session_id] = queue.Queue()
        
        # Start background transcription task
        if session_id not in self.transcription_tasks:
            task = asyncio.create_task(
                self._transcription_worker(session_id, audio_config)
            )
            self.transcription_tasks[session_id] = task
            logger.info(f"Started transcription for session {session_id}")
    
    async def process_audio_chunk(self, session_id: str, audio_data: bytes):
        """Process incoming audio chunk"""
        if session_id in self.audio_buffers:
            # Add to buffer for processing
            self.audio_buffers[session_id].put(audio_data)
        else:
            logger.warning(f"No transcription active for session {session_id}")
    
    async def stop_transcription(self, session_id: str):
        """Stop transcription for a session"""
        if session_id in self.transcription_tasks:
            self.transcription_tasks[session_id].cancel()
            del self.transcription_tasks[session_id]
        
        if session_id in self.audio_buffers:
            del self.audio_buffers[session_id]
        
        logger.info(f"Stopped transcription for session {session_id}")
    
    async def _transcription_worker(self, session_id: str, audio_config: Dict):
        """Background worker for processing audio and generating transcripts"""
        try:
            sample_rate = audio_config.get('sample_rate', 16000)
            frame_duration_ms = 30  # 30ms frames for VAD
            frame_size = int(sample_rate * frame_duration_ms / 1000)
            
            audio_buffer = collections.deque(maxlen=50)  # Keep last 1.5 seconds
            speech_frames = []
            is_speaking = False
            
            while session_id in self.transcription_tasks:
                try:
                    # Get audio chunk from buffer (with timeout)
                    audio_chunk = await asyncio.get_event_loop().run_in_executor(
                        executor, self._get_audio_chunk_timeout, session_id, 0.1
                    )
                    
                    if audio_chunk is None:
                        continue
                    
                    # Process audio for VAD
                    # Convert to 16-bit PCM if needed
                    pcm_data = self._convert_to_pcm(audio_chunk, sample_rate)
                    
                    # Split into frames for VAD
                    frames = self._split_into_frames(pcm_data, frame_size)
                    
                    for frame in frames:
                        audio_buffer.append(frame)
                        
                        # Voice Activity Detection
                        is_speech = self._is_speech_frame(frame, sample_rate)
                        
                        if is_speech and not is_speaking:
                            # Start of speech
                            is_speaking = True
                            speech_frames = list(audio_buffer)  # Include context
                            logger.debug(f"Speech started for session {session_id}")
                        
                        elif is_speech and is_speaking:
                            # Continue speech
                            speech_frames.append(frame)
                        
                        elif not is_speech and is_speaking:
                            # End of speech - process accumulated frames
                            is_speaking = False
                            
                            if len(speech_frames) > 10:  # Minimum speech length
                                audio_data = b''.join(speech_frames)
                                
                                # Send for transcription
                                await self._transcribe_audio_segment(
                                    session_id, audio_data, sample_rate
                                )
                            
                            speech_frames = []
                
                except asyncio.CancelledError:
                    break
                except Exception as e:
                    logger.error(f"Error in transcription worker for {session_id}: {e}")
                    await asyncio.sleep(0.1)
        
        except Exception as e:
            logger.error(f"Transcription worker failed for {session_id}: {e}")
    
    def _get_audio_chunk_timeout(self, session_id: str, timeout: float):
        """Get audio chunk with timeout"""
        try:
            return self.audio_buffers[session_id].get(timeout=timeout)
        except queue.Empty:
            return None
        except KeyError:
            return None
    
    def _convert_to_pcm(self, audio_data: bytes, sample_rate: int) -> bytes:
        """Convert audio to 16-bit PCM"""
        # This is a simplified conversion - in practice, you'd use proper audio libraries
        return audio_data
    
    def _split_into_frames(self, pcm_data: bytes, frame_size: int) -> List[bytes]:
        """Split PCM data into frames"""
        frames = []
        for i in range(0, len(pcm_data), frame_size * 2):  # 2 bytes per sample
            frame = pcm_data[i:i + frame_size * 2]
            if len(frame) == frame_size * 2:
                frames.append(frame)
        return frames
    
    def _is_speech_frame(self, frame: bytes, sample_rate: int) -> bool:
        """Use WebRTC VAD to determine if frame contains speech"""
        try:
            return self.vad.is_speech(frame, sample_rate)
        except:
            return False
    
    async def _transcribe_audio_segment(self, session_id: str, audio_data: bytes, sample_rate: int):
        """Transcribe an audio segment using available STT engines"""
        try:
            transcript_text = ""
            confidence = 0.0
            
            # Try Google STT first if available
            if 'google' in speech_engines:
                try:
                    result = await self._google_stt(audio_data, sample_rate)
                    if result:
                        transcript_text = result['text']
                        confidence = result['confidence']
                except Exception as e:
                    logger.warning(f"Google STT failed: {e}")
            
            # Fallback to Vosk if Google failed or unavailable
            if not transcript_text and 'vosk' in speech_engines:
                try:
                    result = await self._vosk_stt(audio_data, sample_rate)
                    if result:
                        transcript_text = result['text']
                        confidence = result.get('confidence', 0.5)
                except Exception as e:
                    logger.warning(f"Vosk STT failed: {e}")
            
            # Send transcript update if we got results
            if transcript_text.strip():
                update = {
                    'type': 'transcript_update',
                    'session_id': session_id,
                    'text': transcript_text,
                    'confidence': confidence,
                    'is_final': True,
                    'timestamp': datetime.now().isoformat(),
                    'source': 'speech'
                }
                
                # Broadcast to WebSocket connections
                await app.state.connection_manager.broadcast_to_session(
                    session_id, update
                )
                
                # Store in Redis
                await self._store_transcript_segment(session_id, update)
        
        except Exception as e:
            logger.error(f"Error transcribing audio segment: {e}")
    
    async def _google_stt(self, audio_data: bytes, sample_rate: int) -> Optional[Dict]:
        """Use Google Speech-to-Text"""
        try:
            client = speech_engines['google']
            
            audio = speech.RecognitionAudio(content=audio_data)
            config = speech.RecognitionConfig(
                encoding=speech.RecognitionConfig.AudioEncoding.LINEAR16,
                sample_rate_hertz=sample_rate,
                language_code="en-US",
                enable_automatic_punctuation=True,
                enable_word_confidence=True
            )
            
            # Use synchronous recognition for shorter segments
            response = await asyncio.get_event_loop().run_in_executor(
                executor, lambda: client.recognize(config=config, audio=audio)
            )
            
            if response.results:
                result = response.results[0]
                alternative = result.alternatives[0]
                return {
                    'text': alternative.transcript,
                    'confidence': alternative.confidence
                }
        
        except Exception as e:
            logger.error(f"Google STT error: {e}")
        
        return None
    
    async def _vosk_stt(self, audio_data: bytes, sample_rate: int) -> Optional[Dict]:
        """Use Vosk for speech recognition"""
        try:
            model = speech_engines['vosk']
            recognizer = vosk.KaldiRecognizer(model, sample_rate)
            
            # Process audio data
            result = await asyncio.get_event_loop().run_in_executor(
                executor, lambda: recognizer.AcceptWaveform(audio_data)
            )
            
            if result:
                result_json = json_module.loads(recognizer.Result())
                if 'text' in result_json and result_json['text']:
                    return {
                        'text': result_json['text'],
                        'confidence': result_json.get('conf', 0.5)
                    }
        
        except Exception as e:
            logger.error(f"Vosk STT error: {e}")
        
        return None
    
    async def _store_transcript_segment(self, session_id: str, update: Dict):
        """Store transcript segment in Redis"""
        try:
            key = f"transcript:{session_id}:segments"
            segment_data = json.dumps(update)
            redis_client.lpush(key, segment_data)
            redis_client.expire(key, 3600)  # 1 hour TTL
        except Exception as e:
            logger.error(f"Error storing transcript segment: {e}")

# FastAPI endpoints

@app.get("/")
async def root():
    """Health check endpoint"""
    engines_status = {
        'google_stt': 'google' in speech_engines,
        'vosk': 'vosk' in speech_engines
    }
    
    return {
        "service": "ARIA Speech & Transcript Service",
        "status": "healthy",
        "version": "1.0.0",
        "engines": engines_status,
        "timestamp": datetime.now().isoformat()
    }

@app.websocket("/ws/transcript/{session_id}")
async def websocket_transcript(websocket: WebSocket, session_id: str):
    """WebSocket endpoint for real-time transcript streaming"""
    await app.state.connection_manager.connect(websocket, session_id)
    
    try:
        # Send welcome message
        await websocket.send_json({
            'type': 'connection_established',
            'session_id': session_id,
            'message': 'Transcript WebSocket connected'
        })
        
        # Start transcription if not already started
        audio_config = {
            'sample_rate': 16000,
            'channels': 1,
            'format': 'pcm'
        }
        await app.state.transcription_manager.start_transcription(session_id, audio_config)
        
        # Listen for incoming messages
        while True:
            data = await websocket.receive()
            
            if 'bytes' in data:
                # Audio data received
                audio_bytes = data['bytes']
                await app.state.transcription_manager.process_audio_chunk(
                    session_id, audio_bytes
                )
            
            elif 'text' in data:
                # Text message received (control commands)
                try:
                    message = json.loads(data['text'])
                    await handle_websocket_message(session_id, message)
                except json.JSONDecodeError:
                    logger.warning(f"Invalid JSON received: {data['text']}")
    
    except WebSocketDisconnect:
        logger.info(f"WebSocket disconnected for session {session_id}")
    except Exception as e:
        logger.error(f"WebSocket error for session {session_id}: {e}")
    finally:
        app.state.connection_manager.disconnect(websocket, session_id)
        await app.state.transcription_manager.stop_transcription(session_id)

async def handle_websocket_message(session_id: str, message: Dict):
    """Handle control messages from WebSocket"""
    message_type = message.get('type')
    
    if message_type == 'start_transcription':
        audio_config = message.get('audio_config', {})
        await app.state.transcription_manager.start_transcription(session_id, audio_config)
    
    elif message_type == 'stop_transcription':
        await app.state.transcription_manager.stop_transcription(session_id)
    
    elif message_type == 'code_update':
        # Handle code editor updates
        code_content = message.get('code', '')
        await broadcast_code_update(session_id, code_content)
    
    elif message_type == 'chat_message':
        # Handle chat messages
        chat_text = message.get('text', '')
        await broadcast_chat_message(session_id, chat_text)

async def broadcast_code_update(session_id: str, code_content: str):
    """Broadcast code editor updates"""
    update = {
        'type': 'code_update',
        'session_id': session_id,
        'code': code_content,
        'timestamp': datetime.now().isoformat(),
        'source': 'code'
    }
    
    await app.state.connection_manager.broadcast_to_session(session_id, update)
    
    # Store in transcript
    transcript_update = TranscriptUpdate(
        session_id=session_id,
        text=f"[CODE] {code_content}",
        confidence=1.0,
        is_final=True,
        timestamp=datetime.now(),
        source='code'
    )
    
    await store_transcript_update(transcript_update)

async def broadcast_chat_message(session_id: str, chat_text: str):
    """Broadcast chat messages"""
    update = {
        'type': 'chat_message',
        'session_id': session_id,
        'text': chat_text,
        'timestamp': datetime.now().isoformat(),
        'source': 'chat'
    }
    
    await app.state.connection_manager.broadcast_to_session(session_id, update)
    
    # Store in transcript
    transcript_update = TranscriptUpdate(
        session_id=session_id,
        text=f"[CHAT] {chat_text}",
        confidence=1.0,
        is_final=True,
        timestamp=datetime.now(),
        source='chat'
    )
    
    await store_transcript_update(transcript_update)

@app.post("/transcript/merge", response_model=TranscriptMergeResponse)
async def merge_transcripts(request: TranscriptMergeRequest):
    """
    Merge audio transcript with code and chat content chronologically
    """
    try:
        logger.info(f"Merging transcripts for session {request.session_id}")
        
        # Get all transcript segments from Redis
        segments_key = f"transcript:{request.session_id}:segments"
        segments_data = redis_client.lrange(segments_key, 0, -1)
        
        segments = []
        for segment_json in segments_data:
            try:
                segment = json.loads(segment_json)
                segments.append(segment)
            except json.JSONDecodeError:
                continue
        
        # Add new content to segments
        if request.audio_transcript:
            segments.append({
                'type': 'speech',
                'text': request.audio_transcript,
                'timestamp': request.timestamp.isoformat(),
                'source': 'speech'
            })
        
        if request.code_content:
            segments.append({
                'type': 'code',
                'text': f"[CODE] {request.code_content}",
                'timestamp': request.timestamp.isoformat(),
                'source': 'code'
            })
        
        for chat_msg in request.chat_messages:
            segments.append({
                'type': 'chat',
                'text': f"[CHAT] {chat_msg.get('text', '')}",
                'timestamp': chat_msg.get('timestamp', request.timestamp.isoformat()),
                'source': 'chat'
            })
        
        # Sort by timestamp
        segments.sort(key=lambda x: x.get('timestamp', ''))
        
        # Merge into unified transcript
        merged_transcript = []
        for segment in segments:
            timestamp_str = datetime.fromisoformat(
                segment['timestamp'].replace('Z', '+00:00')
            ).strftime('%H:%M:%S')
            
            text = segment.get('text', '').strip()
            if text:
                merged_transcript.append(f"[{timestamp_str}] {text}")
        
        merged_text = '\n'.join(merged_transcript)
        
        # Store merged transcript
        merged_key = f"transcript:{request.session_id}:merged"
        redis_client.set(merged_key, merged_text, ex=3600)
        
        response = TranscriptMergeResponse(
            session_id=request.session_id,
            merged_transcript=merged_text,
            segments=segments,
            merge_metadata={
                'total_segments': len(segments),
                'merge_strategy': request.merge_strategy,
                'merged_at': datetime.now().isoformat()
            }
        )
        
        logger.info(f"Successfully merged {len(segments)} segments for session {request.session_id}")
        return response
    
    except Exception as e:
        logger.error(f"Error merging transcripts: {e}")
        raise HTTPException(status_code=500, detail=f"Transcript merge failed: {str(e)}")

async def store_transcript_update(update: TranscriptUpdate):
    """Store transcript update in Redis"""
    try:
        key = f"transcript:{update.session_id}:segments"
        segment_data = json.dumps(update.dict(), default=str)
        redis_client.lpush(key, segment_data)
        redis_client.expire(key, 3600)
    except Exception as e:
        logger.error(f"Error storing transcript update: {e}")

@app.get("/transcript/{session_id}")
async def get_transcript(session_id: str):
    """Get current merged transcript for a session"""
    try:
        merged_key = f"transcript:{session_id}:merged"
        merged_transcript = redis_client.get(merged_key)
        
        if merged_transcript:
            return {
                'session_id': session_id,
                'transcript': merged_transcript,
                'retrieved_at': datetime.now().isoformat()
            }
        else:
            # Generate on-the-fly if not cached
            segments_key = f"transcript:{session_id}:segments"
            segments_data = redis_client.lrange(segments_key, 0, -1)
            
            if not segments_data:
                return {
                    'session_id': session_id,
                    'transcript': '',
                    'retrieved_at': datetime.now().isoformat()
                }
            
            # Quick merge
            segments = []
            for segment_json in reversed(segments_data):  # Redis stores in reverse order
                try:
                    segment = json.loads(segment_json)
                    segments.append(segment)
                except:
                    continue
            
            segments.sort(key=lambda x: x.get('timestamp', ''))
            
            merged_lines = []
            for segment in segments:
                text = segment.get('text', '').strip()
                if text:
                    merged_lines.append(text)
            
            transcript = '\n'.join(merged_lines)
            
            return {
                'session_id': session_id,
                'transcript': transcript,
                'segments_count': len(segments),
                'retrieved_at': datetime.now().isoformat()
            }
    
    except Exception as e:
        logger.error(f"Error retrieving transcript: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to retrieve transcript: {str(e)}")

@app.get("/health")
async def health_check():
    """Detailed health check"""
    try:
        redis_status = "healthy" if redis_client.ping() else "unhealthy"
        
        components = {
            "redis": redis_status,
            "google_stt": "available" if 'google' in speech_engines else "unavailable",
            "vosk": "available" if 'vosk' in speech_engines else "unavailable",
            "websocket_manager": "healthy",
            "transcription_manager": "healthy"
        }
        
        overall_status = "healthy" if redis_status == "healthy" else "degraded"
        
        return {
            "status": overall_status,
            "components": components,
            "active_sessions": len(app.state.connection_manager.active_connections),
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
    uvicorn.run(app, host="0.0.0.0", port=8002, log_level="info")
