#!/usr/bin/env python3
"""
Adaptive Question Engine for ARIA Interview Platform
Implements Item Response Theory (IRT) for adaptive question selection
with continuous learning and bias detection capabilities.
"""

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Dict, Optional, Any
import numpy as np
from datetime import datetime
import logging
import asyncio
import json
import redis
from contextlib import asynccontextmanager

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Import our custom modules
from irt_engine import IRTEngine
from continuous_learning import ContinuousLearningModule
from bias_detector import BiasDetector
from question_selector import QuestionSelector

# Redis connection for caching and session management
redis_client = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize services on startup"""
    logger.info("Starting Adaptive Question Engine...")
    
    # Initialize core components
    app.state.irt_engine = IRTEngine()
    app.state.learning_module = ContinuousLearningModule()
    app.state.bias_detector = BiasDetector()
    app.state.question_selector = QuestionSelector()
    
    # Start background learning task
    learning_task = asyncio.create_task(
        app.state.learning_module.start_continuous_learning()
    )
    
    yield
    
    # Cleanup on shutdown
    logger.info("Shutting down Adaptive Question Engine...")
    learning_task.cancel()

app = FastAPI(
    title="ARIA Adaptive Question Engine",
    description="IRT-based adaptive question selection with AI-powered bias detection",
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

# Pydantic models for API requests/responses
class QuestionRequest(BaseModel):
    session_id: str
    candidate_id: int
    current_theta: float = 0.0
    standard_error: float = 1.0
    answered_questions: List[int] = []
    job_role: str
    experience_level: str
    technologies: List[str]
    min_difficulty: float = -3.0
    max_difficulty: float = 3.0
    question_type: Optional[str] = None

class QuestionResponse(BaseModel):
    question_id: int
    question_text: str
    question_type: str
    difficulty: float
    discrimination: float
    category: str
    technologies: List[str]
    expected_duration_minutes: int
    coding_required: bool
    multi_part: bool
    followup_questions: List[int] = []
    confidence_score: float
    selection_reason: str

class ThetaUpdateRequest(BaseModel):
    session_id: str
    candidate_id: int
    question_id: int
    response_data: Dict[str, Any]
    current_theta: float
    current_se: float
    partial_credit: Optional[float] = None
    response_time_seconds: Optional[int] = None

class ThetaUpdateResponse(BaseModel):
    new_theta: float
    new_standard_error: float
    theta_change: float
    confidence_level: float
    termination_recommended: bool
    next_difficulty_range: Dict[str, float]
    bias_flag: bool = False
    bias_details: Optional[Dict[str, Any]] = None

class LearningUpdateRequest(BaseModel):
    session_id: str
    interview_outcome: Dict[str, Any]
    question_effectiveness: List[Dict[str, Any]]
    bias_incidents: List[Dict[str, Any]] = []
    conversation_patterns: List[Dict[str, Any]] = []

@app.get("/")
async def root():
    """Health check endpoint"""
    return {
        "service": "ARIA Adaptive Question Engine",
        "status": "healthy",
        "version": "1.0.0",
        "timestamp": datetime.now().isoformat()
    }

@app.post("/next-question", response_model=QuestionResponse)
async def get_next_question(request: QuestionRequest):
    """
    Get the next optimal question based on current theta estimate and constraints
    """
    try:
        logger.info(f"Selecting next question for session {request.session_id}, "
                   f"theta={request.current_theta}, se={request.standard_error}")
        
        # Use question selector to find optimal question
        question = await app.state.question_selector.select_next_question(
            session_id=request.session_id,
            current_theta=request.current_theta,
            standard_error=request.standard_error,
            answered_questions=request.answered_questions,
            job_role=request.job_role,
            experience_level=request.experience_level,
            technologies=request.technologies,
            difficulty_range=(request.min_difficulty, request.max_difficulty),
            question_type=request.question_type
        )
        
        if not question:
            raise HTTPException(status_code=404, detail="No suitable question found")
        
        # Check for potential bias in question selection
        bias_check = await app.state.bias_detector.check_question_bias(
            question_id=question["question_id"],
            candidate_profile={
                "job_role": request.job_role,
                "experience_level": request.experience_level,
                "technologies": request.technologies
            }
        )
        
        response = QuestionResponse(
            question_id=question["question_id"],
            question_text=question["question_text"],
            question_type=question["question_type"],
            difficulty=question["difficulty"],
            discrimination=question["discrimination"],
            category=question["category"],
            technologies=question["technologies"],
            expected_duration_minutes=question["expected_duration_minutes"],
            coding_required=question.get("coding_required", False),
            multi_part=question.get("multi_part", False),
            followup_questions=question.get("followup_questions", []),
            confidence_score=question["confidence_score"],
            selection_reason=question["selection_reason"]
        )
        
        # Cache question selection for session
        cache_key = f"session:{request.session_id}:current_question"
        redis_client.setex(
            cache_key, 
            3600,  # 1 hour TTL
            json.dumps(response.dict())
        )
        
        logger.info(f"Selected question {question['question_id']} for session {request.session_id}")
        return response
        
    except Exception as e:
        logger.error(f"Error selecting question for session {request.session_id}: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Question selection failed: {str(e)}")

@app.post("/update-theta", response_model=ThetaUpdateResponse)
async def update_theta(request: ThetaUpdateRequest):
    """
    Update candidate's theta estimate based on response
    """
    try:
        logger.info(f"Updating theta for session {request.session_id}, "
                   f"question {request.question_id}")
        
        # Get question details for IRT calculation
        question_info = await app.state.question_selector.get_question_info(request.question_id)
        if not question_info:
            raise HTTPException(status_code=404, detail="Question not found")
        
        # Calculate new theta using IRT
        theta_result = app.state.irt_engine.update_theta(
            current_theta=request.current_theta,
            current_se=request.current_se,
            question_difficulty=question_info["difficulty"],
            question_discrimination=question_info["discrimination"],
            response_correct=request.response_data.get("correct", False),
            partial_credit=request.partial_credit,
            response_quality_score=request.response_data.get("quality_score")
        )
        
        # Check for bias in response evaluation
        bias_result = await app.state.bias_detector.analyze_response_bias(
            candidate_id=request.candidate_id,
            question_id=request.question_id,
            response_data=request.response_data,
            theta_change=theta_result["theta_change"]
        )
        
        # Determine if interview can terminate early
        termination_recommended = (
            theta_result["standard_error"] <= 0.3 and  # High confidence
            abs(theta_result["theta_change"]) < 0.1    # Theta stabilized
        )
        
        # Calculate next difficulty range
        next_difficulty_range = {
            "min": max(theta_result["new_theta"] - 1.5, -3.0),
            "max": min(theta_result["new_theta"] + 1.5, 3.0),
            "optimal": theta_result["new_theta"]
        }
        
        response = ThetaUpdateResponse(
            new_theta=theta_result["new_theta"],
            new_standard_error=theta_result["standard_error"],
            theta_change=theta_result["theta_change"],
            confidence_level=1.0 - theta_result["standard_error"],
            termination_recommended=termination_recommended,
            next_difficulty_range=next_difficulty_range,
            bias_flag=bias_result["bias_detected"],
            bias_details=bias_result.get("details")
        )
        
        # Cache theta update for session
        cache_key = f"session:{request.session_id}:theta_history"
        theta_history = json.loads(redis_client.get(cache_key) or "[]")
        theta_history.append({
            "timestamp": datetime.now().isoformat(),
            "question_id": request.question_id,
            "theta": theta_result["new_theta"],
            "standard_error": theta_result["standard_error"],
            "theta_change": theta_result["theta_change"]
        })
        redis_client.setex(cache_key, 3600, json.dumps(theta_history))
        
        logger.info(f"Updated theta for session {request.session_id}: "
                   f"{request.current_theta} -> {theta_result['new_theta']}")
        
        return response
        
    except Exception as e:
        logger.error(f"Error updating theta for session {request.session_id}: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Theta update failed: {str(e)}")

@app.post("/learning/update")
async def update_learning(request: LearningUpdateRequest, background_tasks: BackgroundTasks):
    """
    Update the continuous learning model with interview outcomes
    """
    try:
        logger.info(f"Updating learning model with data from session {request.session_id}")
        
        # Add background task for learning update
        background_tasks.add_task(
            app.state.learning_module.process_interview_outcome,
            session_id=request.session_id,
            outcome_data=request.interview_outcome,
            question_effectiveness=request.question_effectiveness,
            bias_incidents=request.bias_incidents,
            conversation_patterns=request.conversation_patterns
        )
        
        return {
            "status": "success",
            "message": "Learning update queued successfully",
            "session_id": request.session_id
        }
        
    except Exception as e:
        logger.error(f"Error queuing learning update for session {request.session_id}: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Learning update failed: {str(e)}")

@app.get("/analytics/session/{session_id}")
async def get_session_analytics(session_id: str):
    """
    Get analytics and insights for a specific session
    """
    try:
        # Retrieve session data from cache
        theta_history_key = f"session:{session_id}:theta_history"
        theta_history = json.loads(redis_client.get(theta_history_key) or "[]")
        
        current_question_key = f"session:{session_id}:current_question"
        current_question = json.loads(redis_client.get(current_question_key) or "{}")
        
        analytics = {
            "session_id": session_id,
            "theta_progression": theta_history,
            "current_question": current_question,
            "total_questions": len(theta_history),
            "theta_stability": calculate_theta_stability(theta_history),
            "average_difficulty": calculate_average_difficulty(theta_history),
            "learning_trajectory": analyze_learning_trajectory(theta_history)
        }
        
        return analytics
        
    except Exception as e:
        logger.error(f"Error retrieving analytics for session {session_id}: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Analytics retrieval failed: {str(e)}")

@app.get("/health")
async def health_check():
    """Detailed health check"""
    try:
        # Check Redis connection
        redis_status = "healthy" if redis_client.ping() else "unhealthy"
        
        # Check component status
        components = {
            "irt_engine": "healthy" if hasattr(app.state, 'irt_engine') else "not_initialized",
            "learning_module": "healthy" if hasattr(app.state, 'learning_module') else "not_initialized",
            "bias_detector": "healthy" if hasattr(app.state, 'bias_detector') else "not_initialized",
            "question_selector": "healthy" if hasattr(app.state, 'question_selector') else "not_initialized",
            "redis": redis_status
        }
        
        overall_status = "healthy" if all(status == "healthy" for status in components.values()) else "degraded"
        
        return {
            "status": overall_status,
            "components": components,
            "timestamp": datetime.now().isoformat()
        }
        
    except Exception as e:
        logger.error(f"Health check failed: {str(e)}")
        return {
            "status": "unhealthy",
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }

# Utility functions
def calculate_theta_stability(theta_history: List[Dict]) -> float:
    """Calculate theta stability score based on recent changes"""
    if len(theta_history) < 3:
        return 0.0
    
    recent_changes = [abs(entry["theta_change"]) for entry in theta_history[-3:]]
    return 1.0 - (sum(recent_changes) / len(recent_changes))

def calculate_average_difficulty(theta_history: List[Dict]) -> float:
    """Calculate average difficulty of questions asked"""
    if not theta_history:
        return 0.0
    
    difficulties = [entry["theta"] for entry in theta_history]
    return sum(difficulties) / len(difficulties)

def analyze_learning_trajectory(theta_history: List[Dict]) -> Dict[str, Any]:
    """Analyze candidate's learning trajectory"""
    if len(theta_history) < 2:
        return {"trend": "insufficient_data"}
    
    thetas = [entry["theta"] for entry in theta_history]
    trend = "improving" if thetas[-1] > thetas[0] else "declining" if thetas[-1] < thetas[0] else "stable"
    
    return {
        "trend": trend,
        "total_change": thetas[-1] - thetas[0],
        "volatility": np.std(thetas) if len(thetas) > 1 else 0.0,
        "peak_theta": max(thetas),
        "current_theta": thetas[-1] if thetas else 0.0
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001, log_level="info")
