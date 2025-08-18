"""
Pydantic models for ARIA AI Analytics Service

This module defines request/response models for all analytics endpoints including
video analysis, text analysis, bias detection, and performance predictions.
"""

from datetime import datetime
from typing import Dict, List, Optional, Any, Union
from pydantic import BaseModel, Field, validator
from enum import Enum


class ContentType(str, Enum):
    """Content types for analysis"""
    QUESTION = "question"
    RESPONSE = "response"
    CONVERSATION = "conversation"
    TRANSCRIPT = "transcript"


class AnalysisConfig(BaseModel):
    """Configuration for analysis parameters"""
    enable_emotion_detection: bool = True
    enable_engagement_tracking: bool = True
    enable_bias_detection: bool = True
    enable_micro_expressions: bool = True
    frame_sampling_rate: int = 10  # Process every Nth frame
    audio_analysis: bool = True
    confidence_threshold: float = 0.7


class EmotionScores(BaseModel):
    """Emotion detection scores"""
    happiness: float = Field(..., ge=0.0, le=1.0)
    sadness: float = Field(..., ge=0.0, le=1.0)
    anger: float = Field(..., ge=0.0, le=1.0)
    fear: float = Field(..., ge=0.0, le=1.0)
    surprise: float = Field(..., ge=0.0, le=1.0)
    disgust: float = Field(..., ge=0.0, le=1.0)
    neutral: float = Field(..., ge=0.0, le=1.0)


class FacialExpressions(BaseModel):
    """Facial expression analysis"""
    smile_intensity: float = Field(..., ge=0.0, le=1.0)
    eyebrow_movement: float = Field(..., ge=0.0, le=1.0)
    eye_openness: float = Field(..., ge=0.0, le=1.0)
    mouth_openness: float = Field(..., ge=0.0, le=1.0)
    head_pose: Dict[str, float] = Field(default_factory=dict)  # pitch, yaw, roll
    gaze_direction: Dict[str, float] = Field(default_factory=dict)


class PostureAnalysis(BaseModel):
    """Body posture and movement analysis"""
    posture_score: float = Field(..., ge=0.0, le=1.0)
    movement_intensity: float = Field(..., ge=0.0, le=1.0)
    gesture_frequency: float = Field(..., ge=0.0, le=10.0)
    body_orientation: str  # facing_camera, turned_left, turned_right, etc.
    fidgeting_indicators: List[str] = Field(default_factory=list)


class MicroExpressions(BaseModel):
    """Micro-expression detection results"""
    detected_expressions: List[Dict[str, Any]] = Field(default_factory=list)
    authenticity_score: float = Field(..., ge=0.0, le=1.0)
    deception_indicators: List[str] = Field(default_factory=list)
    emotional_leakage: List[str] = Field(default_factory=list)


class StressIndicators(BaseModel):
    """Stress and anxiety indicators"""
    overall_stress_level: float = Field(..., ge=0.0, le=1.0)
    physical_indicators: List[str] = Field(default_factory=list)
    vocal_stress_markers: List[str] = Field(default_factory=list)
    behavioral_patterns: List[str] = Field(default_factory=list)


# Request Models
class VideoAnalysisRequest(BaseModel):
    """Request for video analysis"""
    session_id: str = Field(..., min_length=1, max_length=100)
    candidate_id: int = Field(..., gt=0)
    video_data: str = Field(..., description="Base64 encoded video data or video URL")
    timestamp: Optional[datetime] = None
    config: AnalysisConfig = Field(default_factory=AnalysisConfig)
    
    class Config:
        schema_extra = {
            "example": {
                "session_id": "session-12345",
                "candidate_id": 123,
                "video_data": "base64_encoded_video_or_url",
                "timestamp": "2024-01-15T10:30:00Z",
                "config": {
                    "enable_emotion_detection": True,
                    "enable_engagement_tracking": True,
                    "frame_sampling_rate": 10
                }
            }
        }


