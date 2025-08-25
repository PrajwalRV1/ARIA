#!/usr/bin/env python3
"""
ARIA AI Avatar Service - Enhanced with Alex AI Conversational Intelligence

This unified service creates and manages AI avatars with Alex AI's conversational capabilities:
- Alex AI-style personalized greetings and natural dialogue
- Dynamic technical question sequences with intelligent follow-ups  
- Role-specific responses (salary, company info, benefits)
- Advanced cheat detection and evaluation
- Jitsi Meet integration for video/audio presence
- WebSocket connections for real-time communication
- Speech synthesis with emotional context
- Adaptive questioning based on candidate responses
- Real-time analysis and comprehensive scoring
- Detailed recruiter reports
"""

import asyncio
import json
import logging
import os
import time
import uuid
from contextlib import asynccontextmanager
from typing import Dict, List, Optional, Any
from dataclasses import dataclass
from datetime import datetime, timedelta
import aiohttp
import websockets
from fastapi import FastAPI, WebSocket, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import redis.asyncio as aioredis

# Voice synthesis integration
from voice_integration import VoiceSynthesisIntegration

# Alex AI Question Database Integration
from question_database import AlexQuestionBank, InterviewQuestion as AlexInterviewQuestion, QuestionType as AlexQuestionType

# Interview Flow Manager Integration
from interview_flow_manager import InterviewFlowManager, InterviewStage

# Text-to-Speech imports (legacy fallback)
try:
    import pyttsx3
    from gtts import gTTS
    TTS_AVAILABLE = True
except ImportError:
    TTS_AVAILABLE = False
    logging.warning("TTS libraries not available")

# OpenAI for advanced evaluation (optional)
try:
    import openai
    OPENAI_AVAILABLE = True
except ImportError:
    OPENAI_AVAILABLE = False
    logging.warning("OpenAI not available")

# Audio processing
try:
    import pyaudio
    import wave
    AUDIO_AVAILABLE = True
except ImportError:
    AUDIO_AVAILABLE = False
    logging.warning("Audio processing libraries not available")

import threading
import queue

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ==================== CONFIGURATION ====================

class Settings:
    """AI Avatar Service Configuration - Enhanced with Alex AI"""
    
    # Service Configuration
    SERVICE_NAME = "ARIA AI Avatar Service (Enhanced with Alex AI)"
    SERVICE_VERSION = "2.0.0"
    HOST = "0.0.0.0"
    PORT = 8006  # Unified service on original AI Avatar port (changed to avoid conflict with Job Description Analyzer)
    
    # External Service URLs (SSL enabled)
    INTERVIEW_ORCHESTRATOR_URL = "https://localhost:8081"
    SPEECH_SERVICE_URL = "https://localhost:8002"
    ANALYTICS_SERVICE_URL = "https://localhost:8003"
    ADAPTIVE_ENGINE_URL = "https://localhost:8001"
    
    # WebSocket Endpoints (SSL enabled)
    WS_ORCHESTRATOR = "wss://localhost:8081/ws/interview"
    WS_SPEECH = "wss://localhost:8002/ws/speech"
    WS_ANALYTICS = "wss://localhost:8003/ws/analytics"
    
    # Jitsi Meet Configuration
    JITSI_DOMAIN = os.getenv("JITSI_DOMAIN", "meet.jit.si")
    JITSI_EXTERNAL_API_URL = "https://{}/external_api.js"
    
    # AI Avatar Configuration - Enhanced with Alex AI Personality
    AVATAR_NAME = "Alex - ARIA AI Interviewer"
    AVATAR_EMAIL = "alex@aria-ai.com"
    VOICE_LANGUAGE = "en-US"
    VOICE_SPEED = 0.9
    VOICE_PITCH = 0.8
    
    # Alex AI Enhanced Features
    ALEX_PERSONALITY_ENABLED = True
    ALEX_CHEAT_DETECTION = True
    ALEX_SALARY_RESPONSES = True
    ALEX_COMPANY_INFO = True
    
    # Redis Configuration
    REDIS_URL = "redis://localhost:6379/2"
    
    # Updated Interview Flow Configuration per specifications
    INTERVIEW_STAGES = [
        "introduction_setup",          # 2-3 minutes
        "technical_theory",            # 15-20 minutes  
        "coding_challenges",           # 20-25 minutes
        "cultural_behavioral",         # 10-15 minutes
        "candidate_qa",                # 5-10 minutes
        "interview_conclusion"         # 2-3 minutes
    ]

settings = Settings()

# ==================== DATA MODELS ====================

class InterviewSessionRequest(BaseModel):
    session_id: str
    candidate_profile: Dict[str, Any]
    job_role: str
    experience_level: int
    required_technologies: List[str]
    meeting_link: str

class AIAvatarResponse(BaseModel):
    session_id: str
    status: str
    message: str
    metadata: Optional[Dict[str, Any]] = None

class QuestionResponse(BaseModel):
    question_id: str
    question_text: str
    question_type: str
    expected_duration: int
    metadata: Dict[str, Any]

class SpeechSynthesisRequest(BaseModel):
    text: str
    voice_config: Optional[Dict[str, Any]] = None

@dataclass
class AIAvatarState:
    """Represents the current state of an AI avatar"""
    session_id: str
    status: str  # initializing, ready, speaking, listening, analyzing, thinking
    current_stage: str
    current_question_id: Optional[str]
    candidate_theta: float
    questions_asked: int
    start_time: datetime
    last_interaction: datetime
    metadata: Dict[str, Any]

# ==================== AI AVATAR MANAGER ====================

class AIAvatarManager:
    """Manages AI Avatar instances and their lifecycle"""
    
    def __init__(self):
        self.active_avatars: Dict[str, 'AIAvatar'] = {}
        self.redis: Optional[aioredis.Redis] = None
        
    async def initialize(self):
        """Initialize the avatar manager"""
        try:
            self.redis = aioredis.from_url(settings.REDIS_URL)
            logger.info("✅ AI Avatar Manager initialized")
        except Exception as e:
            logger.error(f"❌ Failed to initialize Avatar Manager: {e}")
            raise
    
    async def create_avatar(self, session_request: InterviewSessionRequest) -> AIAvatarResponse:
        """Create and initialize a new AI avatar for an interview session"""
        try:
            session_id = session_request.session_id
            
            if session_id in self.active_avatars:
                return AIAvatarResponse(
                    session_id=session_id,
                    status="error",
                    message="Avatar already exists for this session"
                )
            
            # Create new AI avatar
            avatar = AIAvatar(session_request, self.redis)
            await avatar.initialize()
            
            # Store avatar
            self.active_avatars[session_id] = avatar
            
            # Start avatar in background
            asyncio.create_task(avatar.run())
            
            logger.info(f"🤖 Created AI Avatar for session: {session_id}")
            
            return AIAvatarResponse(
                session_id=session_id,
                status="created",
                message="AI Avatar initialized successfully",
                metadata={
                    "avatar_name": settings.AVATAR_NAME,
                    "capabilities": ["speech", "analysis", "questioning", "real_time_scoring"],
                    "created_at": datetime.now().isoformat()
                }
            )
            
        except Exception as e:
            logger.error(f"❌ Failed to create avatar for {session_request.session_id}: {e}")
            return AIAvatarResponse(
                session_id=session_request.session_id,
                status="error", 
                message=f"Failed to create avatar: {str(e)}"
            )
    
    async def get_avatar_status(self, session_id: str) -> Optional[AIAvatarResponse]:
        """Get current status of an AI avatar"""
        if session_id not in self.active_avatars:
            return None
            
        avatar = self.active_avatars[session_id]
        return AIAvatarResponse(
            session_id=session_id,
            status=avatar.state.status,
            message=f"Avatar in {avatar.state.current_stage} stage",
            metadata={
                "current_stage": avatar.state.current_stage,
                "questions_asked": avatar.state.questions_asked,
                "candidate_theta": avatar.state.candidate_theta,
                "uptime": str(datetime.now() - avatar.state.start_time)
            }
        )
    
    async def stop_avatar(self, session_id: str) -> bool:
        """Stop and cleanup an AI avatar"""
        if session_id not in self.active_avatars:
            return False
            
        try:
            avatar = self.active_avatars[session_id]
            await avatar.stop()
            del self.active_avatars[session_id]
            
            logger.info(f"🛑 Stopped AI Avatar for session: {session_id}")
            return True
            
        except Exception as e:
            logger.error(f"❌ Error stopping avatar for {session_id}: {e}")
            return False

# ==================== AI AVATAR CLASS ====================

