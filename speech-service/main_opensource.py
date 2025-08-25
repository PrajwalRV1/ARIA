#!/usr/bin/env python3
"""
Open-Source Speech & Transcript Service for ARIA Interview Platform
Real-time speech-to-text using Vosk with WebSocket streaming
Replacement for Google Cloud Speech-to-Text
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, BackgroundTasks, UploadFile, File
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
import tempfile
import shutil
import threading
import queue
import time

# Import dual-channel transcription engine
from dual_channel_transcription import DualChannelTranscriptionEngine, ParticipantInfo, AudioFrame, TranscriptSegment
from response_consolidator import ResponseConsolidator, ResponseFragment, ConsolidatedResponse

# Open-source speech recognition imports
try:
    import vosk
    import json as json_module
    VOSK_AVAILABLE = True
except ImportError:
    VOSK_AVAILABLE = False
    logging.warning("Vosk not available, install with: pip install vosk")

try:
    import deepspeech
    DEEPSPEECH_AVAILABLE = True
except ImportError:
    DEEPSPEECH_AVAILABLE = False
    logging.warning("DeepSpeech not available")

# Audio processing
import webrtcvad
import collections
import audioop
import wave
import struct
import numpy as np

# SpeechRecognition as additional fallback
try:
    import speech_recognition as sr
    SPEECH_RECOGNITION_AVAILABLE = True
except ImportError:
    SPEECH_RECOGNITION_AVAILABLE = False
    logging.warning("SpeechRecognition library not available")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Redis client for session management
redis_client = redis.Redis(host='localhost', port=6379, db=1, decode_responses=True)

# Thread pool for background processing
executor = ThreadPoolExecutor(max_workers=10)

# Global speech recognition engines
speech_engines = {}
vosk_models = {}

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize services on startup"""
    logger.info("Starting Open-Source Speech & Transcript Service...")
    
    # Initialize Vosk models if available
    if VOSK_AVAILABLE:
        model_configs = [
            {
                "name": "vosk_small_en",
                "path": os.getenv('VOSK_SMALL_MODEL_PATH', '/models/vosk-model-small-en-us-0.15'),
                "description": "Small English model (fast, less accurate)"
            },
            {
                "name": "vosk_large_en", 
                "path": os.getenv('VOSK_LARGE_MODEL_PATH', '/models/vosk-model-en-us-0.22'),
                "description": "Large English model (slower, more accurate)"
            }
        ]
        
        for config in model_configs:
            model_path = config["path"]
            if os.path.exists(model_path):
                try:
                    model = vosk.Model(model_path)
                    vosk_models[config["name"]] = {
                        "model": model,
                        "path": model_path,
                        "description": config["description"]
                    }
                    speech_engines[config["name"]] = model
                    logger.info(f"Vosk model loaded: {config['name']} from {model_path}")
                except Exception as e:
                    logger.warning(f"Failed to load Vosk model {config['name']}: {e}")
            else:
                logger.warning(f"Vosk model not found at {model_path}")
    
    # Initialize DeepSpeech if available
    if DEEPSPEECH_AVAILABLE:
        deepspeech_model_path = os.getenv('DEEPSPEECH_MODEL_PATH', '/models/deepspeech-0.9.3-models.pbmm')
        deepspeech_scorer_path = os.getenv('DEEPSPEECH_SCORER_PATH', '/models/deepspeech-0.9.3-models.scorer')
        
        if os.path.exists(deepspeech_model_path):
            try:
                ds_model = deepspeech.Model(deepspeech_model_path)
                if os.path.exists(deepspeech_scorer_path):
                    ds_model.enableExternalScorer(deepspeech_scorer_path)
                speech_engines['deepspeech'] = ds_model
                logger.info(f"DeepSpeech model loaded from {deepspeech_model_path}")
            except Exception as e:
                logger.warning(f"Failed to load DeepSpeech model: {e}")
    
    # Initialize SpeechRecognition as fallback
    if SPEECH_RECOGNITION_AVAILABLE:
        try:
            recognizer = sr.Recognizer()
            speech_engines['speech_recognition'] = recognizer
            logger.info("SpeechRecognition initialized as fallback")
        except Exception as e:
            logger.warning(f"Failed to initialize SpeechRecognition: {e}")
    
    # Initialize WebSocket connection manager
    app.state.connection_manager = ConnectionManager()
    app.state.transcription_manager = TranscriptionManager(speech_engines)
    
    # Initialize dual-channel transcription engine
    app.state.dual_channel_engine = DualChannelTranscriptionEngine(
        speech_engines=speech_engines,
        connection_manager=app.state.connection_manager
    )
    logger.info("Open-source dual-channel transcription engine initialized")
    
    # Initialize response consolidator
    app.state.response_consolidator = ResponseConsolidator(app.state.connection_manager)
    await app.state.response_consolidator.start()
    logger.info("Response consolidator initialized")
    
    yield
    
    # Cleanup
    logger.info("Shutting down Open-Source Speech & Transcript Service...")