class TextAnalysisRequest(BaseModel):
    """Request for text analysis"""
    session_id: str = Field(..., min_length=1, max_length=100)
    candidate_id: int = Field(..., gt=0)
    text: str = Field(..., min_length=1, max_length=50000)
    content_type: ContentType = ContentType.RESPONSE
    context: Optional[Dict[str, Any]] = Field(default_factory=dict)
    language: str = "en"
    
    class Config:
        schema_extra = {
            "example": {
                "session_id": "session-12345",
                "candidate_id": 123,
                "text": "I believe the best approach would be to use a microservices architecture...",
                "content_type": "response",
                "context": {"question_id": 456, "topic": "system_design"},
                "language": "en"
            }
        }


class BiasDetectionRequest(BaseModel):
    """Request for bias detection"""
    session_id: str = Field(..., min_length=1, max_length=100)
    content: str = Field(..., min_length=1, max_length=50000)
    content_type: ContentType = ContentType.QUESTION
    demographic_info: Optional[Dict[str, Any]] = Field(default_factory=dict)
    context: Optional[Dict[str, Any]] = Field(default_factory=dict)
    
    class Config:
        schema_extra = {
            "example": {
                "session_id": "session-12345",
                "content": "Tell me about your family background and how it influences your work style.",
                "content_type": "question",
                "demographic_info": {"anonymized": True},
                "context": {"job_role": "software_engineer"}
            }
        }


class EngagementRequest(BaseModel):
    """Request for engagement analysis"""
    session_id: str = Field(..., min_length=1, max_length=100)
    video_data: Optional[str] = None
    audio_data: Optional[str] = None
    timestamp: datetime
    duration: Optional[int] = Field(default=30, description="Analysis duration in seconds")
    
    class Config:
        schema_extra = {
            "example": {
                "session_id": "session-12345",
                "video_data": "base64_encoded_video",
                "audio_data": "base64_encoded_audio",
                "timestamp": "2024-01-15T10:30:00Z",
                "duration": 30
            }
        }


class PredictionRequest(BaseModel):
    """Request for performance prediction"""
    session_id: str = Field(..., min_length=1, max_length=100)
    video_analysis: Optional[Dict[str, Any]] = Field(default_factory=dict)
    text_analysis: Optional[Dict[str, Any]] = Field(default_factory=dict)
    engagement_data: Optional[Dict[str, Any]] = Field(default_factory=dict)
    response_patterns: Optional[Dict[str, Any]] = Field(default_factory=dict)
    job_requirements: Optional[Dict[str, Any]] = Field(default_factory=dict)


# Response Models
class VideoAnalysisResponse(BaseModel):
    """Response for video analysis"""
    session_id: str
    analysis_id: str
    emotions: EmotionScores
    engagement_score: float = Field(..., ge=0.0, le=1.0)
    attention_score: float = Field(..., ge=0.0, le=1.0)
    stress_indicators: StressIndicators
    confidence_level: float = Field(..., ge=0.0, le=1.0)
    facial_expressions: FacialExpressions
    eye_contact_score: float = Field(..., ge=0.0, le=1.0)
    posture_analysis: PostureAnalysis
    micro_expressions: MicroExpressions
    processing_time: float = Field(..., gt=0, description="Processing time in seconds")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Model confidence")
    timestamp: datetime = Field(default_factory=datetime.utcnow)


class TextAnalysisResponse(BaseModel):
    """Response for text analysis"""
    session_id: str
    analysis_id: str
    sentiment_score: float = Field(..., ge=-1.0, le=1.0)
    sentiment_label: str  # positive, negative, neutral
    emotion_scores: EmotionScores
    toxicity_score: float = Field(..., ge=0.0, le=1.0)
    professionalism_score: float = Field(..., ge=0.0, le=1.0)
    coherence_score: float = Field(..., ge=0.0, le=1.0)
    technical_accuracy: float = Field(..., ge=0.0, le=1.0)
    key_topics: List[str] = Field(default_factory=list)
    named_entities: List[Dict[str, str]] = Field(default_factory=list)
    language_complexity: float = Field(..., ge=0.0, le=1.0)
    bias_indicators: List[str] = Field(default_factory=list)
    processing_time: float = Field(..., gt=0)
    timestamp: datetime = Field(default_factory=datetime.utcnow)