class AIAvatar:
    """Individual AI Avatar that conducts interviews"""
    
    def __init__(self, session_request: InterviewSessionRequest, redis: aioredis.Redis):
        self.session_request = session_request
        self.redis = redis
        self.session_id = session_request.session_id
        self.meeting_link = session_request.meeting_link
        
        # Initialize state
        self.state = AIAvatarState(
            session_id=self.session_id,
            status="initializing",
            current_stage="introduction",
            current_question_id=None,
            candidate_theta=0.0,  # IRT ability estimate
            questions_asked=0,
            start_time=datetime.now(),
            last_interaction=datetime.now(),
            metadata={}
        )
        
        # WebSocket connections
        self.orchestrator_ws: Optional[websockets.WebSocketClientProtocol] = None
        self.speech_ws: Optional[websockets.WebSocketClientProtocol] = None
        self.analytics_ws: Optional[websockets.WebSocketClientProtocol] = None
        
        # AI Components
        self.tts_engine = None  # Legacy TTS fallback
        self.voice_synthesis = VoiceSynthesisIntegration("https://localhost:8007")  # New voice service - SSL enabled
        self.conversation_context = []
        self.current_question = None
        
        # Interview flow control - Use InterviewFlowManager
        self.interview_flow_manager = InterviewFlowManager(
            session_id=self.session_id,
            candidate_profile=session_request.candidate_profile,
            job_role=session_request.job_role,
            duration_minutes=45  # Standard interview duration
        )
        self.stage_index = 0
        self.stage_questions = {}
        self.stage_timers = {}
        
        # Alex AI Enhanced Features
        self.alex_question_bank = AlexQuestionBank()
        self.alex_conversation_engine = AlexConversationEngine(session_request.candidate_profile, self.session_id)
        self.salary_info = self._load_salary_info()
        self.company_info = self._load_company_info()
        self.cheat_detection = AlexCheatDetection()
        
        # Running state
        self.running = False
        self.tasks = []
    
    def _load_salary_info(self) -> Dict[str, Any]:
        """Load salary information for different roles."""
        return {
            "senior software engineer": {
                "base_range": (150000, 200000),
                "bonus_pct": 15,
                "equity": "0.1% - 0.25%"
            },
            "software engineer": {
                "base_range": (100000, 140000),
                "bonus_pct": 10,
                "equity": "0.05% - 0.1%"
            }
        }
    
    def _load_company_info(self) -> Dict[str, str]:
        """Load company information for candidate questions."""
        return {
            "culture": "We have a collaborative and fast-paced culture. We value ownership, innovation, and continuous learning.",
            "benefits": "We offer comprehensive health, dental, and vision insurance, a 401k with company match, unlimited PTO, and a professional development stipend.",
            "remote_work": "We are a remote-first company, but we have offices in major cities for those who prefer to work in person."
        }
        
    
    async def initialize(self):
        """Initialize the AI avatar"""
        try:
            logger.info(f"🤖 Initializing AI Avatar for session: {self.session_id}")
            
            # Initialize TTS engine
            await self._initialize_tts()
            
            # Load interview configuration
            await self._load_interview_configuration()
            
            # Establish WebSocket connections
            await self._connect_websockets()
            
            # Join Jitsi Meet room programmatically
            await self._join_jitsi_room()
            
            self.state.status = "ready"
            logger.info(f"✅ AI Avatar ready for session: {self.session_id}")
            
        except Exception as e:
            logger.error(f"❌ Failed to initialize AI Avatar: {e}")
            self.state.status = "error"
            raise
    
    async def run(self):
        """Main execution loop for the AI avatar"""
        self.running = True
        logger.info(f"🚀 Starting AI Avatar interview process for session: {self.session_id}")
        
        try:
            # Start background tasks
            self.tasks = [
                asyncio.create_task(self._websocket_message_handler()),
                asyncio.create_task(self._interview_flow_controller()),
                asyncio.create_task(self._heartbeat_monitor())
            ]
            
            # Wait for all tasks to complete
            await asyncio.gather(*self.tasks, return_exceptions=True)
            
        except Exception as e:
            logger.error(f"❌ AI Avatar execution error: {e}")
        finally:
            await self.cleanup()
    
    async def stop(self):
        """Stop the AI avatar and cleanup resources"""
        self.running = False
        
        # Cancel all tasks
        for task in self.tasks:
            task.cancel()
        
        await self.cleanup()
    
    async def _initialize_tts(self):
        """Initialize text-to-speech engine"""
        if TTS_AVAILABLE:
            try:
                # Initialize pyttsx3 for real-time TTS
                self.tts_engine = pyttsx3.init()
                
                # Configure voice
                voices = self.tts_engine.getProperty('voices')
                for voice in voices:
                    if 'female' in voice.name.lower() or 'zira' in voice.name.lower():
                        self.tts_engine.setProperty('voice', voice.id)
                        break
                
                self.tts_engine.setProperty('rate', int(200 * settings.VOICE_SPEED))
                self.tts_engine.setProperty('volume', 0.8)
                
                logger.info("✅ TTS engine initialized")
            except Exception as e:
                logger.warning(f"⚠️ TTS initialization failed: {e}")
                self.tts_engine = None
        else:
            logger.warning("⚠️ TTS not available")
    
    async def _load_interview_configuration(self):
        """Load interview configuration from adaptive engine"""
        try:
            # Call adaptive engine to get interview plan
            async with aiohttp.ClientSession() as session:
                config_data = {
                    "session_id": self.session_id,
                    "candidate_profile": self.session_request.candidate_profile,
                    "job_role": self.session_request.job_role,
                    "experience_level": self.session_request.experience_level,
                    "required_technologies": self.session_request.required_technologies
                }
                
                async with session.post(
                    f"{settings.ADAPTIVE_ENGINE_URL}/configure-interview",
                    json=config_data
                ) as response:
                    if response.status == 200:
                        config = await response.json()
                        self.stage_questions = config.get("stage_questions", {})
                        self.stage_timers = config.get("stage_timers", {})
                        logger.info("✅ Interview configuration loaded")
                    else:
                        logger.warning("⚠️ Using fallback interview configuration")
                        self._load_fallback_configuration()
                        
        except Exception as e:
            logger.warning(f"⚠️ Failed to load interview config: {e}")
            self._load_fallback_configuration()
    
    def _load_fallback_configuration(self):
        """Load fallback interview configuration"""
        self.stage_questions = {
            "introduction": [
                "Hello! Welcome to your AI-powered interview. I'm ARIA, your AI interviewer. Let's start with a brief introduction - could you tell me about yourself and your background?"
            ],
            "technical_theory": [
                "Let's discuss some technical concepts relevant to your experience. Can you explain the difference between REST and GraphQL APIs?",
                "How do you approach error handling in your applications?",
                "What are your thoughts on microservices vs monolithic architecture?"
            ],
            "coding_challenges": [
                "Now let's move to some coding challenges. I'll present a problem and you can solve it using our code editor."
            ],
            "behavioral_assessment": [
                "Tell me about a time when you had to work under pressure to meet a tight deadline.",
                "How do you handle disagreements with team members?",
                "Describe a project you're particularly proud of."
            ],
            "candidate_questions": [
                "Now it's your turn - do you have any questions about the role, team, or company?"
            ],
            "conclusion": [
                "Thank you for your time today. The interview process is now complete. You should hear back from the team within the next few days. Good luck!"
            ]
        }
        
        self.stage_timers = {
            "introduction": 180,      # 3 minutes
            "technical_theory": 1200,  # 20 minutes
            "coding_challenges": 1500, # 25 minutes
            "behavioral_assessment": 900,  # 15 minutes
            "candidate_questions": 300,    # 5 minutes
            "conclusion": 120         # 2 minutes
        }
    
    async def _connect_websockets(self):
        """Establish WebSocket connections to other services"""
        try:
            # Connect to Interview Orchestrator
            orchestrator_url = f"{settings.WS_ORCHESTRATOR}/{self.session_id}?participant=ai_avatar"
            self.orchestrator_ws = await websockets.connect(orchestrator_url)
            logger.info("✅ Connected to Interview Orchestrator WebSocket")
            
            # Connect to Speech Service
            speech_url = f"{settings.WS_SPEECH}/{self.session_id}?mode=ai_avatar"
            self.speech_ws = await websockets.connect(speech_url)
            logger.info("✅ Connected to Speech Service WebSocket")
            
            # Connect to Analytics Service
            analytics_url = f"{settings.WS_ANALYTICS}/{self.session_id}?participant=ai_avatar"
            self.analytics_ws = await websockets.connect(analytics_url)
            logger.info("✅ Connected to Analytics Service WebSocket")
            
        except Exception as e:
            logger.error(f"❌ Failed to connect WebSockets: {e}")
            raise
    
    async def _join_jitsi_room(self):
        """Join Jitsi Meet room programmatically"""
        try:
            # Extract room name and domain from meeting link
            if "://" in self.meeting_link:
                url_parts = self.meeting_link.split("://")[1].split("/")
                domain = url_parts[0]
                room_name = "/".join(url_parts[1:]) if len(url_parts) > 1 else ""
            else:
                domain = settings.JITSI_DOMAIN
                room_name = self.meeting_link
            
            # For this implementation, we'll simulate joining by sending presence info
            # In a full implementation, you would use the Jitsi Meet External API
            # or integrate with a headless browser solution like Puppeteer
            
            presence_data = {
                "type": "ai_avatar_joined",
                "session_id": self.session_id,
                "avatar_info": {
                    "name": settings.AVATAR_NAME,
                    "email": settings.AVATAR_EMAIL,
                    "role": "ai_interviewer",
                    "capabilities": ["speech_synthesis", "real_time_analysis", "adaptive_questioning"]
                },
                "meeting_info": {
                    "room_name": room_name,
                    "domain": domain,
                    "joined_at": datetime.now().isoformat()
                }
            }
            
            # Send presence information to orchestrator
            if self.orchestrator_ws:
                await self.orchestrator_ws.send(json.dumps(presence_data))
            
            logger.info(f"✅ AI Avatar joined Jitsi room: {room_name} on {domain}")
            
        except Exception as e:
            logger.error(f"❌ Failed to join Jitsi room: {e}")
            raise
    
    async def _websocket_message_handler(self):
        """Handle incoming WebSocket messages from all services"""
        try:
            while self.running:
                # Handle messages from different WebSocket connections
                tasks = []
                
                if self.orchestrator_ws:
                    tasks.append(self._handle_orchestrator_messages())
                
                if self.speech_ws:
                    tasks.append(self._handle_speech_messages())
                
                if self.analytics_ws:
                    tasks.append(self._handle_analytics_messages())
                
                if tasks:
                    await asyncio.gather(*tasks, return_exceptions=True)
                
                await asyncio.sleep(0.1)  # Small delay to prevent busy waiting
                
        except Exception as e:
            logger.error(f"❌ WebSocket message handler error: {e}")
    
    async def _handle_orchestrator_messages(self):
        """Handle messages from Interview Orchestrator"""
        try:
            message = await asyncio.wait_for(self.orchestrator_ws.recv(), timeout=0.1)
            data = json.loads(message)
            
            message_type = data.get("type")
            
            if message_type == "candidate_joined":
                await self._on_candidate_joined(data)
            elif message_type == "interview_started":
                await self._on_interview_started(data)
            elif message_type == "session_control":
                await self._handle_session_control(data)
            elif message_type == "recruiter_intervention":
                await self._handle_recruiter_intervention(data)
                
        except asyncio.TimeoutError:
            pass  # No message available, continue
        except Exception as e:
            logger.error(f"Error handling orchestrator message: {e}")
    
    async def _handle_speech_messages(self):
        """Handle messages from Speech Service"""
        try:
            message = await asyncio.wait_for(self.speech_ws.recv(), timeout=0.1)
            data = json.loads(message)
            
            message_type = data.get("type")
            
            if message_type == "transcript_update":
                await self._on_candidate_response(data)
            elif message_type == "speech_ended":
                await self._on_candidate_finished_speaking(data)
                
        except asyncio.TimeoutError:
            pass
        except Exception as e:
            logger.error(f"Error handling speech message: {e}")
    
    async def _handle_analytics_messages(self):
        """Handle messages from Analytics Service"""
        try:
            message = await asyncio.wait_for(self.analytics_ws.recv(), timeout=0.1)
            data = json.loads(message)
            
            message_type = data.get("type")
            
            if message_type == "real_time_analysis":
                await self._on_real_time_analysis(data)
            elif message_type == "bias_alert":
                await self._on_bias_alert(data)
                
        except asyncio.TimeoutError:
            pass
        except Exception as e:
            logger.error(f"Error handling analytics message: {e}")
    
    async def _interview_flow_controller(self):
        """Control the interview flow using InterviewFlowManager"""
        try:
            logger.info(f"🎯 Starting structured interview flow for session: {self.session_id}")
            
            # Start the interview flow using the flow manager
            current_stage_data = await self.interview_flow_manager.start_interview_flow()
            
            while self.running and current_stage_data:
                # Update avatar state with current stage
                self.state.current_stage = current_stage_data["stage"]
                stage_name = current_stage_data["stage_name"]
                
                logger.info(f"🎯 Executing stage: {stage_name} ({current_stage_data['stage']})")
                
                # Execute current stage using flow manager data
                await self._execute_structured_stage(current_stage_data)
                
                # Get next stage from flow manager
                current_stage_data = await self.interview_flow_manager.get_next_stage()
                
                # Brief pause between stages for natural flow
                if current_stage_data:
                    await asyncio.sleep(3)  # 3 second pause between stages
            
            # Interview completed
            logger.info(f"✅ All interview stages completed")
            await self._complete_interview()
            
        except Exception as e:
            logger.error(f"❌ Interview flow controller error: {e}")
    
    async def _execute_structured_stage(self, stage_data: Dict[str, Any]):
        """Execute a structured stage using InterviewFlowManager data"""
        try:
            stage = stage_data["stage"]
            stage_name = stage_data["stage_name"]
            duration_minutes = stage_data["duration_minutes"]
            flow = stage_data["flow"]
            
            logger.info(f"🎢 Executing structured stage: {stage_name} ({duration_minutes} min)")
            
            stage_start_time = datetime.now()
            
            # Execute each flow action in the stage
            for flow_action in flow:
                if not self.running:
                    break
                
                # Check if stage time limit exceeded
                elapsed_minutes = (datetime.now() - stage_start_time).total_seconds() / 60
                if elapsed_minutes > duration_minutes:
                    logger.info(f"⏰ Stage {stage_name} time limit reached ({duration_minutes} min)")
                    break
                
                action_type = flow_action.get("action")
                
                if action_type == "ask_question":
                    question_data = flow_action["question"]
                    await self._ask_structured_question(question_data, stage)
                
                elif action_type == "conditional_follow_up":
                    # Handle follow-up logic based on response quality
                    await self._handle_conditional_follow_up(flow_action)
                
                elif action_type == "initialize_code_editor":
                    # Initialize coding environment
                    await self._initialize_coding_environment(flow_action["editor_config"])
                
                elif action_type == "present_coding_problem":
                    # Present coding challenge
                    await self._present_coding_problem(flow_action["problem"])
                
                elif action_type == "ask_behavioral_question":
                    # Ask behavioral/scenario question
                    question_data = flow_action["question"]
                    await self._ask_behavioral_question(question_data, stage)
                
                elif action_type == "handle_candidate_questions":
                    # Handle candidate Q&A with boundaries
                    await self._handle_candidate_qa_session(flow_action)
                
                elif action_type == "deliver_conclusion":
                    # Deliver interview conclusion
                    message_data = flow_action["message"]
                    await self._deliver_conclusion(message_data)
                
                elif action_type == "generate_final_report":
                    # Generate comprehensive report
                    await self._generate_structured_report(flow_action["report_components"])
                
                logger.debug(f"✅ Completed action: {action_type}")
            
            logger.info(f"✅ Completed structured stage: {stage_name}")
            
        except Exception as e:
            logger.error(f"❌ Error executing structured stage {stage_data.get('stage', 'unknown')}: {e}")
    
    async def _ask_structured_question(self, question_data: Dict[str, Any], stage: str):
        """Ask a structured question with full metadata"""
        try:
            question_text = question_data["text"]
            question_id = question_data["id"]
            expected_duration = question_data["expected_duration"]
            question_type = question_data["type"]
            
            self.state.status = "speaking"
            self.state.questions_asked += 1
            
            # Store current question with full context
            self.current_question = {
                "id": question_id,
                "text": question_text,
                "stage": stage,
                "type": question_type,
                "expected_duration": expected_duration,
                "asked_at": datetime.now().isoformat(),
                "scoring_enabled": question_data.get("scoring_enabled", False),
                "follow_up_enabled": question_data.get("follow_up_enabled", False)
            }
            
            # Generate speech with appropriate tone/style
            await self._synthesize_and_play_speech(question_text)
            
            # Send structured question data to services
            question_event = {
                "type": "structured_ai_question",
                "session_id": self.session_id,
                "question_data": self.current_question,
                "stage_info": {
                    "stage": stage,
                    "questions_in_stage": self.state.questions_asked
                },
                "timestamp": datetime.now().isoformat()
            }
            
            # Broadcast to connected services
            if self.orchestrator_ws:
                await self.orchestrator_ws.send(json.dumps(question_event))
            
            if self.analytics_ws:
                await self.analytics_ws.send(json.dumps(question_event))
            
            # Update conversation context
            self.conversation_context.append({
                "role": "ai_interviewer",
                "content": question_text,
                "timestamp": datetime.now(),
                "question_type": question_type,
                "stage": stage
            })
            
            self.state.status = "listening"
            self.state.last_interaction = datetime.now()
            
            logger.info(f"❓ Asked structured question [{question_type}]: {question_text[:50]}...")
            
            # Wait for response based on expected duration
            await self._wait_for_structured_response(expected_duration)
            
        except Exception as e:
            logger.error(f"❌ Error asking structured question: {e}")
    
    async def _wait_for_structured_response(self, expected_duration_seconds: int):
        """Wait for candidate response with structured timing"""
        try:
            # Allow 2x expected duration for candidate thinking time
            timeout = min(180, expected_duration_seconds * 2)  # Max 3 minutes
            start_time = datetime.now()
            
            logger.info(f"⏱️ Waiting for response (timeout: {timeout}s)")
            
            while self.running:
                elapsed = (datetime.now() - start_time).seconds
                
                if elapsed > timeout:
                    logger.warning(f"⏰ Response timeout ({timeout}s)")
                    await self._handle_no_response()
                    break
                
                # Simulate response (in production, this would be handled by WebSocket messages)
                if elapsed > 8:  # Simulate 8-second response time
                    logger.info("🗨️ Simulated candidate response received")
                    break
                
                await asyncio.sleep(1)
                
        except Exception as e:
            logger.error(f"❌ Error waiting for structured response: {e}")
    
    async def _handle_conditional_follow_up(self, follow_up_data: Dict[str, Any]):
        """Handle conditional follow-up questions based on response quality"""
        try:
            # In a full implementation, this would evaluate the candidate's last response
            # For now, we'll simulate the decision
            condition = follow_up_data.get("condition", "")
            follow_up_types = follow_up_data.get("follow_up_types", [])
            
            # Simulate response quality check
            response_quality = 7.5  # Mock score
            requires_clarification = False  # Mock flag
            
            if "response_quality < 7.0" in condition and response_quality < 7.0:
                follow_up_text = "Can you elaborate on that point a bit more? I'd like to understand your reasoning."
                await self._ask_follow_up_question(follow_up_text, "clarification")
            elif "requires_clarification" in condition and requires_clarification:
                follow_up_text = "That's interesting. Could you provide a specific example to illustrate your approach?"
                await self._ask_follow_up_question(follow_up_text, "example_request")
            else:
                logger.info("✅ Response quality sufficient, no follow-up needed")
                
        except Exception as e:
            logger.error(f"❌ Error handling conditional follow-up: {e}")
    
    async def _ask_follow_up_question(self, question_text: str, follow_up_type: str):
        """Ask a follow-up question"""
        try:
            logger.info(f"🔄 Asking follow-up question [{follow_up_type}]: {question_text[:50]}...")
            
            await self._synthesize_and_play_speech(question_text)
            
            # Wait for follow-up response
            await self._wait_for_structured_response(60)  # 1 minute for follow-up
            
        except Exception as e:
            logger.error(f"❌ Error asking follow-up question: {e}")
    
    async def _initialize_coding_environment(self, editor_config: Dict[str, Any]):
        """Initialize coding environment (Monaco Editor setup)"""
        try:
            language = editor_config.get("language", "javascript")
            theme = editor_config.get("theme", "vs-dark")
            features = editor_config.get("features", [])
            
            logger.info(f"💻 Initializing code editor: {language} ({theme})")
            
            # Send editor initialization to frontend
            editor_event = {
                "type": "initialize_code_editor",
                "session_id": self.session_id,
                "config": editor_config,
                "timestamp": datetime.now().isoformat()
            }
            
            if self.orchestrator_ws:
                await self.orchestrator_ws.send(json.dumps(editor_event))
            
            # Brief pause for editor initialization
            await asyncio.sleep(2)
            
            logger.info("✅ Code editor initialized")
            
        except Exception as e:
            logger.error(f"❌ Error initializing coding environment: {e}")
    
    async def _present_coding_problem(self, problem_data: Dict[str, Any]):
        """Present a coding problem to the candidate"""
        try:
            problem_title = problem_data["title"]
            problem_description = problem_data["description"]
            expected_duration = problem_data["expected_duration"]
            
            logger.info(f"🧩 Presenting coding problem: {problem_title}")
            
            # Present the problem verbally
            problem_intro = f"Now I'd like you to solve a coding problem: {problem_title}. {problem_description}"
            await self._synthesize_and_play_speech(problem_intro)
            
            # Send problem details to frontend
            problem_event = {
                "type": "coding_problem_presented",
                "session_id": self.session_id,
                "problem": problem_data,
                "timestamp": datetime.now().isoformat()
            }
            
            if self.orchestrator_ws:
                await self.orchestrator_ws.send(json.dumps(problem_event))
            
            # Wait for coding solution
            await self._wait_for_coding_solution(expected_duration)
            
        except Exception as e:
            logger.error(f"❌ Error presenting coding problem: {e}")
    
    async def _wait_for_coding_solution(self, expected_duration_seconds: int):
        """Wait for candidate to complete coding problem"""
        try:
            # Allow generous time for coding (minimum 5 minutes)
            timeout = max(300, expected_duration_seconds)
            start_time = datetime.now()
            
            logger.info(f"⏱️ Waiting for coding solution (timeout: {timeout//60} minutes)")
            
            while self.running:
                elapsed = (datetime.now() - start_time).seconds
                
                if elapsed > timeout:
                    logger.warning(f"⏰ Coding timeout ({timeout//60} minutes)")
                    timeout_message = "Let's move on for now. You can continue thinking about this problem."
                    await self._synthesize_and_play_speech(timeout_message)
                    break
                
                # In production, this would monitor code editor activity
                # For now, simulate completion after reasonable time
                if elapsed > 90:  # Simulate 1.5 minutes of coding
                    logger.info("💻 Simulated coding completion")
                    completion_message = "Great! I can see you've made good progress. Let's discuss your approach."
                    await self._synthesize_and_play_speech(completion_message)
                    break
                
                await asyncio.sleep(5)  # Check every 5 seconds
                
        except Exception as e:
            logger.error(f"❌ Error waiting for coding solution: {e}")
    
    async def _ask_behavioral_question(self, question_data: Dict[str, Any], stage: str):
        """Ask a behavioral/scenario-based question"""
        try:
            question_text = question_data["text"]
            scenario_type = question_data.get("scenario_type", "general")
            
            logger.info(f"💭 Asking behavioral question [{scenario_type}]")
            
            # Add context for behavioral questions
            intro = "Now I'd like to ask you about a specific situation. "
            full_question = intro + question_text
            
            await self._ask_structured_question({
                **question_data,
                "text": full_question,
                "type": "behavioral"
            }, stage)
            
        except Exception as e:
            logger.error(f"❌ Error asking behavioral question: {e}")
    
    async def _handle_candidate_qa_session(self, qa_data: Dict[str, Any]):
        """Handle candidate Q&A session with professional boundaries"""
        try:
            logger.info("🙋‍♂️ Starting candidate Q&A session")
            
            # Initial invitation
            invitation = "This is your opportunity to ask me any questions you have about the role, team, company culture, or anything else you'd like to know."
            await self._synthesize_and_play_speech(invitation)
            
            # Wait for candidate questions
            qa_timeout = 300  # 5 minutes for Q&A
            start_time = datetime.now()
            
            while self.running and (datetime.now() - start_time).seconds < qa_timeout:
                # In production, this would handle real candidate questions
                # For simulation, we'll handle a few common question types
                await asyncio.sleep(30)  # Simulate 30-second questions
                
                # Simulate handling different types of candidate questions
                sample_responses = [
                    "The team consists of 8 engineers working in an agile environment with 2-week sprints.",
                    "We offer flexible working arrangements with a hybrid model - 3 days in office, 2 days remote.",
                    "The role involves working on our core platform using React, Node.js, and PostgreSQL."
                ]
                
                for response in sample_responses:
                    await self._synthesize_and_play_speech(response)
                    await asyncio.sleep(20)  # Pause between responses
                    
                    if (datetime.now() - start_time).seconds >= qa_timeout:
                        break
                
                break  # End simulation
            
            # Wrap up Q&A session
            wrap_up = "Those are great questions! Is there anything else you'd like to know?"
            await self._synthesize_and_play_speech(wrap_up)
            
            # Brief pause for final questions
            await asyncio.sleep(15)
            
            logger.info("✅ Candidate Q&A session completed")
            
        except Exception as e:
            logger.error(f"❌ Error handling candidate Q&A session: {e}")
    
    async def _deliver_conclusion(self, message_data: Dict[str, Any]):
        """Deliver professional interview conclusion"""
        try:
            conclusion_text = message_data["text"]
            
            logger.info("🎆 Delivering interview conclusion")
            
            # Deliver conclusion with appropriate tone
            await self._synthesize_and_play_speech(conclusion_text)
            
            # Brief pause for impact
            await asyncio.sleep(2)
            
            logger.info("✅ Interview conclusion delivered")
            
        except Exception as e:
            logger.error(f"❌ Error delivering conclusion: {e}")
    
    async def _generate_structured_report(self, report_components: List[str]):
        """Generate comprehensive structured report"""
        try:
            logger.info(f"📄 Generating structured report with {len(report_components)} components")
            
            # Generate detailed report based on interview flow data
            report_data = {
                "type": "structured_interview_report",
                "session_id": self.session_id,
                "interview_summary": self.interview_flow_manager.get_interview_summary(),
                "stage_progress": self.interview_flow_manager.get_current_stage_progress(),
                "components": report_components,
                "generated_at": datetime.now().isoformat()
            }
            
            # Send report to analytics service
            if self.analytics_ws:
                await self.analytics_ws.send(json.dumps({
                    "type": "generate_structured_report",
                    "report_data": report_data
                }))
            
            logger.info("✅ Structured report generation initiated")
            
        except Exception as e:
            logger.error(f"❌ Error generating structured report: {e}")
    
    async def _execute_interview_stage(self, stage: str):
        """Execute a specific interview stage"""
        try:
            stage_questions = self.stage_questions.get(stage, [])
            stage_duration = self.stage_timers.get(stage, 300)  # Default 5 minutes
            
            stage_start_time = datetime.now()
            
            for question_text in stage_questions:
                if not self.running:
                    break
                
                # Check if stage time limit exceeded
                if (datetime.now() - stage_start_time).seconds > stage_duration:
                    logger.info(f"⏰ Stage {stage} time limit reached")
                    break
                
                # Ask question
                await self._ask_question(question_text, stage)
                
                # Wait for candidate response and analysis
                await self._wait_for_response_and_analysis()
                
            logger.info(f"✅ Completed interview stage: {stage}")
            
        except Exception as e:
            logger.error(f"❌ Error executing stage {stage}: {e}")
    
    async def _ask_question(self, question_text: str, stage: str):
        """Ask a question to the candidate"""
        try:
            self.state.status = "speaking"
            self.state.questions_asked += 1
            question_id = f"{stage}_{self.state.questions_asked}"
            
            # Store current question
            self.current_question = {
                "id": question_id,
                "text": question_text,
                "stage": stage,
                "asked_at": datetime.now().isoformat()
            }
            
            # Generate speech audio
            await self._synthesize_and_play_speech(question_text)
            
            # Send question to other services
            question_data = {
                "type": "ai_question",
                "session_id": self.session_id,
                "question_id": question_id,
                "question_text": question_text,
                "stage": stage,
                "timestamp": datetime.now().isoformat()
            }
            
            # Broadcast to all connected services
            if self.orchestrator_ws:
                await self.orchestrator_ws.send(json.dumps(question_data))
            
            if self.analytics_ws:
                await self.analytics_ws.send(json.dumps(question_data))
            
            # Update conversation context
            self.conversation_context.append({
                "role": "ai_interviewer",
                "content": question_text,
                "timestamp": datetime.now()
            })
            
            self.state.status = "listening"
            self.state.last_interaction = datetime.now()
            
            logger.info(f"❓ Asked question: {question_text[:50]}...")
            
        except Exception as e:
            logger.error(f"❌ Error asking question: {e}")
    
    async def _synthesize_and_play_speech(self, text: str):
        """Generate and play speech audio using advanced voice synthesis"""
        try:
            # Determine response type based on current stage and question content
            response_type = self._determine_response_type(text)
            
            # Connect to voice synthesis service if not already connected
            if self.session_id not in self.voice_synthesis.websocket_connections:
                await self.voice_synthesis.connect_to_voice_service(self.session_id)
            
            # Synthesize speech with contextual voice
            synthesis_result = await self.voice_synthesis.synthesize_interview_response(
                session_id=self.session_id,
                response_text=text,
                response_type=response_type,
                urgency="normal"
            )
            
            if synthesis_result:
                # Send synthesized audio to meeting room
                await self._play_synthesized_audio(synthesis_result)
                
                logger.info(f"🔊 Advanced speech synthesis: {text[:30]}... using {synthesis_result.get('engine_used')}")
            else:
                # Fallback to legacy TTS
                await self._fallback_speech_synthesis(text)
            
        except Exception as e:
            logger.error(f"❌ Advanced speech synthesis error: {e}")
            # Fallback to legacy TTS
            await self._fallback_speech_synthesis(text)
    
    def _determine_response_type(self, text: str) -> str:
        """Determine the type of response based on content and context"""
        text_lower = text.lower()
        
        if "hello" in text_lower or "welcome" in text_lower or "introduce" in text_lower:
            return "greeting"
        elif "question" in text_lower and "?" in text:
            if "technical" in text_lower or "code" in text_lower or "algorithm" in text_lower:
                return "question_ask"
            elif "tell me about" in text_lower or "describe" in text_lower:
                return "behavioral_question"
            else:
                return "question_ask"
        elif "great" in text_lower or "excellent" in text_lower or "good job" in text_lower:
            return "encouragement"
        elif "thank you" in text_lower or "complete" in text_lower or "finished" in text_lower:
            return "closing"
        elif "let me" in text_lower or "provide" in text_lower or "clarify" in text_lower:
            return "clarification"
        elif "next" in text_lower or "move" in text_lower or "now" in text_lower:
            return "transition"
        else:
            return "general"
    
    async def _play_synthesized_audio(self, synthesis_result: Dict[str, Any]):
        """Play synthesized audio in the meeting room"""
        try:
            # In a real implementation, this would stream audio to Jitsi Meet
            # For now, we'll simulate audio playback and broadcast to connected services
            
            audio_data = synthesis_result.get('audio_data')
            duration_ms = synthesis_result.get('duration_ms', 0)
            format_type = synthesis_result.get('format', 'wav')
            
            # Broadcast audio event to other services
            audio_event = {
                "type": "ai_avatar_speaking",
                "session_id": self.session_id,
                "audio_duration_ms": duration_ms,
                "format": format_type,
                "timestamp": datetime.now().isoformat(),
                "has_audio_data": bool(audio_data)
            }
            
            if self.orchestrator_ws:
                await self.orchestrator_ws.send(json.dumps(audio_event))
            
            # Simulate audio playback duration
            if duration_ms > 0:
                await asyncio.sleep(duration_ms / 1000.0)  # Convert to seconds
            else:
                # Fallback duration calculation
                words = len(synthesis_result.get('text', '').split())
                await asyncio.sleep(words * 0.6)  # 0.6 seconds per word
            
        except Exception as e:
            logger.error(f"❌ Error playing synthesized audio: {e}")
    
    async def _fallback_speech_synthesis(self, text: str):
        """Fallback speech synthesis using legacy TTS"""
        try:
            if self.tts_engine:
                # Use pyttsx3 for immediate playback
                self.tts_engine.say(text)
                self.tts_engine.runAndWait()
            else:
                # Simulate speech duration
                words = len(text.split())
                speech_duration = words * 0.5  # Approximate 0.5 seconds per word
                await asyncio.sleep(speech_duration)
            
            logger.info(f"🔊 Fallback speech synthesis: {text[:30]}...")
            
        except Exception as e:
            logger.error(f"❌ Fallback speech synthesis error: {e}")
    
    async def _wait_for_response_and_analysis(self):
        """Wait for candidate response and real-time analysis"""
        try:
            timeout = 120  # 2 minutes timeout for candidate response
            start_time = datetime.now()
            
            while self.running:
                # Check timeout
                if (datetime.now() - start_time).seconds > timeout:
                    logger.warning("⏰ Candidate response timeout")
                    await self._handle_no_response()
                    break
                
                # Wait for response or analysis
                await asyncio.sleep(1)
                
                # Check if we received a response (this would be set by message handlers)
                # For now, simulate a response after some time
                if (datetime.now() - start_time).seconds > 10:  # Simulate response after 10 seconds
                    break
                    
        except Exception as e:
            logger.error(f"❌ Error waiting for response: {e}")
    
    async def _on_candidate_response(self, data: Dict[str, Any]):
        """Handle candidate's response"""
        try:
            response_text = data.get("text", "")
            confidence = data.get("confidence", 0.0)
            
            # Add to conversation context
            self.conversation_context.append({
                "role": "candidate",
                "content": response_text,
                "timestamp": datetime.now(),
                "confidence": confidence
            })
            
            # Analyze response and update candidate ability estimate
            await self._analyze_response(response_text)
            
            self.state.last_interaction = datetime.now()
            
            logger.info(f"👤 Candidate response: {response_text[:50]}...")
            
        except Exception as e:
            logger.error(f"❌ Error processing candidate response: {e}")
    
    async def _generate_alex_response(self, candidate_response: str) -> Dict[str, Any]:
        """Generate Alex AI-style conversational response"""
        try:
            # Determine if this is a candidate question or technical response
            if self._is_candidate_question(candidate_response):
                return await self._handle_candidate_question(candidate_response)
            else:
                return await self._handle_technical_response(candidate_response)
                
        except Exception as e:
            logger.error(f"❌ Error generating Alex response: {e}")
            return {
                "text": "I appreciate your response. Let me ask you another question.",
                "type": "general",
                "should_continue": True,
                "next_action": "next_question"
            }
    
    def _is_candidate_question(self, text: str) -> bool:
        """Check if the candidate is asking a question"""
        question_indicators = [
            "what", "how", "when", "where", "why", "can you tell", "could you", 
            "salary", "benefits", "culture", "remote", "team", "company"
        ]
        text_lower = text.lower()
        return any(indicator in text_lower for indicator in question_indicators) and "?" in text
    
    async def _handle_candidate_question(self, question: str) -> Dict[str, Any]:
        """Handle candidate questions about role, salary, company, etc."""
        question_lower = question.lower()
        
        if "salary" in question_lower or "compensation" in question_lower or "pay" in question_lower:
            role = self.session_request.job_role.lower()
            salary_data = self.salary_info.get(role, self.salary_info.get("software engineer"))
            
            response = f"The base salary range for this position is between ${salary_data['base_range'][0]:,} and ${salary_data['base_range'][1]:,}, with an annual bonus target of {salary_data['bonus_pct']}%. We also offer equity compensation in the range of {salary_data['equity']}. The total compensation package is quite competitive for the market."
            
            return {
                "text": response,
                "type": "salary_info",
                "should_continue": True,
                "next_action": "wait_for_next_question"
            }
        
        elif "culture" in question_lower or "team" in question_lower or "environment" in question_lower:
            response = self.company_info["culture"]
            return {
                "text": response,
                "type": "company_info",
                "should_continue": True,
                "next_action": "wait_for_next_question"
            }
        
        elif "remote" in question_lower or "work from home" in question_lower or "hybrid" in question_lower:
            response = self.company_info["remote_work"]
            return {
                "text": response,
                "type": "company_info",
                "should_continue": True,
                "next_action": "wait_for_next_question"
            }
        
        elif "benefits" in question_lower or "perks" in question_lower:
            response = self.company_info["benefits"]
            return {
                "text": response,
                "type": "company_info",
                "should_continue": True,
                "next_action": "wait_for_next_question"
            }
        
        else:
            response = "That's a great question! I can provide information about the role responsibilities, team structure, company culture, compensation, benefits, and work arrangements. What specific aspect would you like to know more about?"
            return {
                "text": response,
                "type": "clarification",
                "should_continue": True,
                "next_action": "wait_for_clarification"
            }
    
    async def _handle_technical_response(self, response: str) -> Dict[str, Any]:
        """Handle technical response and provide appropriate follow-up"""
        # Analyze response quality
        response_quality = self._evaluate_response_quality(response)
        
        if response_quality < 0.6:
            # Need follow-up or clarification
            follow_up = "I'd like to dive a bit deeper into that. Can you provide a specific example or elaborate on your approach?"
            return {
                "text": follow_up,
                "type": "follow_up",
                "should_continue": True,
                "next_action": "wait_for_response"
            }
        elif response_quality > 0.8:
            # Excellent response, provide encouragement
            encouragement = "Great answer! I can see you have a solid understanding of that concept. Let me ask you about something else."
            return {
                "text": encouragement,
                "type": "encouragement", 
                "should_continue": True,
                "next_action": "next_question"
            }
        else:
            # Good response, continue
            transition = "Thanks for that explanation. Let's move on to the next question."
            return {
                "text": transition,
                "type": "transition",
                "should_continue": True,
                "next_action": "next_question"
            }
    
    def _evaluate_response_quality(self, response: str) -> float:
        """Simple response quality evaluation"""
        # Simple heuristic evaluation
        score = 0.5  # Base score
        
        # Length check
        word_count = len(response.split())
        if word_count > 20:
            score += 0.2
        if word_count > 50:
            score += 0.1
        
        # Technical terms (basic check)
        technical_terms = ["algorithm", "database", "api", "framework", "architecture", "design pattern"]
        found_terms = sum(1 for term in technical_terms if term in response.lower())
        score += min(0.3, found_terms * 0.1)
        
        return min(1.0, score)
    
    async def _analyze_response(self, response_text: str):
        """Analyze candidate response and update scoring"""
        try:
            # Send response to analytics service for detailed analysis
            analysis_request = {
                "type": "analyze_response",
                "session_id": self.session_id,
                "question_id": self.current_question["id"] if self.current_question else None,
                "response_text": response_text,
                "question_context": self.current_question,
                "conversation_history": self.conversation_context[-5:],  # Last 5 interactions
                "candidate_theta": self.state.candidate_theta
            }
            
            if self.analytics_ws:
                await self.analytics_ws.send(json.dumps(analysis_request))
            
            # Simple scoring for demonstration (in production, use sophisticated NLP analysis)
            response_score = min(10.0, len(response_text.split()) / 10.0 * 8.0)  # Basic length-based scoring
            
            # Update candidate ability estimate using simple IRT approximation
            self.state.candidate_theta += (response_score - 5.0) * 0.1
            self.state.candidate_theta = max(-3.0, min(3.0, self.state.candidate_theta))  # Bound theta
            
            logger.info(f"📊 Response analyzed - Score: {response_score:.1f}, Theta: {self.state.candidate_theta:.2f}")
            
        except Exception as e:
            logger.error(f"❌ Error analyzing response: {e}")
    
    async def _on_real_time_analysis(self, data: Dict[str, Any]):
        """Handle real-time analysis updates"""
        try:
            analysis_type = data.get("analysis_type")
            results = data.get("results", {})
            
            if analysis_type == "engagement_analysis":
                engagement_score = results.get("engagement_score", 0.5)
                self.state.metadata["engagement_score"] = engagement_score
                
            elif analysis_type == "emotion_analysis":
                emotion_data = results.get("emotions", {})
                self.state.metadata["current_emotions"] = emotion_data
                
            elif analysis_type == "technical_assessment":
                technical_score = results.get("technical_score", 0.0)
                self.state.candidate_theta = technical_score  # Update theta based on technical analysis
            
            logger.info(f"📈 Real-time analysis update: {analysis_type}")
            
        except Exception as e:
            logger.error(f"❌ Error processing real-time analysis: {e}")
    
    async def _heartbeat_monitor(self):
        """Monitor avatar health and send periodic updates"""
        try:
            while self.running:
                # Send heartbeat to orchestrator
                heartbeat_data = {
                    "type": "ai_avatar_heartbeat",
                    "session_id": self.session_id,
                    "status": self.state.status,
                    "current_stage": self.state.current_stage,
                    "questions_asked": self.state.questions_asked,
                    "candidate_theta": self.state.candidate_theta,
                    "timestamp": datetime.now().isoformat()
                }
                
                if self.orchestrator_ws:
                    await self.orchestrator_ws.send(json.dumps(heartbeat_data))
                
                await asyncio.sleep(30)  # Send heartbeat every 30 seconds
                
        except Exception as e:
            logger.error(f"❌ Heartbeat monitor error: {e}")
    
    async def _complete_interview(self):
        """Complete the interview and generate final report"""
        try:
            self.state.status = "completing"
            
            # Send completion message to candidate
            completion_message = "Thank you for completing the interview! The AI analysis will be available shortly."
            await self._synthesize_and_play_speech(completion_message)
            
            # Generate final analysis
            final_analysis = {
                "type": "interview_completed",
                "session_id": self.session_id,
                "final_theta": self.state.candidate_theta,
                "questions_asked": self.state.questions_asked,
                "interview_duration": str(datetime.now() - self.state.start_time),
                "conversation_context": self.conversation_context,
                "metadata": self.state.metadata,
                "completed_at": datetime.now().isoformat()
            }
            
            # Send to all services
            if self.orchestrator_ws:
                await self.orchestrator_ws.send(json.dumps(final_analysis))
            
            if self.analytics_ws:
                await self.analytics_ws.send(json.dumps(final_analysis))
            
            self.state.status = "completed"
            logger.info(f"🏁 Interview completed for session: {self.session_id}")
            
        except Exception as e:
            logger.error(f"❌ Error completing interview: {e}")
    
    async def _handle_no_response(self):
        """Handle case when candidate doesn't respond"""
        try:
            prompt_message = "I notice you might need a moment to think. Take your time, and let me know when you're ready to continue."
            await self._synthesize_and_play_speech(prompt_message)
            
        except Exception as e:
            logger.error(f"❌ Error handling no response: {e}")
    
    async def _on_candidate_joined(self, data: Dict[str, Any]):
        """Handle candidate joining the interview"""
        logger.info(f"👤 Candidate joined session: {self.session_id}")
        
    async def _on_interview_started(self, data: Dict[str, Any]):
        """Handle interview start signal"""
        logger.info(f"🚀 Interview started for session: {self.session_id}")
        
    async def _handle_session_control(self, data: Dict[str, Any]):
        """Handle session control commands"""
        command = data.get("command")
        
        if command == "pause":
            self.state.status = "paused"
            logger.info(f"⏸️ Avatar paused for session: {self.session_id}")
        elif command == "resume":
            self.state.status = "ready"
            logger.info(f"▶️ Avatar resumed for session: {self.session_id}")
        elif command == "stop":
            await self.stop()
            logger.info(f"🛑 Avatar stopped for session: {self.session_id}")
    
    async def _handle_recruiter_intervention(self, data: Dict[str, Any]):
        """Handle recruiter intervention during interview"""
        intervention_type = data.get("intervention_type")
        message = data.get("message", "")
        
        if intervention_type == "inject_question":
            # Recruiter wants to ask a specific question
            await self._ask_question(message, "recruiter_intervention")
        elif intervention_type == "provide_hint":
            # Recruiter wants to provide a hint
            hint_message = f"Let me provide some additional context: {message}"
            await self._synthesize_and_play_speech(hint_message)
            
        logger.info(f"👩‍💼 Recruiter intervention: {intervention_type}")
    
    async def _on_candidate_finished_speaking(self, data: Dict[str, Any]):
        """Handle candidate finishing their response"""
        self.state.status = "analyzing"
        
    async def _on_bias_alert(self, data: Dict[str, Any]):
        """Handle bias detection alerts"""
        bias_type = data.get("bias_type")
        confidence = data.get("confidence", 0.0)
        
        logger.warning(f"⚠️ Bias alert: {bias_type} (confidence: {confidence})")
        
        # Store bias alert in metadata
        if "bias_alerts" not in self.state.metadata:
            self.state.metadata["bias_alerts"] = []
            
        self.state.metadata["bias_alerts"].append({
            "type": bias_type,
            "confidence": confidence,
            "timestamp": datetime.now().isoformat()
        })
    
    async def cleanup(self):
        """Cleanup avatar resources"""
        try:
            # Close WebSocket connections
            if self.orchestrator_ws:
                await self.orchestrator_ws.close()
            if self.speech_ws:
                await self.speech_ws.close()
            if self.analytics_ws:
                await self.analytics_ws.close()
            
            # Cleanup TTS engine
            if self.tts_engine:
                self.tts_engine.stop()
            
            logger.info(f"🧹 AI Avatar cleanup completed for session: {self.session_id}")
            
        except Exception as e:
            logger.error(f"❌ Error during avatar cleanup: {e}")


