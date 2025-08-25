#!/usr/bin/env python3
"""
Response Consolidation Service for ARIA Interview Platform
Aggregates fragmented STT responses into single coherent candidate responses
"""

import asyncio
import json
import logging
import time
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
from dataclasses import dataclass
from enum import Enum

logger = logging.getLogger(__name__)

class ResponseState(Enum):
    LISTENING = "listening"
    COLLECTING = "collecting" 
    FINALIZING = "finalizing"
    COMPLETED = "completed"

@dataclass
class ResponseFragment:
    """Individual speech fragment from STT"""
    session_id: str
    text: str
    confidence: float
    timestamp: float
    is_final: bool
    source: str = "speech"

@dataclass
class ConsolidatedResponse:
    """Complete consolidated candidate response"""
    session_id: str
    consolidated_text: str
    fragments: List[ResponseFragment]
    start_time: float
    end_time: float
    average_confidence: float
    word_count: int
    total_duration: float
    is_complete: bool = True

class ResponseConsolidator:
    """Consolidates fragmented STT responses into coherent single responses"""
    
    def __init__(self, connection_manager):
        self.connection_manager = connection_manager
        
        # Active response sessions
        self.active_responses: Dict[str, Dict[str, Any]] = {}
        
        # Consolidation settings
        self.silence_threshold = 2.0  # 2 seconds of silence ends response
        self.max_response_duration = 300.0  # 5 minutes max response time
        self.min_response_length = 10  # Minimum 10 characters for valid response
        
        # Background task for monitoring timeouts
        self._monitor_task = None
        self._running = True
        
    async def start(self):
        """Start the response consolidator service"""
        self._monitor_task = asyncio.create_task(self._monitor_response_timeouts())
        logger.info("✅ Response Consolidator started")
    
    async def stop(self):
        """Stop the response consolidator service"""
        self._running = False
        if self._monitor_task:
            self._monitor_task.cancel()
        logger.info("🛑 Response Consolidator stopped")
    
    async def start_response_collection(self, session_id: str) -> None:
        """Start collecting response fragments for a session"""
        logger.info(f"🎙️ Starting response collection for session: {session_id}")
        
        current_time = time.time()
        self.active_responses[session_id] = {
            "state": ResponseState.LISTENING,
            "fragments": [],
            "start_time": current_time,
            "last_fragment_time": current_time,
            "silence_start": None,
            "total_text": "",
            "total_confidence": 0.0,
            "fragment_count": 0
        }
        
        # Notify frontend that response collection started
        await self._notify_frontend(session_id, {
            "type": "response_collection_started",
            "timestamp": datetime.now().isoformat()
        })
    
    async def add_fragment(self, session_id: str, fragment: ResponseFragment) -> None:
        """Add a speech fragment to the active response"""
        if session_id not in self.active_responses:
            logger.warning(f"⚠️ No active response for session {session_id}, starting collection")
            await self.start_response_collection(session_id)
        
        response_data = self.active_responses[session_id]
        current_time = time.time()
        
        # Only process fragments with actual text content
        if not fragment.text.strip():
            return
            
        logger.debug(f"📝 Adding fragment to {session_id}: '{fragment.text[:50]}...'")
        
        # Update response state
        if response_data["state"] == ResponseState.LISTENING:
            response_data["state"] = ResponseState.COLLECTING
            
        # Add fragment
        response_data["fragments"].append(fragment)
        response_data["last_fragment_time"] = current_time
        response_data["silence_start"] = None  # Reset silence timer
        response_data["fragment_count"] += 1
        
        # Update consolidated text
        if response_data["total_text"]:
            response_data["total_text"] += " " + fragment.text
        else:
            response_data["total_text"] = fragment.text
            
        # Update confidence tracking
        response_data["total_confidence"] += fragment.confidence
        
        # Check if this is a final fragment that should end the response
        if fragment.is_final and self._should_end_response(response_data):
            await self._finalize_response(session_id)
            return
        
        # Notify frontend of live transcript update
        await self._notify_frontend(session_id, {
            "type": "live_transcript_update",
            "text": response_data["total_text"],
            "confidence": response_data["total_confidence"] / response_data["fragment_count"],
            "is_final": False,
            "timestamp": datetime.now().isoformat()
        })
    
    async def end_response(self, session_id: str) -> Optional[ConsolidatedResponse]:
        """Manually end response collection (triggered by space bar or End Response button)"""
        logger.info(f"🏁 Ending response collection for session: {session_id}")
        
        if session_id not in self.active_responses:
            logger.warning(f"⚠️ No active response to end for session {session_id}")
            return None
            
        return await self._finalize_response(session_id)
    
    async def _finalize_response(self, session_id: str) -> Optional[ConsolidatedResponse]:
        """Finalize and consolidate the response"""
        if session_id not in self.active_responses:
            return None
            
        response_data = self.active_responses[session_id]
        response_data["state"] = ResponseState.FINALIZING
        
        current_time = time.time()
        
        # Create consolidated response
        consolidated = ConsolidatedResponse(
            session_id=session_id,
            consolidated_text=self._clean_and_consolidate_text(response_data["total_text"]),
            fragments=response_data["fragments"],
            start_time=response_data["start_time"],
            end_time=current_time,
            average_confidence=response_data["total_confidence"] / max(response_data["fragment_count"], 1),
            word_count=len(response_data["total_text"].split()),
            total_duration=current_time - response_data["start_time"]
        )
        
        # Validate response quality
        if not self._is_valid_response(consolidated):
            logger.warning(f"⚠️ Invalid response quality for session {session_id}")
            # Don't finalize, keep collecting
            response_data["state"] = ResponseState.COLLECTING
            return None
        
        # Mark as completed
        response_data["state"] = ResponseState.COMPLETED
        
        logger.info(f"✅ Response finalized for session {session_id}: "
                   f"{len(consolidated.consolidated_text)} chars, "
                   f"{consolidated.word_count} words, "
                   f"{consolidated.total_duration:.1f}s duration")
        
        # Notify frontend of final consolidated response
        await self._notify_frontend(session_id, {
            "type": "response_finalized",
            "consolidated_response": {
                "text": consolidated.consolidated_text,
                "word_count": consolidated.word_count,
                "duration": consolidated.total_duration,
                "confidence": consolidated.average_confidence,
                "fragment_count": len(consolidated.fragments)
            },
            "timestamp": datetime.now().isoformat()
        })
        
        # Clean up active response
        del self.active_responses[session_id]
        
        return consolidated
    
    def _clean_and_consolidate_text(self, text: str) -> str:
        """Clean and consolidate the fragmented text"""
        if not text:
            return ""
        
        # Remove duplicate phrases that often occur in STT fragmentation
        words = text.split()
        cleaned_words = []
        
        # Remove consecutive duplicates
        prev_word = None
        for word in words:
            if word.lower() != prev_word:
                cleaned_words.append(word)
            prev_word = word.lower()
        
        # Join and clean punctuation
        consolidated = " ".join(cleaned_words)
        
        # Basic punctuation cleanup
        consolidated = consolidated.replace(" ,", ",")
        consolidated = consolidated.replace(" .", ".")
        consolidated = consolidated.replace(" ?", "?")
        consolidated = consolidated.replace(" !", "!")
        
        # Ensure proper sentence endings
        if consolidated and not consolidated[-1] in '.?!':
            consolidated += "."
            
        return consolidated.strip()
    
    def _should_end_response(self, response_data: Dict[str, Any]) -> bool:
        """Determine if response should be automatically ended"""
        current_time = time.time()
        
        # Check for silence timeout
        if response_data["silence_start"]:
            silence_duration = current_time - response_data["silence_start"]
            if silence_duration >= self.silence_threshold:
                return True
        
        # Check for maximum duration
        total_duration = current_time - response_data["start_time"]
        if total_duration >= self.max_response_duration:
            return True
            
        return False
    
    def _is_valid_response(self, response: ConsolidatedResponse) -> bool:
        """Validate if the consolidated response meets quality criteria"""
        # Minimum length check
        if len(response.consolidated_text) < self.min_response_length:
            return False
            
        # Minimum confidence check
        if response.average_confidence < 0.3:
            return False
            
        # At least one word
        if response.word_count < 1:
            return False
            
        return True
    
    async def _monitor_response_timeouts(self):
        """Background task to monitor and handle response timeouts"""
        while self._running:
            try:
                current_time = time.time()
                sessions_to_finalize = []
                
                for session_id, response_data in self.active_responses.items():
                    if response_data["state"] in [ResponseState.COLLECTING, ResponseState.LISTENING]:
                        
                        # Check for silence timeout
                        if response_data["fragments"]:  # Only if we have fragments
                            silence_duration = current_time - response_data["last_fragment_time"]
                            if silence_duration >= self.silence_threshold:
                                if not response_data["silence_start"]:
                                    response_data["silence_start"] = current_time
                                elif current_time - response_data["silence_start"] >= 1.0:  # 1 second grace
                                    sessions_to_finalize.append(session_id)
                        
                        # Check for max duration timeout
                        total_duration = current_time - response_data["start_time"]
                        if total_duration >= self.max_response_duration:
                            sessions_to_finalize.append(session_id)
                
                # Finalize timed-out sessions
                for session_id in sessions_to_finalize:
                    logger.info(f"⏰ Auto-finalizing response due to timeout: {session_id}")
                    await self._finalize_response(session_id)
                
                await asyncio.sleep(0.5)  # Check every 500ms
                
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"❌ Error in response timeout monitor: {e}")
                await asyncio.sleep(1)
    
    async def _notify_frontend(self, session_id: str, message: Dict[str, Any]):
        """Send notification to frontend via WebSocket"""
        try:
            await self.connection_manager.broadcast_to_session(session_id, message)
        except Exception as e:
            logger.error(f"❌ Failed to notify frontend for session {session_id}: {e}")
    
    def get_active_response_status(self, session_id: str) -> Optional[Dict[str, Any]]:
        """Get status of active response collection"""
        if session_id not in self.active_responses:
            return None
            
        response_data = self.active_responses[session_id]
        current_time = time.time()
        
        return {
            "session_id": session_id,
            "state": response_data["state"].value,
            "fragment_count": response_data["fragment_count"],
            "current_text": response_data["total_text"],
            "duration": current_time - response_data["start_time"],
            "word_count": len(response_data["total_text"].split()),
            "average_confidence": (response_data["total_confidence"] / max(response_data["fragment_count"], 1))
        }