app = FastAPI(
    title="ARIA Open-Source Speech & Transcript Service", 
    description="Real-time speech-to-text using Vosk and DeepSpeech with WebSocket streaming",
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

# Pydantic models (same as original)
class TranscriptMergeRequest(BaseModel):
    session_id: str
    audio_transcript: str
    code_content: str = ""
    chat_messages: List[Dict[str, Any]] = []
    timestamp: datetime
    merge_strategy: str = "chronological"

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
    source: str = "speech"
    engine_used: str = "vosk"

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

class VoskRecognizer:
    """Vosk-based speech recognition"""
    
    def __init__(self, model_name="vosk_large_en"):
        self.model_name = model_name
        self.model = vosk_models.get(model_name, {}).get("model")
        if not self.model:
            # Fallback to any available model
            available_models = list(vosk_models.keys())
            if available_models:
                self.model_name = available_models[0]
                self.model = vosk_models[self.model_name]["model"]
            else:
                raise ValueError("No Vosk models available")
        
        self.recognizer = vosk.KaldiRecognizer(self.model, 16000)
        self.recognizer.SetWords(True)
    
    def recognize_audio(self, audio_data: bytes) -> Dict[str, Any]:
        """Recognize speech from audio data"""
        try:
            if self.recognizer.AcceptWaveform(audio_data):
                result = json.loads(self.recognizer.Result())
                return {
                    "text": result.get("text", ""),
                    "confidence": result.get("confidence", 0.5),
                    "is_final": True,
                    "words": result.get("result", [])
                }
            else:
                partial = json.loads(self.recognizer.PartialResult())
                return {
                    "text": partial.get("partial", ""),
                    "confidence": 0.3,
                    "is_final": False,
                    "words": []
                }
        except Exception as e:
            logger.error(f"Vosk recognition error: {e}")
            return {
                "text": "",
                "confidence": 0.0,
                "is_final": True,
                "words": []
            }
    
    def reset(self):
        """Reset the recognizer state"""
        self.recognizer = vosk.KaldiRecognizer(self.model, 16000)
        self.recognizer.SetWords(True)

class DeepSpeechRecognizer:
    """DeepSpeech-based speech recognition"""
    
    def __init__(self):
        self.model = speech_engines.get('deepspeech')
        if not self.model:
            raise ValueError("DeepSpeech model not available")
    
    def recognize_audio(self, audio_data: bytes) -> Dict[str, Any]:
        """Recognize speech from audio data using DeepSpeech"""
        try:
            # Convert audio data to numpy array
            audio_np = np.frombuffer(audio_data, dtype=np.int16)
            
            # DeepSpeech expects 16kHz mono PCM
            text = self.model.stt(audio_np)
            
            return {
                "text": text,
                "confidence": 0.8,  # DeepSpeech doesn't provide confidence scores
                "is_final": True,
                "words": []
            }
        except Exception as e:
            logger.error(f"DeepSpeech recognition error: {e}")
            return {
                "text": "",
                "confidence": 0.0,
                "is_final": True,
                "words": []
            }

class SpeechRecognitionFallback:
    """Fallback using SpeechRecognition library"""
    
    def __init__(self):
        if not SPEECH_RECOGNITION_AVAILABLE:
            raise ValueError("SpeechRecognition not available")
        self.recognizer = sr.Recognizer()
    
    def recognize_audio(self, audio_data: bytes) -> Dict[str, Any]:
        """Recognize speech using SpeechRecognition library"""
        try:
            # Convert raw audio to AudioData
            audio_file = sr.AudioFile(io.BytesIO(audio_data))
            with audio_file as source:
                audio = self.recognizer.record(source)
            
            # Try different engines
            engines = ['wit', 'sphinx']  # Free engines
            
            for engine in engines:
                try:
                    if engine == 'wit':
                        text = self.recognizer.recognize_wit(audio, key="your_wit_key_here")
                    elif engine == 'sphinx':
                        text = self.recognizer.recognize_sphinx(audio)
                    
                    return {
                        "text": text,
                        "confidence": 0.6,
                        "is_final": True,
                        "words": []
                    }
                except Exception as e:
                    logger.warning(f"Engine {engine} failed: {e}")
                    continue
            
            return {
                "text": "",
                "confidence": 0.0,
                "is_final": True,
                "words": []
            }
        except Exception as e:
            logger.error(f"SpeechRecognition fallback error: {e}")
            return {
                "text": "",
                "confidence": 0.0,
                "is_final": True,
                "words": []
            }

class TranscriptionManager:
    """Manages real-time transcription processing with open-source engines"""
    
    def __init__(self, speech_engines: Dict):
        self.speech_engines = speech_engines
        self.audio_buffers: Dict[str, queue.Queue] = {}
        self.transcription_tasks: Dict[str, asyncio.Task] = {}
        self.recognizers: Dict[str, Any] = {}
        self.vad = webrtcvad.Vad(2)  # Voice activity detection
        
        # Initialize recognizers
        self.initialize_recognizers()
    
    def initialize_recognizers(self):
        """Initialize speech recognition engines"""
        # Initialize Vosk recognizers
        for model_name in vosk_models.keys():
            try:
                self.recognizers[model_name] = VoskRecognizer(model_name)
            except Exception as e:
                logger.warning(f"Failed to initialize Vosk recognizer {model_name}: {e}")
        
        # Initialize DeepSpeech recognizer
        if 'deepspeech' in self.speech_engines:
            try:
                self.recognizers['deepspeech'] = DeepSpeechRecognizer()
            except Exception as e:
                logger.warning(f"Failed to initialize DeepSpeech recognizer: {e}")
        
        # Initialize SpeechRecognition fallback
        if SPEECH_RECOGNITION_AVAILABLE:
            try:
                self.recognizers['speech_recognition'] = SpeechRecognitionFallback()
            except Exception as e:
                logger.warning(f"Failed to initialize SpeechRecognition fallback: {e}")
    
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
            logger.info(f"Started open-source transcription for session {session_id}")
    
    async def process_audio_chunk(self, session_id: str, audio_data: bytes):
        """Process incoming audio chunk"""
        if session_id in self.audio_buffers:
            self.audio_buffers[session_id].put(audio_data)
    
    async def stop_transcription(self, session_id: str):
        """Stop transcription for a session"""
        if session_id in self.transcription_tasks:
            self.transcription_tasks[session_id].cancel()
            del self.transcription_tasks[session_id]
        
        if session_id in self.audio_buffers:
            del self.audio_buffers[session_id]
        
        logger.info(f"Stopped transcription for session {session_id}")
    
    async def _transcription_worker(self, session_id: str, audio_config: Dict):
        """Background worker for continuous transcription"""
        connection_manager = app.state.connection_manager
        preferred_engine = self.get_best_available_recognizer()
        
        logger.info(f"Starting transcription worker for session {session_id} using {preferred_engine}")
        
        # Get the recognizer
        recognizer = self.recognizers.get(preferred_engine)
        if not recognizer:
            logger.error(f"No recognizer available for session {session_id}")
            return
        
        audio_buffer = self.audio_buffers[session_id]
        accumulated_audio = b""
        last_result_time = time.time()
        
        try:
            while True:
                try:
                    # Get audio chunk with timeout
                    audio_chunk = audio_buffer.get(timeout=1.0)
                    accumulated_audio += audio_chunk
                    
                    # Process audio when we have enough data (about 1 second)
                    if len(accumulated_audio) >= 16000 * 2:  # 1 second at 16kHz 16-bit
                        result = await self.process_audio_with_recognizer(
                            recognizer, accumulated_audio, preferred_engine
                        )
                        
                        if result["text"].strip():
                            # Create response fragment for consolidator
                            fragment = ResponseFragment(
                                session_id=session_id,
                                text=result["text"],
                                confidence=result["confidence"],
                                timestamp=time.time(),
                                is_final=result["is_final"],
                                source="speech"
                            )
                            
                            # Add to response consolidator instead of sending directly
                            response_consolidator = app.state.response_consolidator
                            await response_consolidator.add_fragment(session_id, fragment)
                        
                        # Clear buffer if final result
                        if result["is_final"]:
                            accumulated_audio = b""
                            if hasattr(recognizer, 'reset'):
                                recognizer.reset()
                        else:
                            # Keep some audio for context
                            accumulated_audio = accumulated_audio[-8000:]  # Keep last 0.5s
                        
                        last_result_time = time.time()
                    
                    # Timeout handling - send periodic updates
                    if time.time() - last_result_time > 5.0 and accumulated_audio:
                        result = await self.process_audio_with_recognizer(
                            recognizer, accumulated_audio, preferred_engine
                        )
                        
                        if result["text"].strip():
                            await connection_manager.broadcast_to_session(session_id, {
                                "type": "transcript_update",
                                "session_id": session_id,
                                "text": result["text"],
                                "confidence": result["confidence"],
                                "is_final": True,  # Force final after timeout
                                "timestamp": datetime.now().isoformat(),
                                "source": "speech",
                                "engine_used": preferred_engine
                            })
                        
                        accumulated_audio = b""
                        if hasattr(recognizer, 'reset'):
                            recognizer.reset()
                        last_result_time = time.time()
                
                except queue.Empty:
                    # No audio received in timeout period
                    continue
                except Exception as e:
                    logger.error(f"Error in transcription worker: {e}")
                    await asyncio.sleep(1)
        
        except asyncio.CancelledError:
            logger.info(f"Transcription worker cancelled for session {session_id}")
        except Exception as e:
            logger.error(f"Transcription worker error for session {session_id}: {e}")
    
    async def process_audio_with_recognizer(self, recognizer, audio_data: bytes, engine_name: str) -> Dict[str, Any]:
        """Process audio with the specified recognizer"""
        try:
            # Run recognition in thread pool to avoid blocking
            result = await asyncio.get_event_loop().run_in_executor(
                executor, recognizer.recognize_audio, audio_data
            )
            return result
        except Exception as e:
            logger.error(f"Recognition error with {engine_name}: {e}")
            return {
                "text": "",
                "confidence": 0.0,
                "is_final": True,
                "words": []
            }
    
    def get_best_available_recognizer(self) -> str:
        """Get the best available speech recognizer"""
        # Preference order: large model > small model > DeepSpeech > fallback
        preferred_order = [
            "vosk_large_en", 
            "vosk_small_en", 
            "deepspeech", 
            "speech_recognition"
        ]
        
        for engine_name in preferred_order:
            if engine_name in self.recognizers:
                return engine_name
        
        raise ValueError("No speech recognizers available")

# API Endpoints

@app.websocket("/ws/transcript/{session_id}")
async def websocket_transcript_endpoint(websocket: WebSocket, session_id: str):
    """WebSocket endpoint for real-time transcription"""
    connection_manager = app.state.connection_manager
    transcription_manager = app.state.transcription_manager
    
    await connection_manager.connect(websocket, session_id)
    
    try:
        await websocket.send_json({
            "type": "connection_established",
            "message": f"Connected to open-source transcription service for session {session_id}",
            "available_engines": list(transcription_manager.recognizers.keys())
        })
        
        while True:
            data = await websocket.receive()
            
            if data["type"] == "websocket.receive":
                if "bytes" in data:
                    # Received binary audio data
                    audio_data = data["bytes"]
                    await transcription_manager.process_audio_chunk(session_id, audio_data)
                elif "text" in data:
                    # Received JSON message
                    try:
                        message = json.loads(data["text"])
                        await handle_websocket_message(session_id, message, transcription_manager)
                    except json.JSONDecodeError:
                        logger.warning("Invalid JSON received")
            
    except WebSocketDisconnect:
        connection_manager.disconnect(websocket, session_id)
        await transcription_manager.stop_transcription(session_id)
        logger.info(f"WebSocket disconnected for session {session_id}")
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        connection_manager.disconnect(websocket, session_id)
        await transcription_manager.stop_transcription(session_id)

async def handle_websocket_message(session_id: str, message: Dict, transcription_manager: TranscriptionManager):
    """Handle WebSocket text messages"""
    message_type = message.get("type")
    
    if message_type == "start_transcription":
        audio_config = message.get("audio_config", {
            "sample_rate": 16000,
            "channels": 1,
            "format": "pcm"
        })
        await transcription_manager.start_transcription(session_id, audio_config)
        
        # Also start response collection
        response_consolidator = app.state.response_consolidator
        await response_consolidator.start_response_collection(session_id)
        
    elif message_type == "stop_transcription":
        await transcription_manager.stop_transcription(session_id)
        
    elif message_type == "end_response":
        # Handle End Response button/space bar trigger
        response_consolidator = app.state.response_consolidator
        consolidated = await response_consolidator.end_response(session_id)
        
        if consolidated:
            # Send final consolidated response for processing
            await app.state.connection_manager.broadcast_to_session(session_id, {
                "type": "consolidated_response",
                "session_id": session_id,
                "text": consolidated.consolidated_text,
                "confidence": consolidated.average_confidence,
                "word_count": consolidated.word_count,
                "duration": consolidated.total_duration,
                "fragment_count": len(consolidated.fragments),
                "is_final": True,
                "timestamp": datetime.now().isoformat(),
                "source": "speech_consolidated"
            })
            logger.info(f"✅ Consolidated response sent for {session_id}: {consolidated.word_count} words")
        
    elif message_type == "start_new_response":
        # Start collecting a new response
        response_consolidator = app.state.response_consolidator  
        await response_consolidator.start_response_collection(session_id)
        
    elif message_type == "audio_chunk":
        # Process base64 encoded audio data
        try:
            audio_b64 = message.get("audio_data", "")
            audio_data = base64.b64decode(audio_b64)
            await transcription_manager.process_audio_chunk(session_id, audio_data)
        except Exception as e:
            logger.error(f"Error processing audio chunk: {e}")

@app.post("/transcript/process-audio", response_model=TranscriptUpdate)
async def process_audio_file(
    session_id: str,
    audio_file: UploadFile = File(...),
    engine: str = "vosk_large_en"
):
    """Process uploaded audio file for transcription"""
    try:
        # Read audio file
        audio_data = await audio_file.read()
        
        # Get transcription manager and recognizer
        transcription_manager = app.state.transcription_manager
        recognizer = transcription_manager.recognizers.get(engine)
        
        if not recognizer:
            raise HTTPException(status_code=400, detail=f"Engine {engine} not available")
        
        # Process audio
        result = await transcription_manager.process_audio_with_recognizer(
            recognizer, audio_data, engine
        )
        
        return TranscriptUpdate(
            session_id=session_id,
            text=result["text"],
            confidence=result["confidence"],
            is_final=result["is_final"],
            timestamp=datetime.now(),
            source="speech",
            engine_used=engine
        )
        
    except Exception as e:
        logger.error(f"Audio processing error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/transcript/{session_id}")
async def get_transcript(session_id: str):
    """Get transcript for a session"""
    try:
        # Try to get transcript from Redis
        transcript_key = f"transcript:{session_id}"
        transcript_data = redis_client.get(transcript_key)
        
        if transcript_data:
            return json.loads(transcript_data)
        else:
            return {
                "session_id": session_id,
                "transcript": "",
                "segments": [],
                "metadata": {
                    "total_duration": 0,
                    "word_count": 0,
                    "engines_used": []
                }
            }
    except Exception as e:
        logger.error(f"Error retrieving transcript: {e}")
        raise HTTPException(status_code=500, detail="Failed to retrieve transcript")

@app.post("/transcript/merge", response_model=TranscriptMergeResponse)
async def merge_transcripts(request: TranscriptMergeRequest):
    """Merge audio transcript with code and chat content"""
    try:
        # Implementation of transcript merging logic
        merged_content = []
        
        # Add audio transcript
        if request.audio_transcript:
            merged_content.append({
                "type": "audio",
                "content": request.audio_transcript,
                "timestamp": request.timestamp.isoformat(),
                "source": "speech"
            })
        
        # Add code content
        if request.code_content:
            merged_content.append({
                "type": "code",
                "content": request.code_content,
                "timestamp": request.timestamp.isoformat(),
                "source": "code_editor"
            })
        
        # Add chat messages
        for msg in request.chat_messages:
            merged_content.append({
                "type": "chat",
                "content": msg.get("text", ""),
                "timestamp": msg.get("timestamp", request.timestamp.isoformat()),
                "source": "chat"
            })
        
        # Sort by timestamp if using chronological strategy
        if request.merge_strategy == "chronological":
            merged_content.sort(key=lambda x: x["timestamp"])
        
        # Create merged transcript
        merged_text = "\n".join([item["content"] for item in merged_content])
        
        # Store in Redis
        transcript_key = f"merged_transcript:{request.session_id}"
        transcript_data = {
            "session_id": request.session_id,
            "merged_transcript": merged_text,
            "segments": merged_content,
            "merge_metadata": {
                "strategy": request.merge_strategy,
                "merge_time": datetime.now().isoformat(),
                "total_segments": len(merged_content)
            }
        }
        redis_client.setex(transcript_key, 3600, json.dumps(transcript_data))  # Store for 1 hour
        
        return TranscriptMergeResponse(**transcript_data)
        
    except Exception as e:
        logger.error(f"Transcript merge error: {e}")
        raise HTTPException(status_code=500, detail="Failed to merge transcripts")

@app.get("/engines")
async def list_available_engines():
    """List available speech recognition engines"""
    transcription_manager = app.state.transcription_manager
    
    engine_info = {}
    for engine_name, recognizer in transcription_manager.recognizers.items():
        if engine_name.startswith("vosk"):
            model_info = vosk_models.get(engine_name, {})
            engine_info[engine_name] = {
                "type": "vosk",
                "description": model_info.get("description", "Vosk model"),
                "model_path": model_info.get("path", "unknown"),
                "available": True
            }
        elif engine_name == "deepspeech":
            engine_info[engine_name] = {
                "type": "deepspeech",
                "description": "Mozilla DeepSpeech",
                "available": True
            }
        elif engine_name == "speech_recognition":
            engine_info[engine_name] = {
                "type": "speech_recognition_fallback",
                "description": "SpeechRecognition library fallback",
                "available": True
            }
    
    return {
        "available_engines": engine_info,
        "default_engine": transcription_manager.get_best_available_recognizer(),
        "vosk_available": VOSK_AVAILABLE,
        "deepspeech_available": DEEPSPEECH_AVAILABLE,
        "speech_recognition_available": SPEECH_RECOGNITION_AVAILABLE
    }

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    transcription_manager = app.state.transcription_manager
    
    return {
        "status": "healthy",
        "service": "Open-Source Speech & Transcript Service",
        "available_engines": list(transcription_manager.recognizers.keys()),
        "vosk_models": list(vosk_models.keys()),
        "vosk_available": VOSK_AVAILABLE,
        "deepspeech_available": DEEPSPEECH_AVAILABLE,
        "speech_recognition_available": SPEECH_RECOGNITION_AVAILABLE,
        "active_sessions": len(transcription_manager.transcription_tasks),
        "timestamp": datetime.now().isoformat()
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002, reload=True)