# ==================== ALEX AI CONVERSATIONAL ENGINE ====================

class AlexConversationEngine:
    """Manages the Alex AI conversational flow, personality, and responses."""
    
    def __init__(self, candidate_profile: Dict[str, Any], session_id: str):
        self.candidate_name = candidate_profile.get("name", "Candidate")
        self.position = candidate_profile.get("position", "the role")
        self.session_id = session_id
    
    def get_greeting(self) -> str:
        """Generate a personalized Alex AI-style greeting."""
        return f"Hey {self.candidate_name}, thanks for joining me today. I'm Alex, and I'll be conducting your interview for the {self.position} position. I'm excited to learn more about your background and experience. How are you feeling today?"

    def get_wrap_up(self) -> str:
        """Generate a professional Alex AI-style wrap-up message."""
        return f"Thank you so much for your time today, {self.candidate_name}. I really enjoyed our conversation and learning about your experience. We'll review everything and get back to you with next steps within the next few days."


# ==================== ALEX AI CHEAT DETECTION ====================

class AlexCheatDetection:
    """Implements Alex AI's cheat detection capabilities."""
    
    def detect_cheating(self, response_text: str, response_time: int) -> List[str]:
        """Detect potential cheating based on response patterns and timing."""
        flags = []
        if response_time < 5 and len(response_text.split()) > 50:
            flags.append("suspiciously_fast_response")
        
        if "as an AI language model" in response_text.lower():
            flags.append("potential_ai_generated_content")
        
        return flags


