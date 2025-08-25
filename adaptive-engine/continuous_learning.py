#!/usr/bin/env python3
"""
Continuous Learning Module for ARIA Adaptive Interview Platform
Implements machine learning algorithms to continuously improve question selection
and effectiveness based on interview outcomes and candidate feedback.
"""

import asyncio
import numpy as np
import logging
from typing import Dict, List, Optional, Any, Tuple
from datetime import datetime, timedelta
from dataclasses import dataclass
from sklearn.ensemble import RandomForestRegressor, GradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import cross_val_score
import joblib
import json
import redis
import mysql.connector
from contextlib import asynccontextmanager

logger = logging.getLogger(__name__)

@dataclass
class QuestionEffectivenessMetric:
    """Metrics for evaluating question effectiveness"""
    question_id: int
    information_gain: float
    discrimination_accuracy: float
    bias_score: float
    candidate_satisfaction: float
    time_efficiency: float
    skill_coverage: float
    overall_score: float

@dataclass
class InterviewOutcome:
    """Complete interview outcome data"""
    session_id: str
    candidate_id: int
    final_theta: float
    confidence_level: float
    total_questions: int
    interview_duration: int
    candidate_feedback: Dict[str, Any]
    recruiter_rating: Optional[float]
    hire_decision: Optional[bool]
    actual_performance: Optional[float]  # Post-hire performance if available

