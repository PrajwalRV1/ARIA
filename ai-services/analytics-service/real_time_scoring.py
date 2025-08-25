#!/usr/bin/env python3
"""
Real-time Scoring Engine for ARIA Interview Platform

Provides continuous evaluation of candidate responses with:
- Rubric-based scoring across multiple dimensions
- Skill mapping and competency tracking
- Performance trend analysis
- Red flag detection for recruiter alerts
- Adaptive scoring based on context
"""

import asyncio
import json
import logging
import numpy as np
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum
import re
import statistics
from collections import defaultdict

# NLP and ML imports
try:
    import nltk
    from nltk.sentiment import SentimentIntensityAnalyzer
    from nltk.tokenize import word_tokenize, sent_tokenize
    from textstat import flesch_reading_ease, coleman_liau_index
    NLTK_AVAILABLE = True
except ImportError:
    NLTK_AVAILABLE = False
    logging.warning("NLTK not available - using basic text analysis")

try:
    from transformers import pipeline, AutoTokenizer, AutoModel
    import torch
    TRANSFORMERS_AVAILABLE = True
except ImportError:
    TRANSFORMERS_AVAILABLE = False
    logging.warning("Transformers not available - using rule-based analysis")

logger = logging.getLogger(__name__)

# ==================== ENUMS AND DATA CLASSES ====================

class ScoreLevel(Enum):
    EXCELLENT = 5
    GOOD = 4
    SATISFACTORY = 3
    NEEDS_IMPROVEMENT = 2
    POOR = 1

class SkillCategory(Enum):
    TECHNICAL_KNOWLEDGE = "technical_knowledge"
    PROBLEM_SOLVING = "problem_solving"
    COMMUNICATION = "communication"
    CODING_ABILITY = "coding_ability"
    SYSTEM_DESIGN = "system_design"
    BEHAVIORAL = "behavioral"
    LEADERSHIP = "leadership"
    ADAPTABILITY = "adaptability"

class RedFlagType(Enum):
    INCONSISTENT_ANSWERS = "inconsistent_answers"
    LACK_OF_KNOWLEDGE = "lack_of_knowledge"
    POOR_COMMUNICATION = "poor_communication"
    ETHICAL_CONCERNS = "ethical_concerns"
    OVERCONFIDENCE = "overconfidence"
    UNDERCONFIDENCE = "underconfidence"
    TIME_MANAGEMENT = "time_management"
    TECHNICAL_GAPS = "technical_gaps"

@dataclass
class ScoringRubric:
    """Scoring rubric for different evaluation dimensions"""
    dimension: str
    weight: float
    criteria: Dict[ScoreLevel, str]
    keywords_positive: List[str] = field(default_factory=list)
    keywords_negative: List[str] = field(default_factory=list)
    min_response_length: int = 10
    max_response_length: int = 1000

@dataclass 
class ResponseScore:
    """Score for a single response"""
    question_id: str
    dimension_scores: Dict[str, float]
    overall_score: float
    confidence: float
    feedback: Dict[str, str]
    red_flags: List[Dict[str, Any]]
    skill_assessments: Dict[SkillCategory, float]
    metadata: Dict[str, Any]
    timestamp: datetime

@dataclass
class CandidateProfile:
    """Dynamic candidate profile with skill tracking"""
    candidate_id: str
    job_role: str
    experience_level: int
    required_skills: List[str]
    skill_scores: Dict[SkillCategory, List[float]] = field(default_factory=lambda: defaultdict(list))
    performance_trend: List[float] = field(default_factory=list)
    red_flag_count: Dict[RedFlagType, int] = field(default_factory=lambda: defaultdict(int))
    strengths: List[str] = field(default_factory=list)
    improvement_areas: List[str] = field(default_factory=list)
    overall_theta: float = 0.0
    confidence_level: float = 0.5

# ==================== REAL-TIME SCORING ENGINE ====================

