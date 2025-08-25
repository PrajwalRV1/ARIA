#!/usr/bin/env python3
"""
Dual-Channel Transcription Engine for ARIA Interview Platform
Provides real-time transcription with participant identification and recording synchronization
"""

import asyncio
import json
import logging
import base64
import time
from datetime import datetime
from typing import Dict, List, Optional, Any, Tuple, Set
from fastapi import WebSocket
import webrtcvad
import numpy as np
from pydantic import BaseModel
import redis

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Redis client for transcript storage
redis_client = redis.Redis(host='localhost', port=6379, db=1, decode_responses=True)

class ParticipantInfo(BaseModel):
    """Information about a participant in the interview"""
    id: str
    name: str
    role: str  # 'candidate', 'ai_avatar', 'recruiter'
    voice_profile: Optional[Dict[str, Any]] = None

class AudioFrame(BaseModel):
    """Audio frame with participant info"""
    participant_id: str
    audio_data: bytes
    timestamp: float
    is_speaking: bool = False
    sample_rate: int = 16000
    format: str = "pcm"

class TranscriptSegment(BaseModel):
    """A segment of transcribed speech"""
    session_id: str
    participant_id: str
    text: str
    start_time: float
    end_time: float
    confidence: float
    is_final: bool
    words: List[Dict[str, Any]] = []
    
