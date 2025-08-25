#!/usr/bin/env python3
"""
Question Selector Module for ARIA Adaptive Interview Platform
Implements intelligent question selection using Item Response Theory (IRT)
with support for candidate constraints, skill matching, and bias prevention.
"""

import numpy as np
import logging
from typing import Dict, List, Optional, Any, Tuple
from datetime import datetime, timedelta
from dataclasses import dataclass
from enum import Enum
import json
import redis
import mysql.connector
import asyncio
from collections import defaultdict
import random
from sklearn.metrics.pairwise import cosine_similarity

logger = logging.getLogger(__name__)

class QuestionType(Enum):
    """Types of interview questions"""
    TECHNICAL = "technical"
    PROBLEM_SOLVING = "problem_solving" 
    CODING = "coding"
    SYSTEM_DESIGN = "system_design"
    BEHAVIORAL = "behavioral"
    CONCEPTUAL = "conceptual"
    SCENARIO = "scenario"

class SelectionStrategy(Enum):
    """Question selection strategies"""
    MAXIMUM_INFORMATION = "max_information"
    TARGETED_DIFFICULTY = "targeted_difficulty"
    BALANCED_COVERAGE = "balanced_coverage"
    SKILL_EXPLORATION = "skill_exploration"
    ADAPTIVE_HYBRID = "adaptive_hybrid"

@dataclass
class QuestionCandidate:
    """Represents a question candidate for selection"""
    question_id: int
    question_text: str
    question_type: QuestionType
    difficulty: float
    discrimination: float
    guessing_parameter: float
    category: str
    technologies: List[str]
    expected_duration_minutes: int
    information_value: float
    skill_coverage: List[str]
    bias_score: float
    effectiveness_score: float
    selection_score: float

@dataclass
class SelectionConstraints:
    """Constraints for question selection"""
    min_difficulty: float
    max_difficulty: float
    excluded_questions: List[int]
    required_technologies: List[str]
    preferred_question_types: List[QuestionType]
    max_duration: int
    skill_areas: List[str]
    avoid_similar_questions: bool