class RealTimeScoringEngine:
    """Main scoring engine for real-time candidate evaluation"""
    
    def __init__(self):
        self.rubrics = {}
        self.candidate_profiles: Dict[str, CandidateProfile] = {}
        self.session_contexts: Dict[str, Dict] = {}
        self.scoring_models = {}
        
        # Initialize components
        self._initialize_rubrics()
        self._initialize_models()
        
        logger.info("Real-time Scoring Engine initialized")
    
    def _initialize_rubrics(self):
        """Initialize scoring rubrics for different dimensions"""
        
        # Technical Knowledge Rubric
        self.rubrics["technical_knowledge"] = ScoringRubric(
            dimension="technical_knowledge",
            weight=0.25,
            criteria={
                ScoreLevel.EXCELLENT: "Demonstrates deep understanding of concepts with accurate details and advanced insights",
                ScoreLevel.GOOD: "Shows solid understanding with mostly correct information and some depth",
                ScoreLevel.SATISFACTORY: "Basic understanding with correct fundamental concepts",
                ScoreLevel.NEEDS_IMPROVEMENT: "Limited understanding with some misconceptions",
                ScoreLevel.POOR: "Significant gaps in knowledge or major misconceptions"
            },
            keywords_positive=["implement", "optimize", "scalable", "efficient", "best practice", "architecture", "design pattern"],
            keywords_negative=["don't know", "not sure", "maybe", "probably", "I think", "confused"]
        )
        
        # Problem Solving Rubric
        self.rubrics["problem_solving"] = ScoringRubric(
            dimension="problem_solving",
            weight=0.25,
            criteria={
                ScoreLevel.EXCELLENT: "Systematic approach with clear reasoning, considers edge cases and alternatives",
                ScoreLevel.GOOD: "Logical approach with mostly clear reasoning and good analysis",
                ScoreLevel.SATISFACTORY: "Basic problem-solving approach with adequate reasoning",
                ScoreLevel.NEEDS_IMPROVEMENT: "Unclear reasoning or inefficient approach",
                ScoreLevel.POOR: "No clear problem-solving approach or flawed reasoning"
            },
            keywords_positive=["analyze", "approach", "solution", "algorithm", "optimize", "trade-off", "complexity"],
            keywords_negative=["random", "guess", "trial and error", "no idea", "whatever"]
        )
        
        # Communication Rubric
        self.rubrics["communication"] = ScoringRubric(
            dimension="communication",
            weight=0.20,
            criteria={
                ScoreLevel.EXCELLENT: "Clear, well-structured responses with appropriate technical language",
                ScoreLevel.GOOD: "Generally clear communication with good structure",
                ScoreLevel.SATISFACTORY: "Adequate communication, mostly understandable",
                ScoreLevel.NEEDS_IMPROVEMENT: "Unclear or poorly structured responses",
                ScoreLevel.POOR: "Very difficult to understand or follow"
            },
            keywords_positive=["explain", "clarify", "example", "specifically", "in other words", "to summarize"],
            keywords_negative=["um", "uh", "like", "you know", "stuff", "thing", "whatever"]
        )
        
        # Coding Ability Rubric
        self.rubrics["coding_ability"] = ScoringRubric(
            dimension="coding_ability",
            weight=0.20,
            criteria={
                ScoreLevel.EXCELLENT: "Clean, efficient code with proper syntax and good practices",
                ScoreLevel.GOOD: "Functional code with mostly correct syntax and reasonable approach",
                ScoreLevel.SATISFACTORY: "Working code with basic functionality",
                ScoreLevel.NEEDS_IMPROVEMENT: "Code with issues but shows some understanding",
                ScoreLevel.POOR: "Significant coding errors or no working solution"
            },
            keywords_positive=["function", "class", "method", "variable", "loop", "condition", "algorithm"],
            keywords_negative=["error", "bug", "broken", "doesn't work", "syntax error"]
        )
        
        # Behavioral Rubric
        self.rubrics["behavioral"] = ScoringRubric(
            dimension="behavioral",
            weight=0.10,
            criteria={
                ScoreLevel.EXCELLENT: "Excellent examples with clear STAR format and strong outcomes",
                ScoreLevel.GOOD: "Good examples with clear situations and positive outcomes",
                ScoreLevel.SATISFACTORY: "Adequate examples with basic structure",
                ScoreLevel.NEEDS_IMPROVEMENT: "Unclear examples or weak outcomes",
                ScoreLevel.POOR: "No clear examples or negative indicators"
            },
            keywords_positive=["led", "improved", "achieved", "collaborated", "resolved", "initiative"],
            keywords_negative=["conflict", "failed", "couldn't", "gave up", "blamed"]
        )
        
        logger.info(f"Initialized {len(self.rubrics)} scoring rubrics")
    
    def _initialize_models(self):
        """Initialize ML models for advanced scoring"""
        try:
            if TRANSFORMERS_AVAILABLE:
                # Initialize sentiment analysis
                self.scoring_models["sentiment"] = pipeline(
                    "sentiment-analysis",
                    model="cardiffnlp/twitter-roberta-base-sentiment-latest",
                    device="cpu"
                )
                
                # Initialize technical competency model
                self.scoring_models["technical"] = pipeline(
                    "text-classification",
                    model="microsoft/DialoGPT-medium",
                    device="cpu"
                )
                
                logger.info("Transformer models initialized")
            
            if NLTK_AVAILABLE:
                # Download required NLTK data
                try:
                    nltk.download('vader_lexicon', quiet=True)
                    nltk.download('punkt', quiet=True)
                    self.scoring_models["sentiment_nltk"] = SentimentIntensityAnalyzer()
                    logger.info("NLTK models initialized")
                except Exception as e:
                    logger.warning(f"NLTK initialization failed: {e}")
                    
        except Exception as e:
            logger.error(f"Model initialization failed: {e}")
    
    async def score_response(
        self,
        session_id: str,
        candidate_id: str,
        question_id: str,
        response_text: str,
        response_metadata: Dict[str, Any],
        question_context: Dict[str, Any]
    ) -> ResponseScore:
        """Score a candidate response in real-time"""
        
        try:
            logger.debug(f"Scoring response for session {session_id}, question {question_id}")
            
            # Get or create candidate profile
            if candidate_id not in self.candidate_profiles:
                self.candidate_profiles[candidate_id] = CandidateProfile(
                    candidate_id=candidate_id,
                    job_role=question_context.get("job_role", ""),
                    experience_level=question_context.get("experience_level", 0),
                    required_skills=question_context.get("required_skills", [])
                )
            
            profile = self.candidate_profiles[candidate_id]
            
            # Preprocess response
            processed_response = self._preprocess_response(response_text)
            
            # Calculate dimension scores
            dimension_scores = {}
            skill_assessments = {}
            feedback = {}
            red_flags = []
            
            # Score each dimension
            for dimension, rubric in self.rubrics.items():
                if self._is_dimension_applicable(dimension, question_context):
                    score = await self._score_dimension(
                        processed_response,
                        rubric,
                        question_context,
                        profile
                    )
                    dimension_scores[dimension] = score["score"]
                    feedback[dimension] = score["feedback"]
                    
                    if score["red_flags"]:
                        red_flags.extend(score["red_flags"])
            
            # Calculate skill assessments
            skill_assessments = await self._assess_skills(
                processed_response,
                question_context,
                dimension_scores
            )
            
            # Calculate overall score
            overall_score = self._calculate_overall_score(dimension_scores)
            
            # Calculate confidence based on response quality
            confidence = self._calculate_confidence(
                processed_response,
                dimension_scores,
                response_metadata
            )
            
            # Detect additional red flags
            additional_red_flags = await self._detect_red_flags(
                processed_response,
                profile,
                question_context
            )
            red_flags.extend(additional_red_flags)
            
            # Create response score
            response_score = ResponseScore(
                question_id=question_id,
                dimension_scores=dimension_scores,
                overall_score=overall_score,
                confidence=confidence,
                feedback=feedback,
                red_flags=red_flags,
                skill_assessments=skill_assessments,
                metadata={
                    "response_length": len(response_text.split()),
                    "response_time": response_metadata.get("response_time_seconds", 0),
                    "question_type": question_context.get("question_type", ""),
                    "difficulty_level": question_context.get("difficulty", 0)
                },
                timestamp=datetime.now()
            )
            
            # Update candidate profile
            await self._update_candidate_profile(profile, response_score, question_context)
            
            logger.debug(f"Response scored: {overall_score:.2f} (confidence: {confidence:.2f})")
            return response_score
            
        except Exception as e:
            logger.error(f"Error scoring response: {e}")
            # Return default score on error
            return ResponseScore(
                question_id=question_id,
                dimension_scores={},
                overall_score=0.0,
                confidence=0.0,
                feedback={},
                red_flags=[],
                skill_assessments={},
                metadata={"error": str(e)},
                timestamp=datetime.now()
            )
    
    def _preprocess_response(self, response_text: str) -> Dict[str, Any]:
        """Preprocess response text for analysis"""
        
        processed = {
            "original": response_text,
            "cleaned": re.sub(r'[^\w\s]', '', response_text.lower()),
            "words": response_text.split(),
            "sentences": sent_tokenize(response_text) if NLTK_AVAILABLE else [response_text],
            "word_count": len(response_text.split()),
            "char_count": len(response_text),
            "avg_sentence_length": 0,
            "readability_score": 0
        }
        
        # Calculate additional metrics
        if processed["sentences"]:
            sentence_lengths = [len(s.split()) for s in processed["sentences"]]
            processed["avg_sentence_length"] = statistics.mean(sentence_lengths)
        
        # Calculate readability if possible
        try:
            processed["readability_score"] = flesch_reading_ease(response_text)
        except:
            processed["readability_score"] = 50  # Default neutral score
        
        return processed
    
    def _is_dimension_applicable(self, dimension: str, question_context: Dict[str, Any]) -> bool:
        """Check if a scoring dimension applies to the current question"""
        
        question_type = question_context.get("question_type", "")
        
        # Map question types to applicable dimensions
        dimension_mapping = {
            "technical": ["technical_knowledge", "problem_solving", "communication"],
            "coding": ["coding_ability", "problem_solving", "technical_knowledge"],
            "behavioral": ["behavioral", "communication"],
            "system_design": ["technical_knowledge", "problem_solving", "communication"],
        }
        
        for q_type, applicable_dims in dimension_mapping.items():
            if q_type in question_type.lower() and dimension in applicable_dims:
                return True
        
        # Default: apply communication to all questions
        return dimension == "communication"
    
    async def _score_dimension(
        self,
        processed_response: Dict[str, Any],
        rubric: ScoringRubric,
        question_context: Dict[str, Any],
        profile: CandidateProfile
    ) -> Dict[str, Any]:
        """Score a specific dimension using the rubric"""
        
        try:
            response_text = processed_response["original"]
            word_count = processed_response["word_count"]
            
            # Initialize score components
            keyword_score = 0.0
            length_score = 0.0
            sentiment_score = 0.0
            ml_score = 0.0
            
            # 1. Keyword-based scoring
            positive_matches = sum(1 for kw in rubric.keywords_positive 
                                 if kw.lower() in processed_response["cleaned"])
            negative_matches = sum(1 for kw in rubric.keywords_negative 
                                 if kw.lower() in processed_response["cleaned"])
            
            keyword_score = min(1.0, positive_matches * 0.2) - min(0.5, negative_matches * 0.1)
            
            # 2. Length-based scoring
            if word_count >= rubric.min_response_length:
                if word_count <= rubric.max_response_length:
                    length_score = 1.0
                else:
                    # Penalize overly long responses
                    length_score = max(0.5, 1.0 - (word_count - rubric.max_response_length) / 500)
            else:
                length_score = word_count / rubric.min_response_length
            
            # 3. Sentiment analysis
            if NLTK_AVAILABLE and "sentiment_nltk" in self.scoring_models:
                sentiment = self.scoring_models["sentiment_nltk"].polarity_scores(response_text)
                sentiment_score = (sentiment['compound'] + 1) / 2  # Normalize to 0-1
            else:
                sentiment_score = 0.5  # Neutral default
            
            # 4. ML-based scoring (if available)
            if TRANSFORMERS_AVAILABLE and rubric.dimension in ["technical_knowledge", "communication"]:
                try:
                    ml_result = self.scoring_models["sentiment"](response_text[:512])  # Limit length
                    if ml_result and len(ml_result) > 0:
                        # Convert sentiment to score
                        if ml_result[0]["label"] == "POSITIVE":
                            ml_score = ml_result[0]["score"]
                        else:
                            ml_score = 1.0 - ml_result[0]["score"]
                except:
                    ml_score = 0.5
            else:
                ml_score = 0.5
            
            # Combine scores with weights
            final_score = (
                keyword_score * 0.3 +
                length_score * 0.2 +
                sentiment_score * 0.2 +
                ml_score * 0.3
            )
            
            # Apply experience level adjustment
            experience_adjustment = min(0.1, profile.experience_level * 0.02)
            final_score = min(1.0, final_score + experience_adjustment)
            
            # Convert to 1-5 scale
            scaled_score = 1.0 + (final_score * 4.0)
            
            # Generate feedback
            feedback = self._generate_feedback(scaled_score, rubric, processed_response)
            
            # Detect red flags for this dimension
            red_flags = self._detect_dimension_red_flags(
                rubric.dimension,
                processed_response,
                scaled_score
            )
            
            return {
                "score": scaled_score,
                "feedback": feedback,
                "red_flags": red_flags,
                "components": {
                    "keyword_score": keyword_score,
                    "length_score": length_score,
                    "sentiment_score": sentiment_score,
                    "ml_score": ml_score
                }
            }
            
        except Exception as e:
            logger.error(f"Error scoring dimension {rubric.dimension}: {e}")
            return {
                "score": 2.5,  # Neutral score
                "feedback": "Unable to analyze response",
                "red_flags": [],
                "components": {}
            }
    
    async def _assess_skills(
        self,
        processed_response: Dict[str, Any],
        question_context: Dict[str, Any],
        dimension_scores: Dict[str, float]
    ) -> Dict[SkillCategory, float]:
        """Assess candidate skills based on response"""
        
        skill_scores = {}
        response_text = processed_response["cleaned"]
        
        # Technical Knowledge Assessment
        technical_keywords = ["api", "database", "algorithm", "framework", "architecture", 
                            "design", "pattern", "scalable", "performance", "security"]
        tech_score = sum(1 for kw in technical_keywords if kw in response_text) / len(technical_keywords)
        skill_scores[SkillCategory.TECHNICAL_KNOWLEDGE] = min(5.0, 1.0 + tech_score * 4.0)
        
        # Problem Solving Assessment
        problem_keywords = ["analyze", "solution", "approach", "strategy", "optimize", 
                          "improve", "resolve", "consider", "evaluate", "implement"]
        problem_score = sum(1 for kw in problem_keywords if kw in response_text) / len(problem_keywords)
        skill_scores[SkillCategory.PROBLEM_SOLVING] = min(5.0, 1.0 + problem_score * 4.0)
        
        # Communication Assessment
        if "communication" in dimension_scores:
            skill_scores[SkillCategory.COMMUNICATION] = dimension_scores["communication"]
        
        # Coding Ability Assessment
        if "coding_ability" in dimension_scores:
            skill_scores[SkillCategory.CODING_ABILITY] = dimension_scores["coding_ability"]
        
        # System Design Assessment
        design_keywords = ["microservice", "load", "balance", "scale", "distributed", 
                         "cache", "queue", "service", "component", "integration"]
        design_score = sum(1 for kw in design_keywords if kw in response_text) / len(design_keywords)
        skill_scores[SkillCategory.SYSTEM_DESIGN] = min(5.0, 1.0 + design_score * 4.0)
        
        # Behavioral Assessment
        if "behavioral" in dimension_scores:
            skill_scores[SkillCategory.BEHAVIORAL] = dimension_scores["behavioral"]
        
        return skill_scores
    
    def _calculate_overall_score(self, dimension_scores: Dict[str, float]) -> float:
        """Calculate overall weighted score"""
        
        if not dimension_scores:
            return 0.0
        
        weighted_sum = 0.0
        total_weight = 0.0
        
        for dimension, score in dimension_scores.items():
            if dimension in self.rubrics:
                weight = self.rubrics[dimension].weight
                weighted_sum += score * weight
                total_weight += weight
        
        return weighted_sum / total_weight if total_weight > 0 else 0.0
    
    def _calculate_confidence(
        self,
        processed_response: Dict[str, Any],
        dimension_scores: Dict[str, float],
        response_metadata: Dict[str, Any]
    ) -> float:
        """Calculate confidence in the scoring"""
        
        factors = []
        
        # Response length factor
        word_count = processed_response["word_count"]
        if word_count >= 20:
            factors.append(min(1.0, word_count / 100))
        else:
            factors.append(word_count / 20)
        
        # Response time factor
        response_time = response_metadata.get("response_time_seconds", 0)
        if 10 <= response_time <= 300:  # 10 seconds to 5 minutes
            factors.append(1.0)
        elif response_time < 10:
            factors.append(response_time / 10)
        else:
            factors.append(max(0.5, 300 / response_time))
        
        # Score consistency factor
        if dimension_scores:
            score_variance = statistics.variance(dimension_scores.values())
            consistency_factor = max(0.5, 1.0 - score_variance / 2.0)
            factors.append(consistency_factor)
        
        # Readability factor
        readability = processed_response.get("readability_score", 50)
        if 30 <= readability <= 70:  # Good readability range
            factors.append(1.0)
        else:
            factors.append(max(0.5, 1.0 - abs(readability - 50) / 50))
        
        return statistics.mean(factors) if factors else 0.5
    
    def _generate_feedback(
        self,
        score: float,
        rubric: ScoringRubric,
        processed_response: Dict[str, Any]
    ) -> str:
        """Generate human-readable feedback for the dimension"""
        
        # Determine score level
        if score >= 4.5:
            level = ScoreLevel.EXCELLENT
        elif score >= 3.5:
            level = ScoreLevel.GOOD
        elif score >= 2.5:
            level = ScoreLevel.SATISFACTORY
        elif score >= 1.5:
            level = ScoreLevel.NEEDS_IMPROVEMENT
        else:
            level = ScoreLevel.POOR
        
        base_feedback = rubric.criteria[level]
        
        # Add specific suggestions
        word_count = processed_response["word_count"]
        if word_count < rubric.min_response_length:
            base_feedback += " Consider providing more detailed explanations."
        elif word_count > rubric.max_response_length:
            base_feedback += " Try to be more concise in your responses."
        
        return base_feedback
    
    def _detect_dimension_red_flags(
        self,
        dimension: str,
        processed_response: Dict[str, Any],
        score: float
    ) -> List[Dict[str, Any]]:
        """Detect red flags specific to a dimension"""
        
        red_flags = []
        response_text = processed_response["cleaned"]
        
        # Low score red flag
        if score < 2.0:
            red_flags.append({
                "type": RedFlagType.LACK_OF_KNOWLEDGE.value,
                "severity": "high",
                "message": f"Very low score in {dimension}",
                "dimension": dimension
            })
        
        # Communication-specific red flags
        if dimension == "communication":
            filler_words = ["um", "uh", "like", "you know"]
            filler_count = sum(response_text.count(word) for word in filler_words)
            if filler_count > 3:
                red_flags.append({
                    "type": RedFlagType.POOR_COMMUNICATION.value,
                    "severity": "medium",
                    "message": "Excessive use of filler words",
                    "dimension": dimension
                })
        
        # Technical-specific red flags
        if dimension == "technical_knowledge":
            uncertainty_phrases = ["don't know", "not sure", "maybe", "i think"]
            uncertainty_count = sum(response_text.count(phrase) for phrase in uncertainty_phrases)
            if uncertainty_count > 2:
                red_flags.append({
                    "type": RedFlagType.UNDERCONFIDENCE.value,
                    "severity": "medium",
                    "message": "Shows uncertainty in technical knowledge",
                    "dimension": dimension
                })
        
        return red_flags
    
    async def _detect_red_flags(
        self,
        processed_response: Dict[str, Any],
        profile: CandidateProfile,
        question_context: Dict[str, Any]
    ) -> List[Dict[str, Any]]:
        """Detect general red flags across all dimensions"""
        
        red_flags = []
        response_text = processed_response["original"]
        word_count = processed_response["word_count"]
        
        # Very short responses
        if word_count < 5:
            red_flags.append({
                "type": RedFlagType.POOR_COMMUNICATION.value,
                "severity": "high",
                "message": "Response too short - lacks sufficient detail",
                "dimension": "general"
            })
        
        # Excessive confidence without substance
        confidence_phrases = ["definitely", "absolutely", "obviously", "clearly", "of course"]
        confidence_count = sum(response_text.lower().count(phrase) for phrase in confidence_phrases)
        if confidence_count > 2 and word_count < 50:
            red_flags.append({
                "type": RedFlagType.OVERCONFIDENCE.value,
                "severity": "medium",
                "message": "Shows overconfidence with limited explanation",
                "dimension": "general"
            })
        
        # Ethical concerns
        concerning_phrases = ["hack", "cheat", "steal", "copy", "plagiarize"]
        if any(phrase in response_text.lower() for phrase in concerning_phrases):
            red_flags.append({
                "type": RedFlagType.ETHICAL_CONCERNS.value,
                "severity": "high",
                "message": "Response contains potentially concerning language",
                "dimension": "behavioral"
            })
        
        return red_flags
    
    async def _update_candidate_profile(
        self,
        profile: CandidateProfile,
        response_score: ResponseScore,
        question_context: Dict[str, Any]
    ):
        """Update candidate profile with latest response data"""
        
        # Update skill scores
        for skill, score in response_score.skill_assessments.items():
            profile.skill_scores[skill].append(score)
            
            # Keep only recent scores (last 10)
            if len(profile.skill_scores[skill]) > 10:
                profile.skill_scores[skill] = profile.skill_scores[skill][-10:]
        
        # Update performance trend
        profile.performance_trend.append(response_score.overall_score)
        if len(profile.performance_trend) > 20:
            profile.performance_trend = profile.performance_trend[-20:]
        
        # Update red flag counts
        for red_flag in response_score.red_flags:
            flag_type = RedFlagType(red_flag["type"])
            profile.red_flag_count[flag_type] += 1
        
        # Update overall theta (ability estimate)
        if profile.performance_trend:
            recent_scores = profile.performance_trend[-5:]
            profile.overall_theta = statistics.mean(recent_scores)
            profile.confidence_level = min(1.0, len(profile.performance_trend) / 10)
        
        # Identify strengths and improvement areas
        self._analyze_candidate_strengths_weaknesses(profile)
    
    def _analyze_candidate_strengths_weaknesses(self, profile: CandidateProfile):
        """Analyze candidate's strengths and areas for improvement"""
        
        strengths = []
        improvements = []
        
        # Analyze each skill category
        for skill, scores in profile.skill_scores.items():
            if scores:
                avg_score = statistics.mean(scores)
                if avg_score >= 4.0:
                    strengths.append(skill.value.replace("_", " ").title())
                elif avg_score <= 2.5:
                    improvements.append(skill.value.replace("_", " ").title())
        
        profile.strengths = strengths[:5]  # Top 5 strengths
        profile.improvement_areas = improvements[:5]  # Top 5 areas for improvement
    
    async def get_session_analytics(self, session_id: str) -> Dict[str, Any]:
        """Get comprehensive analytics for a session"""
        
        try:
            # This would typically query a database for session data
            # For now, return sample analytics structure
            
            analytics = {
                "session_id": session_id,
                "overall_performance": {
                    "average_score": 0.0,
                    "score_trend": [],
                    "confidence_trend": [],
                    "question_count": 0
                },
                "skill_breakdown": {},
                "red_flags": {
                    "total_count": 0,
                    "by_type": {},
                    "severity_distribution": {}
                },
                "recommendations": [],
                "comparative_analysis": {}
            }
            
            return analytics
            
        except Exception as e:
            logger.error(f"Error getting session analytics: {e}")
            return {}
    
    async def get_candidate_report(self, candidate_id: str) -> Dict[str, Any]:
        """Generate comprehensive candidate evaluation report"""
        
        try:
            if candidate_id not in self.candidate_profiles:
                return {"error": "Candidate profile not found"}
            
            profile = self.candidate_profiles[candidate_id]
            
            # Calculate summary statistics
            skill_averages = {}
            for skill, scores in profile.skill_scores.items():
                if scores:
                    skill_averages[skill.value] = {
                        "average": statistics.mean(scores),
                        "trend": "improving" if len(scores) > 1 and scores[-1] > scores[0] else "stable",
                        "consistency": 1.0 - (statistics.stdev(scores) if len(scores) > 1 else 0.0)
                    }
            
            # Generate recommendations
            recommendations = self._generate_recommendations(profile)
            
            report = {
                "candidate_id": candidate_id,
                "profile": {
                    "job_role": profile.job_role,
                    "experience_level": profile.experience_level,
                    "overall_theta": profile.overall_theta,
                    "confidence_level": profile.confidence_level
                },
                "performance_summary": {
                    "overall_score": statistics.mean(profile.performance_trend) if profile.performance_trend else 0.0,
                    "score_range": [min(profile.performance_trend), max(profile.performance_trend)] if profile.performance_trend else [0, 0],
                    "performance_trend": "improving" if len(profile.performance_trend) > 1 and profile.performance_trend[-1] > profile.performance_trend[0] else "stable"
                },
                "skill_assessment": skill_averages,
                "strengths": profile.strengths,
                "improvement_areas": profile.improvement_areas,
                "red_flags": dict(profile.red_flag_count),
                "recommendations": recommendations,
                "generated_at": datetime.now().isoformat()
            }
            
            return report
            
        except Exception as e:
            logger.error(f"Error generating candidate report: {e}")
            return {"error": str(e)}
    
    def _generate_recommendations(self, profile: CandidateProfile) -> List[str]:
        """Generate hiring recommendations based on profile"""
        
        recommendations = []
        
        if profile.overall_theta >= 3.5:
            recommendations.append("Strong candidate - recommend for next round")
        elif profile.overall_theta >= 2.5:
            recommendations.append("Adequate candidate - consider based on other factors")
        else:
            recommendations.append("Candidate needs improvement - not recommended")
        
        # Skill-specific recommendations
        for skill, scores in profile.skill_scores.items():
            if scores and statistics.mean(scores) >= 4.0:
                recommendations.append(f"Excellent {skill.value.replace('_', ' ')} skills")
        
        # Red flag warnings
        total_red_flags = sum(profile.red_flag_count.values())
        if total_red_flags > 5:
            recommendations.append("Candidate has multiple red flags - proceed with caution")
        
        return recommendations[:10]  # Limit to 10 recommendations