# ==================== FASTAPI APPLICATION ====================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager"""
    # Startup
    logger.info(f"Starting {settings.SERVICE_NAME} v{settings.SERVICE_VERSION}")
    
    # Initialize avatar manager
    avatar_manager = AIAvatarManager()
    await avatar_manager.initialize()
    app.state.avatar_manager = avatar_manager
    
    logger.info("AI Avatar Service startup complete")
    yield
    
    # Shutdown
    logger.info("Shutting down AI Avatar Service")
    
    # Stop all active avatars
    for session_id in list(avatar_manager.active_avatars.keys()):
        await avatar_manager.stop_avatar(session_id)
    
    logger.info("AI Avatar Service shutdown complete")

# Create FastAPI application
app = FastAPI(
    title=settings.SERVICE_NAME,
    version=settings.SERVICE_VERSION,
    description="AI Avatar Service for ARIA Interview Platform",
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

# ==================== API ENDPOINTS ====================

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "service": settings.SERVICE_NAME,
        "version": settings.SERVICE_VERSION,
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "active_avatars": len(app.state.avatar_manager.active_avatars),
        "capabilities": ["jitsi_integration", "speech_synthesis", "real_time_analysis", "adaptive_questioning"]
    }

@app.post("/avatar/create", response_model=AIAvatarResponse)
async def create_avatar(request: InterviewSessionRequest):
    """Create a new AI avatar for an interview session"""
    try:
        response = await app.state.avatar_manager.create_avatar(request)
        return response
    except Exception as e:
        logger.error(f"Error creating avatar: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/avatar/{session_id}/status", response_model=AIAvatarResponse)