class ContinuousLearningModule:
    """
    Continuous Learning Module that improves the adaptive questioning system
    through machine learning and feedback analysis
    """
    
    def __init__(self):
        """Initialize the continuous learning module"""
        self.redis_client = redis.Redis(host='localhost', port=6379, db=1, decode_responses=True)
        
        # Machine learning models
        self.question_effectiveness_model = RandomForestRegressor(
            n_estimators=100, 
            random_state=42,
            max_depth=10
        )
        self.difficulty_calibration_model = GradientBoostingClassifier(
            n_estimators=100,
            random_state=42
        )
        self.bias_prediction_model = RandomForestRegressor(
            n_estimators=50,
            random_state=42
        )
        
        # Feature scalers
        self.effectiveness_scaler = StandardScaler()
        self.difficulty_scaler = StandardScaler()
        self.bias_scaler = StandardScaler()
        
        # Learning parameters
        self.learning_batch_size = 50
        self.model_update_frequency = timedelta(hours=6)
        self.min_data_points = 30
        
        # Cached learning data
        self.learning_buffer = []
        self.last_model_update = datetime.now()
        
        # Database connection
        self.db_config = {
            'host': 'localhost',
            'user': 'aria_user',
            'password': 'aria_password',
            'database': 'aria_interviews',
            'autocommit': True
        }
        
        logger.info("Continuous Learning Module initialized with ML models")
    
    async def start_continuous_learning(self):
        """Start the continuous learning background task"""
        try:
            logger.info("Starting continuous learning background task")
            
            while True:
                try:
                    # Load and process new learning data
                    await self._process_learning_buffer()
                    
                    # Update models if enough data available
                    if self._should_update_models():
                        await self._update_models()
                    
                    # Clean up old data
                    await self._cleanup_old_data()
                    
                    # Sleep for 30 minutes before next cycle
                    await asyncio.sleep(1800)
                    
                except Exception as e:
                    logger.error(f"Error in continuous learning cycle: {str(e)}")
                    await asyncio.sleep(300)  # Sleep 5 minutes on error
                    
        except asyncio.CancelledError:
            logger.info("Continuous learning task cancelled")
            raise
        except Exception as e:
            logger.error(f"Fatal error in continuous learning: {str(e)}")
    
    async def process_interview_outcome(
        self,
        session_id: str,
        outcome_data: Dict[str, Any],
        question_effectiveness: List[Dict[str, Any]],
        bias_incidents: List[Dict[str, Any]] = None,
        conversation_patterns: List[Dict[str, Any]] = None
    ):
        """Process completed interview outcome for learning"""
        try:
            logger.info(f"Processing interview outcome for session {session_id}")
            
            # Create structured outcome object
            interview_outcome = InterviewOutcome(
                session_id=session_id,
                candidate_id=outcome_data.get('candidate_id'),
                final_theta=outcome_data.get('final_theta', 0.0),
                confidence_level=outcome_data.get('confidence_level', 0.0),
                total_questions=outcome_data.get('total_questions', 0),
                interview_duration=outcome_data.get('interview_duration', 0),
                candidate_feedback=outcome_data.get('candidate_feedback', {}),
                recruiter_rating=outcome_data.get('recruiter_rating'),
                hire_decision=outcome_data.get('hire_decision'),
                actual_performance=outcome_data.get('actual_performance')
            )
            
            # Process question effectiveness metrics
            effectiveness_metrics = []
            for q_data in question_effectiveness:
                metric = QuestionEffectivenessMetric(
                    question_id=q_data['question_id'],
                    information_gain=q_data.get('information_gain', 0.0),
                    discrimination_accuracy=q_data.get('discrimination_accuracy', 0.0),
                    bias_score=q_data.get('bias_score', 0.0),
                    candidate_satisfaction=q_data.get('candidate_satisfaction', 0.5),
                    time_efficiency=q_data.get('time_efficiency', 0.0),
                    skill_coverage=q_data.get('skill_coverage', 0.0),
                    overall_score=0.0  # Will be calculated
                )
                # Calculate overall effectiveness score
                metric.overall_score = self._calculate_overall_effectiveness(metric)
                effectiveness_metrics.append(metric)
            
            # Store learning data
            learning_data = {
                'interview_outcome': interview_outcome,
                'question_effectiveness': effectiveness_metrics,
                'bias_incidents': bias_incidents or [],
                'conversation_patterns': conversation_patterns or [],
                'processed_at': datetime.now().isoformat()
            }
            
            # Add to learning buffer
            self.learning_buffer.append(learning_data)
            
            # Store in Redis for immediate access
            cache_key = f"learning_data:{session_id}"
            self.redis_client.setex(
                cache_key,
                86400,  # 24 hours TTL
                json.dumps(learning_data, default=str)
            )
            
            # Update question effectiveness immediately
            await self._update_question_effectiveness(effectiveness_metrics)
            
            logger.info(f"Interview outcome processed for session {session_id}")
            
        except Exception as e:
            logger.error(f"Error processing interview outcome: {str(e)}")
            raise
    
    async def get_question_effectiveness_prediction(
        self,
        question_id: int,
        candidate_profile: Dict[str, Any],
        interview_context: Dict[str, Any]
    ) -> float:
        """Predict effectiveness of a question for a specific candidate"""
        try:
            # Extract features for prediction
            features = self._extract_effectiveness_features(
                question_id, candidate_profile, interview_context
            )
            
            if len(features) == 0:
                return 0.5  # Default effectiveness
            
            # Scale features
            if hasattr(self.effectiveness_scaler, 'scale_'):
                features_scaled = self.effectiveness_scaler.transform([features])
                
                # Predict effectiveness
                effectiveness = self.question_effectiveness_model.predict(features_scaled)[0]
                return max(0.0, min(1.0, effectiveness))
            else:
                # Model not trained yet, return default
                return 0.5
                
        except Exception as e:
            logger.error(f"Error predicting question effectiveness: {str(e)}")
            return 0.5
    
    async def get_bias_risk_prediction(
        self,
        question_id: int,
        candidate_profile: Dict[str, Any]
    ) -> Dict[str, float]:
        """Predict bias risk for a question-candidate combination"""
        try:
            # Extract bias-related features
            features = self._extract_bias_features(question_id, candidate_profile)
            
            if len(features) == 0:
                return {'overall_risk': 0.1, 'demographic_risk': 0.1, 'skill_bias': 0.1}
            
            # Scale features and predict
            if hasattr(self.bias_scaler, 'scale_'):
                features_scaled = self.bias_scaler.transform([features])
                bias_risk = self.bias_prediction_model.predict(features_scaled)[0]
                
                return {
                    'overall_risk': max(0.0, min(1.0, bias_risk)),
                    'demographic_risk': max(0.0, min(1.0, bias_risk * 0.8)),
                    'skill_bias': max(0.0, min(1.0, bias_risk * 1.2))
                }
            else:
                return {'overall_risk': 0.1, 'demographic_risk': 0.1, 'skill_bias': 0.1}
                
        except Exception as e:
            logger.error(f"Error predicting bias risk: {str(e)}")
            return {'overall_risk': 0.1, 'demographic_risk': 0.1, 'skill_bias': 0.1}
    
    async def get_adaptive_recommendations(
        self,
        session_id: str,
        current_state: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Get adaptive recommendations based on learned patterns"""
        try:
            # Retrieve similar interview patterns
            similar_patterns = await self._find_similar_interview_patterns(current_state)
            
            # Generate recommendations
            recommendations = {
                'suggested_difficulty_adjustment': self._calculate_difficulty_adjustment(current_state, similar_patterns),
                'recommended_question_types': self._recommend_question_types(current_state, similar_patterns),
                'interview_pacing': self._recommend_pacing(current_state, similar_patterns),
                'early_termination_probability': self._calculate_termination_probability(current_state),
                'confidence_improvement_suggestions': self._suggest_confidence_improvements(current_state)
            }
            
            return recommendations
            
        except Exception as e:
            logger.error(f"Error generating adaptive recommendations: {str(e)}")
            return {}
    
    # Private methods
    
    def _calculate_overall_effectiveness(self, metric: QuestionEffectivenessMetric) -> float:
        """Calculate overall effectiveness score from individual metrics"""
        weights = {
            'information_gain': 0.25,
            'discrimination_accuracy': 0.20,
            'bias_score': -0.15,  # Negative because lower bias is better
            'candidate_satisfaction': 0.15,
            'time_efficiency': 0.15,
            'skill_coverage': 0.20
        }
        
        score = (
            weights['information_gain'] * metric.information_gain +
            weights['discrimination_accuracy'] * metric.discrimination_accuracy +
            weights['bias_score'] * (1.0 - metric.bias_score) +  # Invert bias score
            weights['candidate_satisfaction'] * metric.candidate_satisfaction +
            weights['time_efficiency'] * metric.time_efficiency +
            weights['skill_coverage'] * metric.skill_coverage
        )
        
        return max(0.0, min(1.0, score))
    
    async def _process_learning_buffer(self):
        """Process accumulated learning data"""
        if len(self.learning_buffer) < self.learning_batch_size:
            return
        
        try:
            # Process batch of learning data
            batch = self.learning_buffer[:self.learning_batch_size]
            self.learning_buffer = self.learning_buffer[self.learning_batch_size:]
            
            # Store in database
            await self._store_learning_batch(batch)
            
            logger.info(f"Processed learning batch of {len(batch)} interviews")
            
        except Exception as e:
            logger.error(f"Error processing learning buffer: {str(e)}")
    
    def _should_update_models(self) -> bool:
        """Check if models should be updated"""
        time_since_update = datetime.now() - self.last_model_update
        return time_since_update >= self.model_update_frequency
    
    async def _update_models(self):
        """Update machine learning models with new data"""
        try:
            logger.info("Updating continuous learning models")
            
            # Load training data from database
            training_data = await self._load_training_data()
            
            if len(training_data) < self.min_data_points:
                logger.warning(f"Insufficient training data: {len(training_data)} < {self.min_data_points}")
                return
            
            # Update question effectiveness model
            await self._update_effectiveness_model(training_data)
            
            # Update difficulty calibration model
            await self._update_difficulty_model(training_data)
            
            # Update bias prediction model
            await self._update_bias_model(training_data)
            
            self.last_model_update = datetime.now()
            
            # Save models
            await self._save_models()
            
            logger.info("Models updated successfully")
            
        except Exception as e:
            logger.error(f"Error updating models: {str(e)}")
    
    async def _update_effectiveness_model(self, training_data: List[Dict]):
        """Update the question effectiveness prediction model"""
        try:
            X, y = [], []
            
            for data in training_data:
                for metric in data['question_effectiveness']:
                    features = self._extract_effectiveness_features(
                        metric.question_id,
                        data['candidate_profile'],
                        data['interview_context']
                    )
                    if len(features) > 0:
                        X.append(features)
                        y.append(metric.overall_score)
            
            if len(X) < self.min_data_points:
                return
            
            # Fit scaler and model
            X_scaled = self.effectiveness_scaler.fit_transform(X)
            self.question_effectiveness_model.fit(X_scaled, y)
            
            # Evaluate model
            cv_scores = cross_val_score(self.question_effectiveness_model, X_scaled, y, cv=5)
            logger.info(f"Effectiveness model CV score: {cv_scores.mean():.3f} ± {cv_scores.std():.3f}")
            
        except Exception as e:
            logger.error(f"Error updating effectiveness model: {str(e)}")
    
    def _extract_effectiveness_features(
        self,
        question_id: int,
        candidate_profile: Dict[str, Any],
        interview_context: Dict[str, Any]
    ) -> List[float]:
        """Extract features for effectiveness prediction"""
        try:
            features = []
            
            # Question features (would be loaded from database)
            features.extend([
                question_id % 1000 / 1000.0,  # Normalized question ID
                # Add more question-specific features here
            ])
            
            # Candidate features
            features.extend([
                candidate_profile.get('experience_years', 0) / 20.0,  # Normalized experience
                len(candidate_profile.get('technologies', [])) / 10.0,  # Normalized tech count
                hash(candidate_profile.get('job_role', '')) % 100 / 100.0,  # Hashed role
            ])
            
            # Interview context features
            features.extend([
                interview_context.get('current_theta', 0.0) / 3.0,  # Normalized theta
                interview_context.get('questions_asked', 0) / 20.0,  # Normalized question count
                interview_context.get('time_elapsed', 0) / 3600.0,  # Normalized time (hours)
            ])
            
            return features
            
        except Exception as e:
            logger.error(f"Error extracting effectiveness features: {str(e)}")
            return []
    
    def _extract_bias_features(
        self,
        question_id: int,
        candidate_profile: Dict[str, Any]
    ) -> List[float]:
        """Extract features for bias prediction"""
        try:
            features = []
            
            # Question characteristics
            features.extend([
                question_id % 1000 / 1000.0,
            ])
            
            # Demographic features (encoded to protect privacy)
            demographics = candidate_profile.get('demographics', {})
            features.extend([
                hash(str(demographics.get('region', ''))) % 100 / 100.0,
                demographics.get('experience_level_numeric', 0) / 5.0,
            ])
            
            return features
            
        except Exception as e:
            logger.error(f"Error extracting bias features: {str(e)}")
            return []
    
    async def _store_learning_batch(self, batch: List[Dict]):
        """Store learning batch in database"""
        try:
            conn = mysql.connector.connect(**self.db_config)
            cursor = conn.cursor()
            
            for data in batch:
                # Store interview outcome
                interview_outcome = data['interview_outcome']
                cursor.execute("""
                    INSERT INTO learning_interview_outcomes 
                    (session_id, candidate_id, final_theta, confidence_level, 
                     total_questions, interview_duration, candidate_feedback,
                     recruiter_rating, hire_decision, actual_performance, processed_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """, (
                    interview_outcome.session_id,
                    interview_outcome.candidate_id,
                    interview_outcome.final_theta,
                    interview_outcome.confidence_level,
                    interview_outcome.total_questions,
                    interview_outcome.interview_duration,
                    json.dumps(interview_outcome.candidate_feedback),
                    interview_outcome.recruiter_rating,
                    interview_outcome.hire_decision,
                    interview_outcome.actual_performance,
                    data['processed_at']
                ))
                
                # Store question effectiveness metrics
                for metric in data['question_effectiveness']:
                    cursor.execute("""
                        INSERT INTO learning_question_effectiveness
                        (session_id, question_id, information_gain, discrimination_accuracy,
                         bias_score, candidate_satisfaction, time_efficiency, skill_coverage,
                         overall_score, processed_at)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """, (
                        interview_outcome.session_id,
                        metric.question_id,
                        metric.information_gain,
                        metric.discrimination_accuracy,
                        metric.bias_score,
                        metric.candidate_satisfaction,
                        metric.time_efficiency,
                        metric.skill_coverage,
                        metric.overall_score,
                        data['processed_at']
                    ))
            
            conn.commit()
            cursor.close()
            conn.close()
            
        except Exception as e:
            logger.error(f"Error storing learning batch: {str(e)}")
            raise
    
    async def _load_training_data(self) -> List[Dict]:
        """Load training data from database"""
        try:
            # Implementation would load from database
            # For now, return empty list
            return []
            
        except Exception as e:
            logger.error(f"Error loading training data: {str(e)}")
            return []
    
    async def _update_question_effectiveness(self, metrics: List[QuestionEffectivenessMetric]):
        """Update question effectiveness scores in real-time"""
        try:
            for metric in metrics:
                cache_key = f"question_effectiveness:{metric.question_id}"
                effectiveness_data = {
                    'overall_score': metric.overall_score,
                    'information_gain': metric.information_gain,
                    'discrimination_accuracy': metric.discrimination_accuracy,
                    'bias_score': metric.bias_score,
                    'updated_at': datetime.now().isoformat()
                }
                
                self.redis_client.setex(
                    cache_key,
                    3600 * 24,  # 24 hours TTL
                    json.dumps(effectiveness_data)
                )
                
        except Exception as e:
            logger.error(f"Error updating question effectiveness: {str(e)}")
    
    async def _cleanup_old_data(self):
        """Clean up old cached data"""
        try:
            # Clean up old learning data from Redis
            # Implementation would clean keys older than certain threshold
            pass
            
        except Exception as e:
            logger.error(f"Error cleaning up old data: {str(e)}")
    
    async def _save_models(self):
        """Save trained models to disk"""
        try:
            import os
            os.makedirs('models', exist_ok=True)
            
            joblib.dump(self.question_effectiveness_model, 'models/effectiveness_model.pkl')
            joblib.dump(self.difficulty_calibration_model, 'models/difficulty_model.pkl')
            joblib.dump(self.bias_prediction_model, 'models/bias_model.pkl')
            
            joblib.dump(self.effectiveness_scaler, 'models/effectiveness_scaler.pkl')
            joblib.dump(self.difficulty_scaler, 'models/difficulty_scaler.pkl')
            joblib.dump(self.bias_scaler, 'models/bias_scaler.pkl')
            
            logger.info("Models saved successfully")
            
        except Exception as e:
            logger.error(f"Error saving models: {str(e)}")
    
    async def _find_similar_interview_patterns(self, current_state: Dict[str, Any]) -> List[Dict]:
        """Find similar interview patterns for recommendations"""
        # Implementation would query database for similar patterns
        return []
    
    def _calculate_difficulty_adjustment(self, current_state: Dict, similar_patterns: List[Dict]) -> float:
        """Calculate recommended difficulty adjustment"""
        # Default adjustment based on current theta and confidence
        current_theta = current_state.get('current_theta', 0.0)
        confidence = current_state.get('confidence_level', 0.5)
        
        if confidence > 0.8:
            return 0.2  # Increase difficulty
        elif confidence < 0.3:
            return -0.2  # Decrease difficulty
        else:
            return 0.0  # No adjustment
    
    def _recommend_question_types(self, current_state: Dict, similar_patterns: List[Dict]) -> List[str]:
        """Recommend question types based on current state"""
        # Default recommendations
        return ['technical', 'problem_solving', 'coding']
    
    def _recommend_pacing(self, current_state: Dict, similar_patterns: List[Dict]) -> Dict[str, Any]:
        """Recommend interview pacing"""
        return {
            'suggested_questions_remaining': max(3, 15 - current_state.get('questions_asked', 0)),
            'time_per_question_minutes': 3.0,
            'should_accelerate': current_state.get('time_elapsed', 0) > 2400  # 40 minutes
        }
    
    def _calculate_termination_probability(self, current_state: Dict) -> float:
        """Calculate probability that interview should terminate"""
        confidence = current_state.get('confidence_level', 0.5)
        questions_asked = current_state.get('questions_asked', 0)
        time_elapsed = current_state.get('time_elapsed', 0)
        
        # High confidence and sufficient questions suggest termination
        if confidence > 0.9 and questions_asked >= 8:
            return 0.8
        elif confidence > 0.7 and questions_asked >= 12:
            return 0.6
        elif time_elapsed > 2700:  # 45 minutes
            return 0.7
        else:
            return 0.1
    
    def _suggest_confidence_improvements(self, current_state: Dict) -> List[str]:
        """Suggest ways to improve confidence in ability estimate"""
        suggestions = []
        confidence = current_state.get('confidence_level', 0.5)
        
        if confidence < 0.5:
            suggestions.append("Ask questions closer to current estimated ability")
            suggestions.append("Focus on areas with highest discrimination")
        
        if current_state.get('questions_asked', 0) < 8:
            suggestions.append("Continue with adaptive questioning")
        
        return suggestions
    
    async def _update_difficulty_model(self, training_data: List[Dict]):
        """Update difficulty calibration model"""
        # Placeholder implementation
        pass
    
    async def _update_bias_model(self, training_data: List[Dict]):
        """Update bias prediction model"""
        # Placeholder implementation
        pass