class BiasCategory(BaseModel):
    """Bias category with details"""
    category: str
    score: float = Field(..., ge=0.0, le=1.0)
    description: str
    examples: List[str] = Field(default_factory=list)
    severity: str  # low, medium, high, critical


class FairnessMetrics(BaseModel):
    """Fairness metrics for bias detection"""
    demographic_parity: float = Field(..., ge=0.0, le=1.0)
    equalized_odds: float = Field(..., ge=0.0, le=1.0)
    calibration_score: float = Field(..., ge=0.0, le=1.0)
    individual_fairness: float = Field(..., ge=0.0, le=1.0)


class BiasDetectionResponse(BaseModel):
    """Response for bias detection"""
    session_id: str
    analysis_id: str
    overall_bias_score: float = Field(..., ge=0.0, le=1.0)
    bias_categories: List[BiasCategory] = Field(default_factory=list)
    demographic_bias: float = Field(..., ge=0.0, le=1.0)
    linguistic_bias: float = Field(..., ge=0.0, le=1.0)
    cultural_bias: float = Field(..., ge=0.0, le=1.0)
    fairness_metrics: FairnessMetrics
    recommendations: List[str] = Field(default_factory=list)
    confidence_level: float = Field(..., ge=0.0, le=1.0)
    processing_time: float = Field(..., gt=0)
    timestamp: datetime = Field(default_factory=datetime.utcnow)


class EngagementResponse(BaseModel):
    """Response for engagement analysis"""
    session_id: str
    timestamp: datetime
    engagement_score: float = Field(..., ge=0.0, le=1.0)
    attention_level: float = Field(..., ge=0.0, le=1.0)
    focus_duration: float = Field(..., ge=0.0, description="Focus duration in seconds")
    distraction_indicators: List[str] = Field(default_factory=list)
    eye_contact_percentage: float = Field(..., ge=0.0, le=100.0)
    facial_engagement: float = Field(..., ge=0.0, le=1.0)
    vocal_engagement: float = Field(..., ge=0.0, le=1.0)
    overall_presence: float = Field(..., ge=0.0, le=1.0)


class PerformanceMetrics(BaseModel):
    """Performance prediction metrics"""
    overall_score: float = Field(..., ge=0.0, le=1.0)
    technical_competency: float = Field(..., ge=0.0, le=1.0)
    communication_skills: float = Field(..., ge=0.0, le=1.0)
    cultural_fit: float = Field(..., ge=0.0, le=1.0)
    leadership_potential: float = Field(..., ge=0.0, le=1.0)
    growth_mindset: float = Field(..., ge=0.0, le=1.0)


class PredictionResponse(BaseModel):
    """Response for performance prediction"""
    session_id: str
    overall_score: float = Field(..., ge=0.0, le=1.0)
    technical_competency: float = Field(..., ge=0.0, le=1.0)
    communication_skills: float = Field(..., ge=0.0, le=1.0)
    cultural_fit: float = Field(..., ge=0.0, le=1.0)
    leadership_potential: float = Field(..., ge=0.0, le=1.0)
    growth_mindset: float = Field(..., ge=0.0, le=1.0)
    risk_factors: List[str] = Field(default_factory=list)
    strengths: List[str] = Field(default_factory=list)
    improvement_areas: List[str] = Field(default_factory=list)
    confidence_interval: Dict[str, float] = Field(default_factory=dict)  # lower, upper bounds
    model_version: str
    timestamp: datetime = Field(default_factory=datetime.utcnow)


class HealthResponse(BaseModel):
    """Health check response"""
    status: str  # healthy, unhealthy, degraded
    version: str
    analyzers: Optional[Dict[str, bool]] = Field(default_factory=dict)
    database_connected: bool = False
    error: Optional[str] = None
    timestamp: float
    uptime: Optional[float] = None