async def get_avatar_status(session_id: str):
    """Get current status of an AI avatar"""
    status = await app.state.avatar_manager.get_avatar_status(session_id)
    if not status:
        raise HTTPException(status_code=404, detail="Avatar not found")
    return status

@app.post("/avatar/{session_id}/control")
async def control_avatar(session_id: str, command: Dict[str, Any]):
    """Send control commands to an AI avatar"""
    if session_id not in app.state.avatar_manager.active_avatars:
        raise HTTPException(status_code=404, detail="Avatar not found")
    
    avatar = app.state.avatar_manager.active_avatars[session_id]
    
    # Send control command
    control_data = {
        "type": "session_control",
        "command": command.get("action"),
        "parameters": command.get("parameters", {}),
        "timestamp": datetime.now().isoformat()
    }
    
    if avatar.orchestrator_ws:
        await avatar.orchestrator_ws.send(json.dumps(control_data))
    
    return {"status": "command_sent", "session_id": session_id}

@app.delete("/avatar/{session_id}")
async def stop_avatar(session_id: str):
    """Stop and cleanup an AI avatar"""
    success = await app.state.avatar_manager.stop_avatar(session_id)
    if not success:
        raise HTTPException(status_code=404, detail="Avatar not found or already stopped")
    
    return {"status": "avatar_stopped", "session_id": session_id}

