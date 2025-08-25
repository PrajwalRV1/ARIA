#!/usr/bin/env python3
"""
Voice Synthesis Integration for AI Avatar Service
Coordinates speech synthesis with interview flow and emotional context
"""

import asyncio
import json
import logging
import aiohttp
import websockets
from typing import Dict, Optional, Any
from datetime import datetime

logger = logging.getLogger(__name__)

class VoiceSynthesisIntegration:
    """Integrates voice synthesis with AI avatar interview flow"""
    
    def __init__(self, voice_service_url: str = "http://localhost:8007"):  # Updated to correct voice synthesis service port
        self.voice_service_url = voice_service_url
        self.websocket_connections: Dict[str, websockets.WebSocketClientProtocol] = {}
        self.emotional_context_map = {
            # Interview stages to emotional contexts
            "introduction": {"emotion": "friendly", "intensity": 0.7, "formality": "professional"},
            "technical_questions": {"emotion": "professional", "intensity": 0.6, "formality": "formal"},
            "coding_challenge": {"emotion": "focused", "intensity": 0.5, "formality": "professional"},
            "behavioral_questions": {"emotion": "empathetic", "intensity": 0.6, "formality": "professional"},
            "encouragement": {"emotion": "supportive", "intensity": 0.8, "formality": "casual"},
            "feedback": {"emotion": "neutral", "intensity": 0.5, "formality": "professional"},
            "conclusion": {"emotion": "positive", "intensity": 0.7, "formality": "professional"}
        }
    
    async def connect_to_voice_service(self, session_id: str) -> None:
        """Establish WebSocket connection to voice synthesis service"""
        try:
            ws_url = f"ws://localhost:8007/ws/voice/{session_id}"  # Updated to correct voice synthesis service port
            websocket = await websockets.connect(ws_url)
            self.websocket_connections[session_id] = websocket
            
            # Listen for responses
            asyncio.create_task(self._handle_voice_responses(session_id, websocket))
            
            logger.info(f"Connected to voice synthesis service for session {session_id}")
        
        except Exception as e:
            logger.error(f"Failed to connect to voice synthesis service: {e}")
    
    async def disconnect_from_voice_service(self, session_id: str) -> None:
        """Disconnect from voice synthesis service"""
        if session_id in self.websocket_connections:
            try:
                await self.websocket_connections[session_id].close()
                del self.websocket_connections[session_id]
                logger.info(f"Disconnected from voice synthesis service for session {session_id}")
            except Exception as e:
                logger.error(f"Error disconnecting from voice synthesis service: {e}")
    
    async def synthesize_speech(self, session_id: str, text: str, 
                              interview_stage: str = "general", 
                              urgency: str = "normal") -> Optional[Dict[str, Any]]:
        """Synthesize speech with appropriate emotional context for interview stage"""
        try:
            # Determine emotional context based on interview stage
            emotional_context = self._get_emotional_context(interview_stage, urgency)
            
            # Choose voice profile based on context
            voice_profile = self._select_voice_profile(interview_stage, emotional_context)
            
            # Prepare synthesis request
            synthesis_request = {
                "type": "synthesize_request",
                "text": text,
                "participant_id": "ai_avatar",
                "emotional_context": emotional_context,
                "voice_profile": voice_profile
            }
            
            # Send via WebSocket if available, otherwise use HTTP
            if session_id in self.websocket_connections:
                return await self._synthesize_via_websocket(session_id, synthesis_request)
            else:
                return await self._synthesize_via_http(session_id, synthesis_request)
        
        except Exception as e:
            logger.error(f"Error synthesizing speech: {e}")
            return None
    
    async def _synthesize_via_websocket(self, session_id: str, request: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Synthesize speech via WebSocket connection"""
        try:
            websocket = self.websocket_connections[session_id]
            
            # Send synthesis request
            await websocket.send(json.dumps(request))
            
            # Wait for response (with timeout)
            response = await asyncio.wait_for(websocket.recv(), timeout=10.0)
            response_data = json.loads(response)
            
            if response_data.get('type') == 'synthesis_response':
                return {
                    'audio_data': response_data.get('audio_data'),
                    'duration_ms': response_data.get('duration_ms'),
                    'format': response_data.get('format'),
                    'engine_used': response_data.get('engine_used')
                }
            else:
                logger.error(f"Unexpected response from voice service: {response_data}")
                return None
        
        except asyncio.TimeoutError:
            logger.error("Voice synthesis request timed out")
            return None
        except Exception as e:
            logger.error(f"Error in WebSocket voice synthesis: {e}")
            return None
    
    async def _synthesize_via_http(self, session_id: str, request_data: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Synthesize speech via HTTP API"""
        try:
            # Prepare HTTP request
            synthesis_request = {
                "session_id": session_id,
                "participant_id": request_data.get('participant_id', 'ai_avatar'),
                "text": request_data.get('text', ''),
                "stream": False,
                "format": "wav",
                "sample_rate": 16000
            }
            
            # Add voice profile if specified
            if 'voice_profile' in request_data:
                synthesis_request['voice_profile'] = request_data['voice_profile']
            
            async with aiohttp.ClientSession() as session:
                async with session.post(
                    f"{self.voice_service_url}/synthesize",
                    json=synthesis_request
                ) as response:
                    if response.status == 200:
                        result = await response.json()
                        return {
                            'audio_data': result.get('audio_data'),
                            'duration_ms': result.get('duration_ms'),
                            'format': result.get('format'),
                            'engine_used': result.get('engine_used')
                        }
                    else:
                        logger.error(f"HTTP synthesis failed with status {response.status}")
                        return None
        
        except Exception as e:
            logger.error(f"Error in HTTP voice synthesis: {e}")
            return None
    
    def _get_emotional_context(self, interview_stage: str, urgency: str = "normal") -> Dict[str, Any]:
        """Get emotional context for the current interview stage"""
        base_context = self.emotional_context_map.get(interview_stage, {
            "emotion": "professional",
            "intensity": 0.5,
            "formality": "professional"
        })
        
        # Adjust for urgency
        intensity_adjustment = {
            "low": -0.1,
            "normal": 0.0,
            "high": 0.1,
            "urgent": 0.2
        }
        
        emotional_context = {
            "emotion": base_context["emotion"],
            "intensity": max(0.0, min(1.0, base_context["intensity"] + intensity_adjustment.get(urgency, 0.0))),
            "context_type": "interview",
            "urgency": urgency,
            "formality": base_context["formality"]
        }
        
        return emotional_context
    
    def _select_voice_profile(self, interview_stage: str, emotional_context: Dict[str, Any]) -> Dict[str, Any]:
        """Select appropriate voice profile based on interview stage and emotional context"""
        # Base voice profile
        voice_profile = {
            "voice_id": "ai_avatar_professional",
            "language": "en-US",
            "gender": "neutral",
            "age": "adult",
            "speed": 1.0,
            "pitch": 0.0,
            "volume": 0.0,
            "emotion": emotional_context["emotion"],
            "style": "conversational"
        }
        
        # Adjust based on interview stage
        stage_adjustments = {
            "introduction": {"speed": 0.95, "pitch": 1.0, "style": "friendly"},
            "technical_questions": {"speed": 1.0, "pitch": 0.0, "style": "professional"},
            "coding_challenge": {"speed": 0.9, "pitch": -0.5, "style": "focused"},
            "behavioral_questions": {"speed": 0.95, "pitch": 0.5, "style": "empathetic"},
            "encouragement": {"speed": 1.05, "pitch": 2.0, "style": "supportive"},
            "feedback": {"speed": 0.9, "pitch": 0.0, "style": "constructive"},
            "conclusion": {"speed": 1.0, "pitch": 1.0, "style": "positive"}
        }
        
        if interview_stage in stage_adjustments:
            adjustments = stage_adjustments[interview_stage]
            voice_profile.update(adjustments)
        
        # Fine-tune based on emotional intensity
        intensity = emotional_context["intensity"]
        if intensity > 0.7:
            voice_profile["speed"] = min(voice_profile["speed"] + 0.1, 1.5)
            voice_profile["pitch"] = min(voice_profile["pitch"] + 1.0, 10.0)
        elif intensity < 0.3:
            voice_profile["speed"] = max(voice_profile["speed"] - 0.1, 0.7)
            voice_profile["pitch"] = max(voice_profile["pitch"] - 1.0, -10.0)
        
        return voice_profile
    
    async def _handle_voice_responses(self, session_id: str, websocket: websockets.WebSocketClientProtocol):
        """Handle responses from voice synthesis service"""
        try:
            async for message in websocket:
                try:
                    data = json.loads(message)
                    message_type = data.get('type')
                    
                    if message_type == 'voice_synthesis_connected':
                        logger.info(f"Voice synthesis WebSocket connected for session {session_id}")
                    
                    elif message_type == 'synthesis_response':
                        # Audio synthesis completed
                        logger.debug(f"Speech synthesized: {data.get('duration_ms')}ms using {data.get('engine_used')}")
                    
                    elif message_type == 'synthesis_error':
                        logger.error(f"Voice synthesis error: {data.get('error')}")
                    
                    else:
                        logger.debug(f"Received voice service message: {message_type}")
                
                except json.JSONDecodeError:
                    logger.warning(f"Invalid JSON from voice service: {message}")
        
        except websockets.exceptions.ConnectionClosed:
            logger.info(f"Voice synthesis WebSocket closed for session {session_id}")
        except Exception as e:
            logger.error(f"Error handling voice responses: {e}")
    
    def get_voice_for_response_type(self, response_type: str) -> str:
        """Get appropriate interview stage key for different response types"""
        response_type_map = {
            "greeting": "introduction",
            "question_ask": "technical_questions",
            "code_review": "coding_challenge", 
            "encouragement": "encouragement",
            "clarification": "technical_questions",
            "feedback": "feedback",
            "transition": "general",
            "closing": "conclusion",
            "behavioral_question": "behavioral_questions"
        }
        
        return response_type_map.get(response_type, "general")
    
    async def synthesize_interview_response(self, session_id: str, response_text: str, 
                                          response_type: str = "general", 
                                          urgency: str = "normal") -> Optional[Dict[str, Any]]:
        """Synthesize speech for an interview response with contextually appropriate voice"""
        interview_stage = self.get_voice_for_response_type(response_type)
        return await self.synthesize_speech(session_id, response_text, interview_stage, urgency)
    
    async def get_available_voices(self) -> Optional[Dict[str, Any]]:
        """Get list of available voices from the synthesis service"""
        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(f"{self.voice_service_url}/voices") as response:
                    if response.status == 200:
                        return await response.json()
                    else:
                        logger.error(f"Failed to get available voices: {response.status}")
                        return None
        
        except Exception as e:
            logger.error(f"Error getting available voices: {e}")
            return None