class AnalyticsSessionData(BaseModel):
    """Comprehensive session analytics"""
    session_id: str
    candidate_id: int
    start_time: datetime
    end_time: Optional[datetime] = None
    duration_minutes: Optional[float] = None
    
    # Video Analytics Summary
    avg_engagement_score: float = Field(..., ge=0.0, le=1.0)
    avg_attention_score: float = Field(..., ge=0.0, le=1.0)
    dominant_emotion: str
    stress_level: str  # low, medium, high
    confidence_trend: str  # increasing, decreasing, stable
    eye_contact_quality: str  # excellent, good, fair, poor
    
    # Text Analytics Summary
    avg_sentiment: float = Field(..., ge=-1.0, le=1.0)
    communication_quality: str  # excellent, good, fair, poor
    technical_accuracy_avg: float = Field(..., ge=0.0, le=1.0)
    professionalism_avg: float = Field(..., ge=0.0, le=1.0)
    
    # Bias Detection Summary
    bias_risk_level: str  # low, medium, high, critical
    detected_bias_types: List[str] = Field(default_factory=list)
    fairness_score: float = Field(..., ge=0.0, le=1.0)
    
    # Performance Prediction
    predicted_performance: PerformanceMetrics
    hire_recommendation: str  # strong_yes, yes, maybe, no, strong_no
    recommendation_confidence: float = Field(..., ge=0.0, le=1.0)
    
    # Metadata
    total_questions: int = Field(..., ge=0)
    analysis_version: str
    processed_at: datetime = Field(default_factory=datetime.utcnow)


class CandidateHistoricalAnalytics(BaseModel):
    """Historical analytics for a candidate across multiple interviews"""
    candidate_id: int
    total_interviews: int = Field(..., ge=0)
    date_range: Dict[str, datetime]  # first, last interview dates
    
    # Performance Trends
    performance_trend: str  # improving, stable, declining
    avg_overall_score: float = Field(..., ge=0.0, le=1.0)
    score_consistency: float = Field(..., ge=0.0, le=1.0)  # variance measure
    
    # Communication Trends
    communication_improvement: float = Field(..., ge=-1.0, le=1.0)  # change over time
    avg_engagement_score: float = Field(..., ge=0.0, le=1.0)
    sentiment_stability: float = Field(..., ge=0.0, le=1.0)
    
    # Technical Competency
    technical_growth: float = Field(..., ge=-1.0, le=1.0)
    strong_technical_areas: List[str] = Field(default_factory=list)
    improvement_areas: List[str] = Field(default_factory=list)
    
    # Behavioral Patterns
    stress_management: str  # excellent, good, fair, poor
    confidence_development: str  # growing, stable, declining
    adaptability_score: float = Field(..., ge=0.0, le=1.0)
    
    # Interview Performance by Role
    role_performance: Dict[str, float] = Field(default_factory=dict)
    best_fit_roles: List[str] = Field(default_factory=list)
    
    # Red Flags and Strengths
    recurring_strengths: List[str] = Field(default_factory=list)
    areas_of_concern: List[str] = Field(default_factory=list)
    bias_incidents: int = Field(..., ge=0)
    
    # Recommendations
    development_recommendations: List[str] = Field(default_factory=list)
    interview_preparation_tips: List[str] = Field(default_factory=list)
    
    last_updated: datetime = Field(default_factory=datetime.utcnow)


# Validation helpers
@validator('*', pre=True)
def validate_scores(cls, v):
    """Ensure scores are within valid ranges"""
    if isinstance(v, (int, float)):
        # Handle percentage scores
        if hasattr(cls, '__fields__') and any(
            field.field_info.ge == 0.0 and field.field_info.le == 1.0 
            for field in cls.__fields__.values()
        ):
            return max(0.0, min(1.0, float(v)))
        # Handle sentiment scores
        elif any(
            field.field_info.ge == -1.0 and field.field_info.le == 1.0 
            for field in cls.__fields__.values()
        ):
            return max(-1.0, min(1.0, float(v)))
    return v


# Error models
class AnalysisError(BaseModel):
    """Error response model"""
    error_code: str
    message: str
    details: Optional[Dict[str, Any]] = None
    session_id: Optional[str] = None
    timestamp: datetime = Field(default_factory=datetime.utcnow)


class ValidationError(BaseModel):
    """Validation error details"""
    field: str
    message: str
    invalid_value: Any