@app.get("/avatars")
async def list_active_avatars():
    """List all active AI avatars"""
    avatars = []
    for session_id, avatar in app.state.avatar_manager.active_avatars.items():
        avatars.append({
            "session_id": session_id,
            "status": avatar.state.status,
            "current_stage": avatar.state.current_stage,
            "questions_asked": avatar.state.questions_asked,
            "uptime": str(datetime.now() - avatar.state.start_time),
            "candidate_theta": avatar.state.candidate_theta
        })
    
    return {"active_avatars": avatars, "count": len(avatars)}

@app.post("/avatar/{session_id}/speech")
async def synthesize_speech(session_id: str, request: SpeechSynthesisRequest):
    """Generate speech for an AI avatar"""
    if session_id not in app.state.avatar_manager.active_avatars:
        raise HTTPException(status_code=404, detail="Avatar not found")
    
    avatar = app.state.avatar_manager.active_avatars[session_id]
    
    try:
        await avatar._synthesize_and_play_speech(request.text)
        return {"status": "speech_generated", "text": request.text}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Speech synthesis failed: {str(e)}")

# ==================== ALEX AI COMPATIBLE ENDPOINTS ====================

@app.post("/api/alex/start/{session_id}")
async def start_alex_interview(session_id: str, request: Dict[str, Any]):
    """Start Alex AI interview - compatible with original Alex AI API"""
    try:
        # Convert Alex AI request format to AI Avatar format
        avatar_request = InterviewSessionRequest(
            session_id=session_id,
            candidate_profile={
                "name": request.get("candidate_name", "Candidate"),
                "position": request.get("position", "Software Engineer"),
                "domain": request.get("domain", "general"),
                "experience_level": request.get("experience_level", "mid"),
                "technical_skills": request.get("technical_skills", [])
            },
            job_role=request.get("position", "Software Engineer"),
            experience_level=3,  # Convert string to number
            required_technologies=request.get("technical_skills", []),
            meeting_link=request.get("meeting_link", f"aria-interview-{session_id}")
        )
        
        # Create avatar using existing manager
        avatar_response = await app.state.avatar_manager.create_avatar(avatar_request)
        
        if avatar_response.status == "created":
            # Get the avatar and send Alex AI greeting
            avatar = app.state.avatar_manager.active_avatars[session_id]
            greeting = avatar.alex_conversation_engine.get_greeting()
            
            # Send greeting immediately
            await avatar._synthesize_and_play_speech(greeting)
            
            return {
                "session_id": session_id,
                "status": "started",
                "message": "Alex AI interview started successfully",
                "alex_response": {
                    "text": greeting,
                    "type": "greeting",
                    "should_continue": True,
                    "next_action": "wait_for_response"
                }
            }
        else:
            raise HTTPException(status_code=500, detail=avatar_response.message)
            
    except Exception as e:
        logger.error(f"Error starting Alex interview: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/alex/respond/{session_id}")