class QuestionSelector:
    """
    Advanced question selector using IRT and machine learning techniques
    for optimal adaptive question selection in interviews
    """
    
    def __init__(self):
        """Initialize the question selector"""
        self.redis_client = redis.Redis(host='localhost', port=6379, db=3, decode_responses=True)
        
        # Database connection
        self.db_config = {
            'host': 'localhost',
            'user': 'aria_user',
            'password': 'aria_password',
            'database': 'aria_interviews',
            'autocommit': True
        }
        
        # Selection parameters
        self.default_strategy = SelectionStrategy.ADAPTIVE_HYBRID
        self.information_weight = 0.4
        self.difficulty_match_weight = 0.3
        self.skill_coverage_weight = 0.2
        self.bias_penalty_weight = 0.1
        
        # Similarity thresholds
        self.content_similarity_threshold = 0.8
        self.skill_similarity_threshold = 0.7
        
        # Caching parameters
        self.cache_ttl = 1800  # 30 minutes
        self.question_pool_size = 200  # Maximum questions to consider
        
        # Question bank cache
        self.question_cache = {}
        self.last_cache_update = datetime.min
        self.cache_refresh_interval = timedelta(hours=1)
        
        logger.info("Question Selector initialized with adaptive selection algorithms")
    
    async def select_next_question(
        self,
        session_id: str,
        current_theta: float,
        standard_error: float,
        answered_questions: List[int],
        job_role: str,
        experience_level: str,
        technologies: List[str],
        difficulty_range: Tuple[float, float] = (-3.0, 3.0),
        question_type: Optional[str] = None,
        selection_strategy: SelectionStrategy = None,
        job_description: Optional[str] = None,
        key_responsibilities: Optional[str] = None
    ) -> Optional[Dict[str, Any]]:
        """
        Select the next optimal question for the candidate
        
        Args:
            session_id: Interview session ID
            current_theta: Current ability estimate
            standard_error: Current standard error of ability estimate
            answered_questions: List of already answered question IDs
            job_role: Candidate's target job role
            experience_level: Candidate's experience level
            technologies: List of relevant technologies
            difficulty_range: Min/max difficulty constraints
            question_type: Specific question type if required
            selection_strategy: Strategy to use for selection
            
        Returns:
            Dict with selected question details or None if no suitable question found
        """
        try:
            logger.info(f"Selecting next question for session {session_id}, theta={current_theta:.3f}")
            
            # Use default strategy if not specified
            if selection_strategy is None:
                selection_strategy = self.default_strategy
            
            # Create selection constraints
            constraints = SelectionConstraints(
                min_difficulty=difficulty_range[0],
                max_difficulty=difficulty_range[1],
                excluded_questions=answered_questions,
                required_technologies=technologies,
                preferred_question_types=[QuestionType(question_type)] if question_type else [],
                max_duration=600,  # 10 minutes default
                skill_areas=self._extract_skill_areas(job_role, technologies),
                avoid_similar_questions=True
            )
            
            # Load candidate question pool
            question_pool = await self._load_question_pool(constraints)
            if not question_pool:
                logger.warning("No questions found in pool")
                return None
            
            # Filter questions based on constraints
            filtered_questions = self._filter_questions(question_pool, constraints)
            if not filtered_questions:
                logger.warning("No questions left after filtering")
                return None
            
            # Calculate information value for each question
            scored_questions = await self._calculate_question_scores(
                filtered_questions,
                current_theta,
                standard_error,
                session_id,
                selection_strategy
            )
            
            # Apply bias prevention
            bias_filtered_questions = await self._apply_bias_prevention(
                scored_questions, 
                {
                    'job_role': job_role,
                    'experience_level': experience_level,
                    'technologies': technologies
                }
            )
            
            if not bias_filtered_questions:
                logger.warning("No questions left after bias filtering")
                return None
            
            # Select the best question
            selected_question = self._select_optimal_question(
                bias_filtered_questions, 
                selection_strategy
            )
            
            # Cache selection for analysis
            await self._cache_selection_decision(
                session_id, 
                selected_question, 
                current_theta,
                len(scored_questions)
            )
            
            return self._format_question_response(selected_question)
            
        except Exception as e:
            logger.error(f"Error selecting next question: {str(e)}")
            return None
    
    async def get_question_info(self, question_id: int) -> Optional[Dict[str, Any]]:
        """
        Get detailed information about a specific question
        
        Args:
            question_id: ID of the question
            
        Returns:
            Dict with question information or None if not found
        """
        try:
            # Check cache first
            cache_key = f"question_info:{question_id}"
            cached_info = self.redis_client.get(cache_key)
            
            if cached_info:
                return json.loads(cached_info)
            
            # Load from database
            conn = mysql.connector.connect(**self.db_config)
            cursor = conn.cursor(dictionary=True)
            
            cursor.execute("""
                SELECT q.*, qp.difficulty, qp.discrimination, qp.guessing_parameter,
                       qp.category, qp.technologies, qp.expected_duration_minutes,
                       qp.skill_areas, qp.question_type
                FROM questions q
                LEFT JOIN question_irt_parameters qp ON q.question_id = qp.question_id
                WHERE q.question_id = %s AND q.active = 1
            """, (question_id,))
            
            result = cursor.fetchone()
            cursor.close()
            conn.close()
            
            if result:
                # Convert JSON fields
                if result.get('technologies'):
                    result['technologies'] = json.loads(result['technologies'])
                if result.get('skill_areas'):
                    result['skill_areas'] = json.loads(result['skill_areas'])
                
                # Cache the result
                self.redis_client.setex(cache_key, self.cache_ttl, json.dumps(result, default=str))
                
                return result
            
            return None
            
        except Exception as e:
            logger.error(f"Error getting question info for {question_id}: {str(e)}")
            return None
    
    async def get_question_recommendations(
        self,
        session_id: str,
        current_theta: float,
        answered_questions: List[int],
        candidate_profile: Dict[str, Any],
        count: int = 5
    ) -> List[Dict[str, Any]]:
        """
        Get multiple question recommendations with scoring details
        
        Args:
            session_id: Interview session ID
            current_theta: Current ability estimate
            answered_questions: Already answered questions
            candidate_profile: Candidate profile information
            count: Number of recommendations to return
            
        Returns:
            List of recommended questions with scores
        """
        try:
            constraints = SelectionConstraints(
                min_difficulty=current_theta - 1.5,
                max_difficulty=current_theta + 1.5,
                excluded_questions=answered_questions,
                required_technologies=candidate_profile.get('technologies', []),
                preferred_question_types=[],
                max_duration=600,
                skill_areas=self._extract_skill_areas(
                    candidate_profile.get('job_role', ''),
                    candidate_profile.get('technologies', [])
                ),
                avoid_similar_questions=True
            )
            
            # Load and score questions
            question_pool = await self._load_question_pool(constraints)
            filtered_questions = self._filter_questions(question_pool, constraints)
            
            scored_questions = await self._calculate_question_scores(
                filtered_questions,
                current_theta,
                0.5,  # Default SE
                session_id,
                SelectionStrategy.MAXIMUM_INFORMATION
            )
            
            # Sort by selection score and return top N
            sorted_questions = sorted(
                scored_questions,
                key=lambda q: q.selection_score,
                reverse=True
            )
            
            recommendations = []
            for question in sorted_questions[:count]:
                recommendations.append({
                    'question_id': question.question_id,
                    'question_text': question.question_text,
                    'difficulty': question.difficulty,
                    'information_value': question.information_value,
                    'selection_score': question.selection_score,
                    'bias_score': question.bias_score,
                    'effectiveness_score': question.effectiveness_score,
                    'expected_duration': question.expected_duration_minutes
                })
            
            return recommendations
            
        except Exception as e:
            logger.error(f"Error getting question recommendations: {str(e)}")
            return []
    
    async def update_question_effectiveness(
        self,
        question_id: int,
        effectiveness_data: Dict[str, Any]
    ):
        """
        Update question effectiveness based on interview outcomes
        
        Args:
            question_id: ID of the question
            effectiveness_data: Data about question effectiveness
        """
        try:
            cache_key = f"question_effectiveness:{question_id}"
            
            # Get existing effectiveness data
            existing_data = self.redis_client.get(cache_key)
            if existing_data:
                existing = json.loads(existing_data)
            else:
                existing = {
                    'total_uses': 0,
                    'average_information_gain': 0.0,
                    'average_satisfaction': 0.0,
                    'bias_incidents': 0,
                    'effectiveness_trend': []
                }
            
            # Update with new data
            existing['total_uses'] += 1
            existing['average_information_gain'] = (
                (existing['average_information_gain'] * (existing['total_uses'] - 1) + 
                 effectiveness_data.get('information_gain', 0.0)) / existing['total_uses']
            )
            existing['average_satisfaction'] = (
                (existing['average_satisfaction'] * (existing['total_uses'] - 1) + 
                 effectiveness_data.get('satisfaction', 0.5)) / existing['total_uses']
            )
            
            if effectiveness_data.get('bias_detected', False):
                existing['bias_incidents'] += 1
            
            # Add to effectiveness trend (keep last 20 data points)
            existing['effectiveness_trend'].append({
                'timestamp': datetime.now().isoformat(),
                'information_gain': effectiveness_data.get('information_gain', 0.0),
                'satisfaction': effectiveness_data.get('satisfaction', 0.5)
            })
            
            if len(existing['effectiveness_trend']) > 20:
                existing['effectiveness_trend'] = existing['effectiveness_trend'][-20:]
            
            # Store updated data
            self.redis_client.setex(cache_key, 86400, json.dumps(existing, default=str))
            
            logger.debug(f"Updated effectiveness for question {question_id}")
            
        except Exception as e:
            logger.error(f"Error updating question effectiveness: {str(e)}")
    
    # Private methods
    
    async def _load_question_pool(self, constraints: SelectionConstraints) -> List[QuestionCandidate]:
        """Load questions from database that meet basic constraints"""
        try:
            # Check if we need to refresh the cache
            if (datetime.now() - self.last_cache_update) > self.cache_refresh_interval:
                await self._refresh_question_cache()
            
            conn = mysql.connector.connect(**self.db_config)
            cursor = conn.cursor(dictionary=True)
            
            # Build query based on constraints
            where_conditions = ["q.active = 1"]
            params = []
            
            # Difficulty range
            where_conditions.append("qp.difficulty BETWEEN %s AND %s")
            params.extend([constraints.min_difficulty, constraints.max_difficulty])
            
            # Exclude answered questions
            if constraints.excluded_questions:
                placeholders = ','.join(['%s'] * len(constraints.excluded_questions))
                where_conditions.append(f"q.question_id NOT IN ({placeholders})")
                params.extend(constraints.excluded_questions)
            
            # Technology requirements
            if constraints.required_technologies:
                tech_conditions = []
                for tech in constraints.required_technologies:
                    tech_conditions.append("JSON_CONTAINS(qp.technologies, %s)")
                    params.append(f'"{tech}"')
                if tech_conditions:
                    where_conditions.append(f"({' OR '.join(tech_conditions)})")
            
            # Question type preference
            if constraints.preferred_question_types:
                type_conditions = []
                for qtype in constraints.preferred_question_types:
                    type_conditions.append("qp.question_type = %s")
                    params.append(qtype.value)
                if type_conditions:
                    where_conditions.append(f"({' OR '.join(type_conditions)})")
            
            # Duration constraint
            if constraints.max_duration:
                where_conditions.append("qp.expected_duration_minutes <= %s")
                params.append(constraints.max_duration)
            
            query = f"""
                SELECT q.question_id, q.question_text, q.created_at,
                       qp.difficulty, qp.discrimination, qp.guessing_parameter,
                       qp.category, qp.technologies, qp.expected_duration_minutes,
                       qp.skill_areas, qp.question_type
                FROM questions q
                LEFT JOIN question_irt_parameters qp ON q.question_id = qp.question_id
                WHERE {' AND '.join(where_conditions)}
                ORDER BY qp.difficulty
                LIMIT %s
            """
            params.append(self.question_pool_size)
            
            cursor.execute(query, params)
            results = cursor.fetchall()
            
            cursor.close()
            conn.close()
            
            # Convert to QuestionCandidate objects
            question_candidates = []
            for row in results:
                # Parse JSON fields
                technologies = json.loads(row.get('technologies', '[]')) if row.get('technologies') else []
                skill_areas = json.loads(row.get('skill_areas', '[]')) if row.get('skill_areas') else []
                
                candidate = QuestionCandidate(
                    question_id=row['question_id'],
                    question_text=row['question_text'],
                    question_type=QuestionType(row.get('question_type', 'technical')),
                    difficulty=float(row.get('difficulty', 0.0)),
                    discrimination=float(row.get('discrimination', 1.0)),
                    guessing_parameter=float(row.get('guessing_parameter', 0.0)),
                    category=row.get('category', 'general'),
                    technologies=technologies,
                    expected_duration_minutes=int(row.get('expected_duration_minutes', 5)),
                    information_value=0.0,  # Will be calculated later
                    skill_coverage=skill_areas,
                    bias_score=0.0,  # Will be calculated later
                    effectiveness_score=0.0,  # Will be calculated later
                    selection_score=0.0  # Will be calculated later
                )
                question_candidates.append(candidate)
            
            logger.debug(f"Loaded {len(question_candidates)} questions into pool")
            return question_candidates
            
        except Exception as e:
            logger.error(f"Error loading question pool: {str(e)}")
            return []
    
    def _filter_questions(
        self, 
        questions: List[QuestionCandidate], 
        constraints: SelectionConstraints
    ) -> List[QuestionCandidate]:
        """Apply additional filtering to questions"""
        try:
            filtered = []
            
            for question in questions:
                # Skip if already answered
                if question.question_id in constraints.excluded_questions:
                    continue
                
                # Check difficulty range
                if (question.difficulty < constraints.min_difficulty or 
                    question.difficulty > constraints.max_difficulty):
                    continue
                
                # Check duration constraint
                if (constraints.max_duration and 
                    question.expected_duration_minutes > constraints.max_duration):
                    continue
                
                # Check technology requirements
                if constraints.required_technologies:
                    if not any(tech in question.technologies for tech in constraints.required_technologies):
                        continue
                
                # Check preferred question types
                if (constraints.preferred_question_types and 
                    question.question_type not in constraints.preferred_question_types):
                    continue
                
                # Check skill area overlap
                if constraints.skill_areas:
                    if not any(skill in question.skill_coverage for skill in constraints.skill_areas):
                        continue
                
                filtered.append(question)
            
            logger.debug(f"Filtered to {len(filtered)} questions")
            return filtered
            
        except Exception as e:
            logger.error(f"Error filtering questions: {str(e)}")
            return questions
    
    async def _calculate_question_scores(
        self,
        questions: List[QuestionCandidate],
        current_theta: float,
        standard_error: float,
        session_id: str,
        strategy: SelectionStrategy
    ) -> List[QuestionCandidate]:
        """Calculate selection scores for all questions"""
        try:
            from irt_engine import IRTEngine
            irt_engine = IRTEngine()
            
            for question in questions:
                # Calculate information value using IRT
                question.information_value = irt_engine.calculate_question_information(
                    theta=current_theta,
                    question_difficulty=question.difficulty,
                    question_discrimination=question.discrimination,
                    model='2PL'
                )
                
                # Get effectiveness score from cache/learning module
                question.effectiveness_score = await self._get_effectiveness_score(question.question_id)
                
                # Calculate bias score (lower is better)
                question.bias_score = await self._get_bias_score(question.question_id)
                
                # Calculate overall selection score based on strategy
                question.selection_score = self._calculate_selection_score(
                    question, current_theta, strategy
                )
            
            return questions
            
        except Exception as e:
            logger.error(f"Error calculating question scores: {str(e)}")
            return questions
    
    def _calculate_selection_score(
        self, 
        question: QuestionCandidate, 
        current_theta: float,
        strategy: SelectionStrategy
    ) -> float:
        """Calculate the overall selection score for a question"""
        try:
            if strategy == SelectionStrategy.MAXIMUM_INFORMATION:
                # Prioritize information gain
                return (
                    question.information_value * 0.7 +
                    question.effectiveness_score * 0.2 +
                    (1.0 - question.bias_score) * 0.1
                )
            
            elif strategy == SelectionStrategy.TARGETED_DIFFICULTY:
                # Prioritize questions near current theta
                difficulty_match = 1.0 - abs(question.difficulty - current_theta) / 3.0
                difficulty_match = max(0.0, difficulty_match)
                
                return (
                    difficulty_match * 0.5 +
                    question.information_value * 0.3 +
                    question.effectiveness_score * 0.1 +
                    (1.0 - question.bias_score) * 0.1
                )
            
            elif strategy == SelectionStrategy.BALANCED_COVERAGE:
                # Balance information, difficulty, and skill coverage
                difficulty_match = 1.0 - abs(question.difficulty - current_theta) / 3.0
                difficulty_match = max(0.0, difficulty_match)
                
                return (
                    question.information_value * self.information_weight +
                    difficulty_match * self.difficulty_match_weight +
                    question.effectiveness_score * self.skill_coverage_weight +
                    (1.0 - question.bias_score) * self.bias_penalty_weight
                )
            
            elif strategy == SelectionStrategy.SKILL_EXPLORATION:
                # Focus on covering different skill areas
                skill_coverage_score = len(question.skill_coverage) / 5.0  # Normalize
                skill_coverage_score = min(1.0, skill_coverage_score)
                
                return (
                    skill_coverage_score * 0.4 +
                    question.information_value * 0.3 +
                    question.effectiveness_score * 0.2 +
                    (1.0 - question.bias_score) * 0.1
                )
            
            else:  # ADAPTIVE_HYBRID
                # Dynamic weighting based on interview progress
                difficulty_match = 1.0 - abs(question.difficulty - current_theta) / 3.0
                difficulty_match = max(0.0, difficulty_match)
                
                # Adjust weights based on current state
                info_weight = 0.4
                diff_weight = 0.3
                eff_weight = 0.2
                bias_weight = 0.1
                
                return (
                    question.information_value * info_weight +
                    difficulty_match * diff_weight +
                    question.effectiveness_score * eff_weight +
                    (1.0 - question.bias_score) * bias_weight
                )
                
        except Exception as e:
            logger.error(f"Error calculating selection score: {str(e)}")
            return 0.0
    
    async def _apply_bias_prevention(
        self,
        questions: List[QuestionCandidate],
        candidate_profile: Dict[str, Any]
    ) -> List[QuestionCandidate]:
        """Apply bias prevention filtering"""
        try:
            # Filter out questions with high bias scores
            bias_threshold = 0.3
            filtered_questions = [
                q for q in questions 
                if q.bias_score < bias_threshold
            ]
            
            # If too many questions filtered, relax threshold
            if len(filtered_questions) < len(questions) * 0.3:
                bias_threshold = 0.5
                filtered_questions = [
                    q for q in questions 
                    if q.bias_score < bias_threshold
                ]
            
            logger.debug(f"Bias prevention filtered {len(questions)} -> {len(filtered_questions)} questions")
            return filtered_questions
            
        except Exception as e:
            logger.error(f"Error applying bias prevention: {str(e)}")
            return questions
    
    def _select_optimal_question(
        self,
        questions: List[QuestionCandidate],
        strategy: SelectionStrategy
    ) -> QuestionCandidate:
        """Select the optimal question from scored candidates"""
        try:
            if not questions:
                return None
            
            # Sort by selection score
            sorted_questions = sorted(
                questions,
                key=lambda q: q.selection_score,
                reverse=True
            )
            
            # Add some randomness to prevent predictable patterns
            if strategy == SelectionStrategy.ADAPTIVE_HYBRID:
                # Select from top 3 questions with weighted probability
                top_questions = sorted_questions[:min(3, len(sorted_questions))]
                weights = [q.selection_score for q in top_questions]
                
                if sum(weights) > 0:
                    # Normalize weights
                    total_weight = sum(weights)
                    probabilities = [w / total_weight for w in weights]
                    
                    # Weighted random selection
                    selected_idx = np.random.choice(len(top_questions), p=probabilities)
                    return top_questions[selected_idx]
            
            # Return highest scoring question
            return sorted_questions[0]
            
        except Exception as e:
            logger.error(f"Error selecting optimal question: {str(e)}")
            return questions[0] if questions else None
    
    def _format_question_response(self, question: QuestionCandidate) -> Dict[str, Any]:
        """Format question for API response"""
        return {
            'question_id': question.question_id,
            'question_text': question.question_text,
            'question_type': question.question_type.value,
            'difficulty': question.difficulty,
            'discrimination': question.discrimination,
            'category': question.category,
            'technologies': question.technologies,
            'expected_duration_minutes': question.expected_duration_minutes,
            'coding_required': question.question_type == QuestionType.CODING,
            'multi_part': False,  # Could be enhanced
            'followup_questions': [],  # Could be enhanced
            'confidence_score': min(question.selection_score, 1.0),
            'selection_reason': self._generate_selection_reason(question)
        }
    
    def _generate_selection_reason(self, question: QuestionCandidate) -> str:
        """Generate human-readable selection reason"""
        reasons = []
        
        if question.information_value > 0.7:
            reasons.append("high information value")
        if question.effectiveness_score > 0.8:
            reasons.append("proven effectiveness")
        if question.bias_score < 0.1:
            reasons.append("low bias risk")
        if len(question.skill_coverage) > 2:
            reasons.append("broad skill coverage")
        
        if reasons:
            return f"Selected for {', '.join(reasons)}"
        else:
            return "Selected based on adaptive algorithm"
    
    async def _get_effectiveness_score(self, question_id: int) -> float:
        """Get effectiveness score from cache/learning module"""
        try:
            cache_key = f"question_effectiveness:{question_id}"
            effectiveness_data = self.redis_client.get(cache_key)
            
            if effectiveness_data:
                data = json.loads(effectiveness_data)
                return data.get('average_information_gain', 0.5)
            else:
                # Default effectiveness for new questions
                return 0.5
                
        except Exception as e:
            logger.error(f"Error getting effectiveness score: {str(e)}")
            return 0.5
    
    async def _get_bias_score(self, question_id: int) -> float:
        """Get bias score for question"""
        try:
            cache_key = f"question_bias_score:{question_id}"
            bias_data = self.redis_client.get(cache_key)
            
            if bias_data:
                data = json.loads(bias_data)
                return data.get('overall_bias_score', 0.1)
            else:
                # Default low bias for new questions
                return 0.1
                
        except Exception as e:
            logger.error(f"Error getting bias score: {str(e)}")
            return 0.1
    
    def _extract_skill_areas(self, job_role: str, technologies: List[str]) -> List[str]:
        """Extract relevant skill areas from job role and technologies"""
        skill_areas = []
        
        # Add technology-based skills
        skill_areas.extend(technologies)
        
        # Add role-based skills
        role_skills = {
            'backend_engineer': ['api_design', 'database_design', 'scalability', 'security'],
            'frontend_engineer': ['ui_design', 'user_experience', 'responsive_design', 'performance'],
            'fullstack_engineer': ['api_design', 'database_design', 'ui_design', 'integration'],
            'data_engineer': ['data_modeling', 'etl_processes', 'big_data', 'analytics'],
            'devops_engineer': ['ci_cd', 'containerization', 'monitoring', 'infrastructure']
        }
        
        role_key = job_role.lower().replace(' ', '_')
        if role_key in role_skills:
            skill_areas.extend(role_skills[role_key])
        
        return list(set(skill_areas))  # Remove duplicates
    
    async def _cache_selection_decision(
        self,
        session_id: str,
        selected_question: QuestionCandidate,
        current_theta: float,
        pool_size: int
    ):
        """Cache the selection decision for analysis"""
        try:
            cache_key = f"selection_decision:{session_id}:{selected_question.question_id}"
            decision_data = {
                'question_id': selected_question.question_id,
                'selection_score': selected_question.selection_score,
                'information_value': selected_question.information_value,
                'effectiveness_score': selected_question.effectiveness_score,
                'bias_score': selected_question.bias_score,
                'current_theta': current_theta,
                'pool_size': pool_size,
                'selected_at': datetime.now().isoformat()
            }
            
            self.redis_client.setex(cache_key, 86400, json.dumps(decision_data, default=str))
            
        except Exception as e:
            logger.error(f"Error caching selection decision: {str(e)}")
    
    async def _refresh_question_cache(self):
        """Refresh the question cache"""
        try:
            logger.info("Refreshing question cache")
            # This would refresh cached question data
            self.last_cache_update = datetime.now()
            
        except Exception as e:
            logger.error(f"Error refreshing question cache: {str(e)}")
    
    # Additional utility methods
    
    async def get_selection_analytics(self, session_id: str) -> Dict[str, Any]:
        """Get analytics about question selection for a session"""
        try:
            # Retrieve all selection decisions for the session
            pattern = f"selection_decision:{session_id}:*"
            keys = self.redis_client.keys(pattern)
            
            decisions = []
            for key in keys:
                data = self.redis_client.get(key)
                if data:
                    decisions.append(json.loads(data))
            
            if not decisions:
                return {"status": "no_data"}
            
            # Calculate analytics
            avg_information = np.mean([d['information_value'] for d in decisions])
            avg_effectiveness = np.mean([d['effectiveness_score'] for d in decisions])
            avg_bias = np.mean([d['bias_score'] for d in decisions])
            
            return {
                "total_selections": len(decisions),
                "average_information_value": avg_information,
                "average_effectiveness": avg_effectiveness,
                "average_bias_score": avg_bias,
                "selection_quality": (avg_information + avg_effectiveness + (1.0 - avg_bias)) / 3.0
            }
            
        except Exception as e:
            logger.error(f"Error getting selection analytics: {str(e)}")
            return {"status": "error", "message": str(e)}
    
    async def optimize_question_pool(self) -> Dict[str, Any]:
        """Optimize the question pool based on usage patterns"""
        try:
            logger.info("Optimizing question pool")
            
            # This would implement question pool optimization
            # - Identify underperforming questions
            # - Suggest new questions for gaps
            # - Update IRT parameters based on performance
            
            return {
                "status": "completed",
                "optimizations_applied": 0,
                "recommendations": []
            }
            
        except Exception as e:
            logger.error(f"Error optimizing question pool: {str(e)}")
            return {"status": "error", "message": str(e)}