class DualChannelTranscriptionEngine:
    """Advanced multi-participant transcription engine for interviews"""
    
    def __init__(self, speech_engines: Dict, connection_manager: Any):
        self.speech_engines = speech_engines
        self.connection_manager = connection_manager
        self.vad = webrtcvad.Vad(3)  # Aggressive VAD for clean detection
        
        # Session management
        self.active_sessions: Dict[str, Dict[str, Any]] = {}
        self.session_participants: Dict[str, Dict[str, ParticipantInfo]] = {}
        self.audio_buffers: Dict[str, Dict[str, List[AudioFrame]]] = {}
        self.speaking_status: Dict[str, Dict[str, bool]] = {}
        
        # Current speech segments being processed
        self.active_speech_segments: Dict[str, Dict[str, Dict[str, Any]]] = {}
        
        # Background tasks
        self.transcription_tasks: Dict[str, asyncio.Task] = {}
        
        # Cross-session locks
        self.session_locks: Dict[str, asyncio.Lock] = {}
        
        # Diarization windows (keep track of who is speaking when)
        self.diarization_windows: Dict[str, List[Dict[str, Any]]] = {}
        
        # Statistics
        self.stats: Dict[str, Dict[str, Any]] = {}
    
    async def initialize_session(self, session_id: str, participants: List[ParticipantInfo], 
                                audio_config: Dict[str, Any]) -> None:
        """Initialize a new transcription session"""
        if session_id in self.active_sessions:
            await self.terminate_session(session_id)
        
        self.session_locks[session_id] = asyncio.Lock()
        
        async with self.session_locks[session_id]:
            # Initialize session data structures
            self.active_sessions[session_id] = {
                'start_time': time.time(),
                'audio_config': audio_config,
                'last_activity': time.time()
            }
            
            self.session_participants[session_id] = {
                p.id: p for p in participants
            }
            
            self.audio_buffers[session_id] = {
                p.id: [] for p in participants
            }
            
            self.speaking_status[session_id] = {
                p.id: False for p in participants
            }
            
            self.active_speech_segments[session_id] = {
                p.id: None for p in participants
            }
            
            self.diarization_windows[session_id] = []
            
            self.stats[session_id] = {
                'total_speech_segments': 0,
                'total_transcribed_words': 0,
                'participant_stats': {
                    p.id: {
                        'speech_segments': 0,
                        'speaking_time': 0.0,
                        'transcribed_words': 0
                    } for p in participants
                }
            }
            
            # Start the transcription worker task
            self.transcription_tasks[session_id] = asyncio.create_task(
                self._transcription_worker(session_id)
            )
            
            logger.info(f"Initialized dual-channel transcription for session {session_id} "
                       f"with {len(participants)} participants")
    
    async def terminate_session(self, session_id: str) -> None:
        """End a transcription session and clean up resources"""
        if session_id not in self.active_sessions:
            return
        
        # Cancel the worker task
        if session_id in self.transcription_tasks:
            self.transcription_tasks[session_id].cancel()
            try:
                await self.transcription_tasks[session_id]
            except asyncio.CancelledError:
                pass
            del self.transcription_tasks[session_id]
        
        # Generate final transcript
        await self._generate_final_transcript(session_id)
        
        # Clean up session data
        async with self.session_locks[session_id]:
            for key in [self.active_sessions, self.session_participants, 
                       self.audio_buffers, self.speaking_status,
                       self.active_speech_segments, self.diarization_windows]:
                if session_id in key:
                    del key[session_id]
        
        # Keep stats for reporting
        logger.info(f"Terminated dual-channel transcription for session {session_id}")
    
    async def process_audio_frame(self, session_id: str, participant_id: str, 
                                 audio_data: bytes, timestamp: float = None) -> None:
        """Process an incoming audio frame from a participant"""
        if session_id not in self.active_sessions or participant_id not in self.session_participants.get(session_id, {}):
            logger.warning(f"Received audio from unknown session/participant: {session_id}/{participant_id}")
            return
        
        if timestamp is None:
            timestamp = time.time()
        
        # Update session activity
        self.active_sessions[session_id]['last_activity'] = timestamp
        
        # Detect if this frame contains speech
        is_speaking = self._detect_speech(audio_data, self.active_sessions[session_id]['audio_config']['sample_rate'])
        
        # Create audio frame object
        frame = AudioFrame(
            participant_id=participant_id,
            audio_data=audio_data,
            timestamp=timestamp,
            is_speaking=is_speaking,
            sample_rate=self.active_sessions[session_id]['audio_config']['sample_rate'],
            format=self.active_sessions[session_id]['audio_config'].get('format', 'pcm')
        )
        
        # Add to buffer for processing
        async with self.session_locks[session_id]:
            self.audio_buffers[session_id][participant_id].append(frame)
            
            # Keep buffer size reasonable (last 5 seconds)
            max_frames = 5 * (self.active_sessions[session_id]['audio_config']['sample_rate'] // 320)
            if len(self.audio_buffers[session_id][participant_id]) > max_frames:
                self.audio_buffers[session_id][participant_id] = self.audio_buffers[session_id][participant_id][-max_frames:]
    
    async def _transcription_worker(self, session_id: str) -> None:
        """Background worker that processes audio and generates transcripts"""
        try:
            logger.info(f"Starting transcription worker for session {session_id}")
            
            while session_id in self.active_sessions:
                try:
                    # Check for speaking status changes and speech segments
                    async with self.session_locks[session_id]:
                        for participant_id in self.session_participants[session_id]:
                            # Get latest frames
                            frames = self.audio_buffers[session_id][participant_id]
                            if not frames:
                                continue
                            
                            # Process speech state transitions
                            current_speaking = any(f.is_speaking for f in frames[-3:])  # Use last 3 frames for stability
                            previous_speaking = self.speaking_status[session_id][participant_id]
                            
                            if current_speaking != previous_speaking:
                                # Speaking state transition
                                self.speaking_status[session_id][participant_id] = current_speaking
                                
                                if current_speaking:
                                    # Speech started
                                    await self._handle_speech_start(session_id, participant_id, frames)
                                else:
                                    # Speech ended
                                    await self._handle_speech_end(session_id, participant_id, frames)
                            
                            # If currently speaking, accumulate frames
                            if current_speaking and self.active_speech_segments[session_id][participant_id]:
                                self.active_speech_segments[session_id][participant_id]['frames'].extend(frames)
                                self.active_speech_segments[session_id][participant_id]['end_time'] = frames[-1].timestamp
                    
                    # Transcribe long-running segments periodically
                    await self._process_long_running_segments(session_id)
                    
                    # Update diarization (who is speaking when)
                    await self._update_diarization(session_id)
                    
                    # Short sleep
                    await asyncio.sleep(0.05)  # 50ms
                
                except asyncio.CancelledError:
                    raise
                except Exception as e:
                    logger.error(f"Error in transcription worker for session {session_id}: {e}")
                    await asyncio.sleep(0.1)
        
        except asyncio.CancelledError:
            logger.info(f"Transcription worker cancelled for session {session_id}")
        except Exception as e:
            logger.error(f"Fatal error in transcription worker for {session_id}: {e}")
    
    async def _handle_speech_start(self, session_id: str, participant_id: str, frames: List[AudioFrame]) -> None:
        """Handle the start of a speech segment"""
        # Create a new active speech segment
        self.active_speech_segments[session_id][participant_id] = {
            'start_time': frames[0].timestamp,
            'end_time': frames[-1].timestamp,
            'frames': frames.copy(),
            'is_processed': False
        }
        
        # Broadcast speaking state change
        await self._broadcast_speaking_state(session_id, participant_id, True)
        
        logger.debug(f"Speech started: session={session_id}, participant={participant_id}")
    
    async def _handle_speech_end(self, session_id: str, participant_id: str, frames: List[AudioFrame]) -> None:
        """Handle the end of a speech segment"""
        active_segment = self.active_speech_segments[session_id][participant_id]
        if not active_segment:
            return
        
        # Update end time
        active_segment['end_time'] = frames[-1].timestamp
        
        # Process the speech segment if long enough
        if len(active_segment['frames']) >= 10:  # Minimum frames threshold
            speech_duration = active_segment['end_time'] - active_segment['start_time']
            if speech_duration >= 0.5:  # Minimum duration 500ms
                # Extract audio data
                audio_data = b''.join([f.audio_data for f in active_segment['frames']])
                sample_rate = active_segment['frames'][0].sample_rate
                
                # Transcribe audio
                await self._transcribe_speech_segment(
                    session_id, 
                    participant_id, 
                    audio_data, 
                    sample_rate, 
                    active_segment['start_time'], 
                    active_segment['end_time']
                )
                
                # Update statistics
                self.stats[session_id]['total_speech_segments'] += 1
                self.stats[session_id]['participant_stats'][participant_id]['speech_segments'] += 1
                self.stats[session_id]['participant_stats'][participant_id]['speaking_time'] += speech_duration
        
        # Clear the active segment
        self.active_speech_segments[session_id][participant_id] = None
        
        # Broadcast speaking state change
        await self._broadcast_speaking_state(session_id, participant_id, False)
        
        logger.debug(f"Speech ended: session={session_id}, participant={participant_id}")
    
    async def _process_long_running_segments(self, session_id: str) -> None:
        """Process speech segments that have been running for a while"""
        for participant_id, segment in self.active_speech_segments[session_id].items():
            if not segment:
                continue
            
            current_time = time.time()
            segment_duration = current_time - segment['start_time']
            
            # Process segments running longer than 3 seconds
            if segment_duration > 3.0 and len(segment['frames']) > 30:
                # Extract audio from first part
                frames_to_process = segment['frames'][:-15]  # Keep last ~500ms buffer unprocessed
                audio_data = b''.join([f.audio_data for f in frames_to_process])
                sample_rate = segment['frames'][0].sample_rate
                end_time = segment['frames'][-15].timestamp
                
                # Transcribe this portion
                await self._transcribe_speech_segment(
                    session_id,
                    participant_id,
                    audio_data,
                    sample_rate,
                    segment['start_time'],
                    end_time,
                    is_final=False
                )
                
                # Update segment to only contain the unprocessed part
                segment['frames'] = segment['frames'][-15:]
                segment['start_time'] = segment['frames'][0].timestamp
    
    async def _transcribe_speech_segment(self, session_id: str, participant_id: str, 
                                        audio_data: bytes, sample_rate: int,
                                        start_time: float, end_time: float,
                                        is_final: bool = True) -> None:
        """Transcribe a speech segment using the best available engine"""
        try:
            transcript_text = ""
            confidence = 0.0
            words = []
            
            # Try Google STT first if available
            if 'google' in self.speech_engines:
                try:
                    from google.cloud import speech
                    client = self.speech_engines['google']
                    
                    audio = speech.RecognitionAudio(content=audio_data)
                    config = speech.RecognitionConfig(
                        encoding=speech.RecognitionConfig.AudioEncoding.LINEAR16,
                        sample_rate_hertz=sample_rate,
                        language_code="en-US",
                        enable_automatic_punctuation=True,
                        enable_word_confidence=True,
                        enable_word_time_offsets=True
                    )
                    
                    response = await asyncio.get_event_loop().run_in_executor(
                        None, lambda: client.recognize(config=config, audio=audio)
                    )
                    
                    if response.results:
                        result = response.results[0]
                        alternative = result.alternatives[0]
                        transcript_text = alternative.transcript
                        confidence = alternative.confidence
                        
                        # Extract word information with timestamps
                        for word_info in alternative.words:
                            word_start = start_time + word_info.start_time.total_seconds()
                            word_end = start_time + word_info.end_time.total_seconds()
                            words.append({
                                'word': word_info.word,
                                'start_time': word_start,
                                'end_time': word_end,
                                'confidence': word_info.confidence
                            })
                
                except Exception as e:
                    logger.warning(f"Google STT failed: {e}")
            
            # Fallback to Vosk if Google failed or unavailable
            if not transcript_text and 'vosk' in self.speech_engines:
                try:
                    import vosk
                    model = self.speech_engines['vosk']
                    recognizer = vosk.KaldiRecognizer(model, sample_rate)
                    
                    # Process audio data
                    result = await asyncio.get_event_loop().run_in_executor(
                        None, lambda: recognizer.AcceptWaveform(audio_data) and json.loads(recognizer.Result())
                    )
                    
                    if result and 'text' in result and result['text']:
                        transcript_text = result['text']
                        confidence = result.get('confidence', 0.5)
                        
                        # Extract word information
                        if 'result' in result:
                            duration = end_time - start_time
                            word_count = len(result['result'])
                            for i, word_info in enumerate(result['result']):
                                # Estimate timestamps (Vosk sometimes provides conf but not precise timestamps)
                                word_start = start_time + (duration * (i / word_count))
                                word_end = start_time + (duration * ((i + 1) / word_count))
                                words.append({
                                    'word': word_info['word'],
                                    'start_time': word_start,
                                    'end_time': word_end,
                                    'confidence': word_info.get('conf', 0.5)
                                })
                
                except Exception as e:
                    logger.warning(f"Vosk STT failed: {e}")
            
            # If we got a transcript, create a segment and broadcast
            if transcript_text.strip():
                # Create transcript segment
                segment = TranscriptSegment(
                    session_id=session_id,
                    participant_id=participant_id,
                    text=transcript_text,
                    start_time=start_time,
                    end_time=end_time,
                    confidence=confidence,
                    is_final=is_final,
                    words=words
                )
                
                # Store transcript segment
                await self._store_transcript_segment(segment)
                
                # Broadcast to connected clients
                await self._broadcast_transcript_segment(segment)
                
                # Update statistics
                word_count = len(transcript_text.split())
                self.stats[session_id]['total_transcribed_words'] += word_count
                self.stats[session_id]['participant_stats'][participant_id]['transcribed_words'] += word_count
                
                logger.debug(f"Transcribed segment: session={session_id}, participant={participant_id}, "
                            f"words={word_count}, duration={end_time-start_time:.2f}s")
        
        except Exception as e:
            logger.error(f"Error transcribing speech segment: {e}")
    
    async def _store_transcript_segment(self, segment: TranscriptSegment) -> None:
        """Store transcript segment in Redis"""
        try:
            # Store segment in Redis
            key = f"transcript:{segment.session_id}:segments"
            segment_data = segment.json()
            redis_client.lpush(key, segment_data)
            redis_client.expire(key, 86400)  # 24 hour TTL
            
            # Also store in participant-specific list
            participant_key = f"transcript:{segment.session_id}:participant:{segment.participant_id}"
            redis_client.lpush(participant_key, segment_data)
            redis_client.expire(participant_key, 86400)  # 24 hour TTL
        
        except Exception as e:
            logger.error(f"Error storing transcript segment: {e}")
    
    async def _broadcast_transcript_segment(self, segment: TranscriptSegment) -> None:
        """Broadcast transcript segment to WebSocket clients"""
        try:
            # Get participant info
            participant = self.session_participants[segment.session_id].get(segment.participant_id)
            if not participant:
                return
            
            # Create message with participant details
            message = {
                'type': 'transcript_segment',
                'session_id': segment.session_id,
                'participant': {
                    'id': participant.id,
                    'name': participant.name,
                    'role': participant.role
                },
                'text': segment.text,
                'start_time': segment.start_time,
                'end_time': segment.end_time,
                'duration': segment.end_time - segment.start_time,
                'confidence': segment.confidence,
                'is_final': segment.is_final,
                'words': segment.words,
                'timestamp': datetime.now().isoformat()
            }
            
            # Broadcast via connection manager
            await self.connection_manager.broadcast_to_session(segment.session_id, message)
        
        except Exception as e:
            logger.error(f"Error broadcasting transcript segment: {e}")
    
    async def _broadcast_speaking_state(self, session_id: str, participant_id: str, is_speaking: bool) -> None:
        """Broadcast speaking state changes to WebSocket clients"""
        try:
            # Get participant info
            participant = self.session_participants[session_id].get(participant_id)
            if not participant:
                return
            
            message = {
                'type': 'speaking_state',
                'session_id': session_id,
                'participant': {
                    'id': participant.id,
                    'name': participant.name,
                    'role': participant.role
                },
                'is_speaking': is_speaking,
                'timestamp': datetime.now().isoformat()
            }
            
            # Broadcast via connection manager
            await self.connection_manager.broadcast_to_session(session_id, message)
        
        except Exception as e:
            logger.error(f"Error broadcasting speaking state: {e}")
    
    async def _update_diarization(self, session_id: str) -> None:
        """Update the diarization window (who is speaking when)"""
        try:
            current_time = time.time()
            speaking_participants = set()
            
            for participant_id, is_speaking in self.speaking_status[session_id].items():
                if is_speaking:
                    speaking_participants.add(participant_id)
            
            # Add a diarization entry
            self.diarization_windows[session_id].append({
                'timestamp': current_time,
                'speaking_participants': list(speaking_participants)
            })
            
            # Keep last 30 seconds of diarization data
            cutoff = current_time - 30
            self.diarization_windows[session_id] = [
                entry for entry in self.diarization_windows[session_id] 
                if entry['timestamp'] > cutoff
            ]
        
        except Exception as e:
            logger.error(f"Error updating diarization: {e}")
    
    async def _generate_final_transcript(self, session_id: str) -> None:
        """Generate the final transcript for the session"""
        try:
            # Get all segments from Redis
            key = f"transcript:{session_id}:segments"
            segment_data = redis_client.lrange(key, 0, -1)
            
            if not segment_data:
                logger.warning(f"No transcript segments found for session {session_id}")
                return
            
            # Parse segments
            segments = []
            for json_data in segment_data:
                try:
                    segment = TranscriptSegment.parse_raw(json_data)
                    segments.append(segment)
                except Exception:
                    continue
            
            # Sort by start time
            segments.sort(key=lambda s: s.start_time)
            
            # Group by participant
            participant_segments = {}
            for segment in segments:
                if segment.participant_id not in participant_segments:
                    participant_segments[segment.participant_id] = []
                participant_segments[segment.participant_id].append(segment)
            
            # Generate merged transcript in chronological order
            merged_lines = []
            for segment in segments:
                participant = self.session_participants[session_id].get(segment.participant_id)
                if not participant:
                    continue
                
                timestamp = datetime.fromtimestamp(segment.start_time).strftime('%H:%M:%S')
                merged_lines.append(f"[{timestamp}] {participant.name} ({participant.role}): {segment.text}")
            
            merged_transcript = "\n".join(merged_lines)
            
            # Store final transcript
            final_key = f"transcript:{session_id}:final"
            redis_client.set(final_key, merged_transcript)
            redis_client.expire(final_key, 86400 * 7)  # 7 day TTL
            
            # Generate participant-specific transcripts
            for participant_id, p_segments in participant_segments.items():
                participant = self.session_participants[session_id].get(participant_id)
                if not participant:
                    continue
                
                p_lines = []
                for segment in p_segments:
                    timestamp = datetime.fromtimestamp(segment.start_time).strftime('%H:%M:%S')
                    p_lines.append(f"[{timestamp}] {segment.text}")
                
                p_transcript = "\n".join(p_lines)
                p_key = f"transcript:{session_id}:participant:{participant_id}:final"
                redis_client.set(p_key, p_transcript)
                redis_client.expire(p_key, 86400 * 7)  # 7 day TTL
            
            # Generate statistics summary
            stats_key = f"transcript:{session_id}:stats"
            redis_client.set(stats_key, json.dumps(self.stats[session_id]))
            redis_client.expire(stats_key, 86400 * 7)  # 7 day TTL
            
            logger.info(f"Generated final transcript for session {session_id} with "
                       f"{len(segments)} segments from {len(participant_segments)} participants")
        
        except Exception as e:
            logger.error(f"Error generating final transcript: {e}")
    
    def _detect_speech(self, audio_data: bytes, sample_rate: int) -> bool:
        """Detect if an audio frame contains speech using WebRTC VAD"""
        try:
            # Ensure frame size is compatible with WebRTC VAD
            # WebRTC VAD requires 10, 20, or 30 ms frames at 8kHz, 16kHz, or 32kHz
            
            # For simplicity, assuming we have 30ms frames at 16kHz
            # WebRTC VAD expects 16-bit PCM samples
            if len(audio_data) == 960:  # 30ms at 16kHz is 480 samples * 2 bytes per sample
                return self.vad.is_speech(audio_data, sample_rate)
        
        except Exception as e:
            logger.debug(f"VAD error: {e}")
        
        # Default to not speaking if error
        return False
    
    async def get_session_stats(self, session_id: str) -> Dict[str, Any]:
        """Get statistics for a transcription session"""
        if session_id not in self.stats:
            # Try to load from Redis if session was previously completed
            stats_key = f"transcript:{session_id}:stats"
            stats_json = redis_client.get(stats_key)
            if stats_json:
                return json.loads(stats_json)
            return {}
        
        # Get latest stats
        return self.stats[session_id]
    
    async def get_participant_transcript(self, session_id: str, participant_id: str, 
                                        format: str = "text") -> Optional[str]:
        """Get transcript for a specific participant"""
        try:
            # Check if we have a final transcript
            p_key = f"transcript:{session_id}:participant:{participant_id}:final"
            transcript = redis_client.get(p_key)
            
            if transcript:
                return transcript
            
            # Otherwise, generate from segments
            participant_key = f"transcript:{session_id}:participant:{participant_id}"
            segment_data = redis_client.lrange(participant_key, 0, -1)
            
            if not segment_data:
                return None
            
            # Parse segments
            segments = []
            for json_data in segment_data:
                try:
                    segment = TranscriptSegment.parse_raw(json_data)
                    segments.append(segment)
                except Exception:
                    continue
            
            # Sort by start time
            segments.sort(key=lambda s: s.start_time)
            
            if format == "text":
                # Generate text transcript
                lines = []
                for segment in segments:
                    timestamp = datetime.fromtimestamp(segment.start_time).strftime('%H:%M:%S')
                    lines.append(f"[{timestamp}] {segment.text}")
                
                return "\n".join(lines)
            
            elif format == "json":
                # Return structured JSON
                return json.dumps([s.dict() for s in segments])
            
            else:
                raise ValueError(f"Unsupported format: {format}")
        
        except Exception as e:
            logger.error(f"Error getting participant transcript: {e}")
            return None
    
    async def get_speaking_timeline(self, session_id: str) -> List[Dict[str, Any]]:
        """Get timeline of who was speaking when"""
        if session_id not in self.diarization_windows:
            return []
        
        return self.diarization_windows[session_id]