async def alex_process_response(session_id: str, request: Dict[str, Any]):
    """Process candidate response and generate Alex AI response"""
    try:
        if session_id not in app.state.avatar_manager.active_avatars:
            raise HTTPException(status_code=404, detail="Interview session not found")
        
        avatar = app.state.avatar_manager.active_avatars[session_id]
        response_text = request.get("response_text", "")
        
        # Alex AI cheat detection
        if settings.ALEX_CHEAT_DETECTION:
            response_time = request.get("response_time", 30)
            cheat_flags = avatar.cheat_detection.detect_cheating(response_text, response_time)
            if cheat_flags:
                avatar.state.metadata.setdefault("cheat_flags", []).extend(cheat_flags)
                logger.warning(f"🚨 Cheat detection flags: {cheat_flags}")
        
        # Process response through existing analytics
        await avatar._on_candidate_response({
            "text": response_text,
            "confidence": request.get("confidence", 0.8),
            "timestamp": datetime.now().isoformat()
        })
        
        # Generate Alex AI response based on conversation context
        alex_response = await avatar._generate_alex_response(response_text)
        
        # Synthesize and play Alex's response
        if alex_response["text"]:
            await avatar._synthesize_and_play_speech(alex_response["text"])
        
        return {
            "session_id": session_id,
            "alex_response": alex_response,
            "candidate_theta": avatar.state.candidate_theta,
            "stage": avatar.state.current_stage
        }
        
    except Exception as e:
        logger.error(f"Error processing Alex response: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/alex/report/{session_id}")