# ==================== WEBSOCKET REAL-TIME SCORING ====================

class RealTimeScoringWebSocket:
    """WebSocket handler for real-time scoring updates"""
    
    def __init__(self, scoring_engine: RealTimeScoringEngine):
        self.scoring_engine = scoring_engine
        self.active_connections: Dict[str, List] = {}
        
    async def connect(self, websocket, session_id: str):
        """Connect to real-time scoring"""
        if session_id not in self.active_connections:
            self.active_connections[session_id] = []
        self.active_connections[session_id].append(websocket)
        
        # Send initial scoring configuration
        await websocket.send_json({
            "type": "scoring_config",
            "rubrics": {name: {"dimension": r.dimension, "weight": r.weight} 
                       for name, r in self.scoring_engine.rubrics.items()}
        })
    
    async def disconnect(self, websocket, session_id: str):
        """Disconnect from real-time scoring"""
        if session_id in self.active_connections:
            if websocket in self.active_connections[session_id]:
                self.active_connections[session_id].remove(websocket)
            if not self.active_connections[session_id]:
                del self.active_connections[session_id]
    
    async def broadcast_score_update(self, session_id: str, score_data: Dict[str, Any]):
        """Broadcast score update to all connected clients"""
        if session_id in self.active_connections:
            message = {
                "type": "score_update",
                "session_id": session_id,
                "data": score_data,
                "timestamp": datetime.now().isoformat()
            }
            
            for connection in self.active_connections[session_id]:
                try:
                    await connection.send_json(message)
                except Exception as e:
                    logger.error(f"Error broadcasting score update: {e}")


# Example usage and initialization
if __name__ == "__main__":
    # Initialize scoring engine
    engine = RealTimeScoringEngine()
    
    # Example scoring
    async def test_scoring():
        response = await engine.score_response(
            session_id="test_session",
            candidate_id="candidate_123",
            question_id="q_001",
            response_text="I would use a REST API with proper authentication and caching to solve this problem efficiently.",
            response_metadata={"response_time_seconds": 45},
            question_context={
                "question_type": "technical",
                "job_role": "Backend Engineer",
                "experience_level": 3,
                "required_skills": ["API Design", "System Architecture"]
            }
        )
        
        print(f"Overall Score: {response.overall_score:.2f}")
        print(f"Confidence: {response.confidence:.2f}")
        print(f"Skill Assessments: {response.skill_assessments}")
        print(f"Red Flags: {len(response.red_flags)}")
    
    # Run test
    asyncio.run(test_scoring())