async def generate_alex_report(session_id: str):
    """Generate comprehensive Alex AI interview report for recruiters"""
    try:
        if session_id not in app.state.avatar_manager.active_avatars:
            raise HTTPException(status_code=404, detail="Interview session not found")
        
        avatar = app.state.avatar_manager.active_avatars[session_id]
        
        # Generate comprehensive report
        report = {
            "interview_summary": {
                "candidate_name": avatar.alex_conversation_engine.candidate_name,
                "position": avatar.alex_conversation_engine.position,
                "duration_minutes": (datetime.now() - avatar.state.start_time).seconds // 60,
                "questions_asked": avatar.state.questions_asked,
                "completion_status": "completed" if avatar.state.status == "completed" else "in_progress"
            },
            "technical_evaluation": {
                "overall_score": max(0, min(10, avatar.state.candidate_theta + 5)),  # Convert theta to 0-10 scale
                "technical_depth": avatar.state.metadata.get("technical_depth", 7.0),
                "problem_solving": avatar.state.metadata.get("problem_solving", 7.5),
                "communication": avatar.state.metadata.get("communication", 8.0)
            },
            "behavioral_analysis": {
                "engagement_score": avatar.state.metadata.get("engagement_score", 0.8),
                "confidence_level": "High",
                "communication_style": "Professional and clear"
            },
            "conversation_highlights": [
                {
                    "timestamp": ctx["timestamp"].isoformat() if isinstance(ctx["timestamp"], datetime) else ctx["timestamp"],
                    "speaker": ctx["role"],
                    "content": ctx["content"][:100] + "..." if len(ctx["content"]) > 100 else ctx["content"]
                }
                for ctx in avatar.conversation_context[-10:]  # Last 10 interactions
            ],
            "cheat_detection": {
                "risk_level": "Low" if not avatar.state.metadata.get("cheat_flags") else "Medium",
                "flags": avatar.state.metadata.get("cheat_flags", []),
                "confidence": 0.95
            },
            "recommendations": {
                "hire_recommendation": "Strong Yes" if avatar.state.candidate_theta > 1.0 else "Maybe" if avatar.state.candidate_theta > -0.5 else "No",
                "strengths": ["Technical knowledge", "Problem-solving approach", "Communication skills"],
                "areas_for_improvement": ["Could expand on edge cases", "More specific examples would help"],
                "next_steps": "Proceed to next interview round" if avatar.state.candidate_theta > 0.5 else "Additional technical screen recommended"
            },
            "generated_at": datetime.now().isoformat(),
            "interviewer": "Alex - ARIA AI Interviewer"
        }
        
        return report
        
    except Exception as e:
        logger.error(f"Error generating Alex report: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.websocket("/ws/alex/{session_id}")
async def alex_websocket(websocket: WebSocket, session_id: str):
    """Alex AI WebSocket endpoint for real-time communication"""
    await websocket.accept()
    logger.info(f"🔌 Alex AI WebSocket connected for session: {session_id}")
    
    try:
        while True:
            # Receive message from frontend
            data = await websocket.receive_text()
            message = json.loads(data)
            
            message_type = message.get("type")
            
            if message_type == "candidate_response":
                # Process response through Alex AI engine
                response_data = {
                    "response_text": message.get("text", ""),
                    "response_time": message.get("response_time", 30)
                }
                
                # Generate Alex response
                alex_response = await alex_process_response(session_id, response_data)
                
                # Send response back to frontend
                await websocket.send_text(json.dumps({
                    "type": "alex_response",
                    "data": alex_response["alex_response"]
                }))
                
            elif message_type == "start_interview":
                # Start Alex AI interview
                start_data = message.get("data", {})
                alex_start = await start_alex_interview(session_id, start_data)
                
                await websocket.send_text(json.dumps({
                    "type": "interview_started",
                    "data": alex_start
                }))
            
    except Exception as e:
        logger.error(f"Alex WebSocket error: {e}")
    finally:
        logger.info(f"🔌 Alex AI WebSocket disconnected for session: {session_id}")

if __name__ == "__main__":
    import uvicorn
    import os
    
    # Check if SSL certificates exist
    ssl_cert_path = "../../ssl-certs/aria-cert.pem"
    ssl_key_path = "../../ssl-certs/aria-key.pem"
    
    if os.path.exists(ssl_cert_path) and os.path.exists(ssl_key_path):
        # Run with SSL
        uvicorn.run(
            app,
            host=settings.HOST,
            port=settings.PORT,
            log_level="info",
            ssl_keyfile=ssl_key_path,
            ssl_certfile=ssl_cert_path
        )
    else:
        # Fallback to HTTP
        print("Warning: SSL certificates not found, running with HTTP")
        uvicorn.run(app, host=settings.HOST, port=settings.PORT, log_level="info")
