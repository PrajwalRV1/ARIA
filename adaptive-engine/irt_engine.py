#!/usr/bin/env python3
"""
Item Response Theory (IRT) Engine for ARIA Adaptive Interview Platform
Implements 2-parameter and 3-parameter logistic models for ability estimation
with support for partial credit scoring and response quality assessment.
"""

import numpy as np
import scipy.optimize as opt
from scipy.stats import norm
from typing import Dict, List, Optional, Tuple, Any
import logging
import math

logger = logging.getLogger(__name__)

class IRTEngine:
    """
    Advanced IRT Engine supporting multiple models and adaptive estimation
    """
    
    def __init__(self):
        """Initialize IRT Engine with default parameters"""
        self.models = {
            '1PL': self._rasch_model,
            '2PL': self._two_param_model,
            '3PL': self._three_param_model,
            'GRM': self._graded_response_model  # For partial credit items
        }
        
        # Default parameters
        self.default_discrimination = 1.0
        self.default_guessing = 0.0
        self.max_iterations = 50
        self.convergence_threshold = 0.001
        
        logger.info("IRT Engine initialized with support for 1PL, 2PL, 3PL, and GRM models")
    
    def update_theta(
        self,
        current_theta: float,
        current_se: float,
        question_difficulty: float,
        question_discrimination: float = None,
        response_correct: bool = None,
        partial_credit: float = None,
        response_quality_score: float = None,
        model: str = '2PL'
    ) -> Dict[str, float]:
        """
        Update candidate's ability estimate (theta) based on response
        
        Args:
            current_theta: Current ability estimate
            current_se: Current standard error
            question_difficulty: Question difficulty parameter (b)
            question_discrimination: Question discrimination parameter (a)
            response_correct: Binary response (True/False) for dichotomous items
            partial_credit: Partial credit score (0.0-1.0) for polytomous items
            response_quality_score: AI-assessed response quality (0.0-1.0)
            model: IRT model to use ('1PL', '2PL', '3PL', 'GRM')
        
        Returns:
            Dict with new theta, standard error, theta change, and other metrics
        """
        try:
            # Validate inputs
            if question_discrimination is None:
                question_discrimination = self.default_discrimination
            
            # Determine response score
            if partial_credit is not None:
                response_score = partial_credit
            elif response_correct is not None:
                response_score = 1.0 if response_correct else 0.0
            else:
                # Use AI quality score as proxy
                response_score = response_quality_score if response_quality_score is not None else 0.5
            
            # Apply response quality adjustment
            if response_quality_score is not None:
                response_score = self._adjust_for_quality(response_score, response_quality_score)
            
            # Calculate new theta using Maximum Likelihood Estimation (MLE)
            new_theta = self._calculate_mle_theta(
                current_theta=current_theta,
                question_difficulty=question_difficulty,
                question_discrimination=question_discrimination,
                response_score=response_score,
                model=model
            )
            
            # Calculate new standard error
            new_se = self._calculate_standard_error(
                theta=new_theta,
                question_difficulty=question_difficulty,
                question_discrimination=question_discrimination,
                model=model
            )
            
            # Combine with previous standard error using Bayesian updating
            combined_se = self._bayesian_se_update(current_se, new_se)
            
            # Calculate theta change and confidence metrics
            theta_change = new_theta - current_theta
            
            result = {
                'new_theta': new_theta,
                'standard_error': combined_se,
                'theta_change': theta_change,
                'information_gain': 1.0 / combined_se**2 if combined_se > 0 else float('inf'),
                'confidence_interval_95': self._calculate_confidence_interval(new_theta, combined_se),
                'model_used': model,
                'convergence_achieved': abs(theta_change) < self.convergence_threshold
            }
            
            logger.debug(f"Theta updated: {current_theta:.3f} → {new_theta:.3f} "
                        f"(SE: {current_se:.3f} → {combined_se:.3f})")
            
            return result
            
        except Exception as e:
            logger.error(f"Error in theta update: {str(e)}")
            # Return current values if update fails
            return {
                'new_theta': current_theta,
                'standard_error': current_se,
                'theta_change': 0.0,
                'information_gain': 0.0,
                'confidence_interval_95': (current_theta - 1.96*current_se, current_theta + 1.96*current_se),
                'model_used': model,
                'convergence_achieved': False,
                'error': str(e)
            }
    
    def calculate_question_information(
        self,
        theta: float,
        question_difficulty: float,
        question_discrimination: float = None,
        model: str = '2PL'
    ) -> float:
        """
        Calculate Fisher Information for a question at given ability level
        Used for optimal question selection in adaptive testing
        """
        try:
            if question_discrimination is None:
                question_discrimination = self.default_discrimination
            
            if model == '1PL' or model == 'Rasch':
                # Rasch model information
                prob = self._rasch_probability(theta, question_difficulty)
                information = prob * (1 - prob)
                
            elif model == '2PL':
                # 2PL model information
                prob = self._two_param_probability(theta, question_difficulty, question_discrimination)
                information = (question_discrimination ** 2) * prob * (1 - prob)
                
            elif model == '3PL':
                # 3PL model information (with guessing parameter)
                guessing = self.default_guessing
                prob = self._three_param_probability(theta, question_difficulty, 
                                                   question_discrimination, guessing)
                denom = prob * (1 - prob)
                if denom > 0:
                    information = (question_discrimination ** 2) * ((prob - guessing) ** 2) / denom
                else:
                    information = 0.0
                    
            else:
                # Default to 2PL
                prob = self._two_param_probability(theta, question_difficulty, question_discrimination)
                information = (question_discrimination ** 2) * prob * (1 - prob)
            
            return max(information, 0.0)
            
        except Exception as e:
            logger.error(f"Error calculating question information: {str(e)}")
            return 0.0
    
    def calculate_test_information(
        self,
        theta: float,
        questions: List[Dict[str, Any]]
    ) -> float:
        """
        Calculate total test information at given ability level
        """
        total_information = 0.0
        
        for question in questions:
            info = self.calculate_question_information(
                theta=theta,
                question_difficulty=question.get('difficulty', 0.0),
                question_discrimination=question.get('discrimination', 1.0),
                model=question.get('model', '2PL')
            )
            total_information += info
        
        return total_information
    
    def optimal_next_difficulty(
        self,
        current_theta: float,
        answered_questions: List[Dict[str, Any]],
        target_information: float = None
    ) -> Tuple[float, float]:
        """
        Calculate optimal difficulty for next question to maximize information gain
        
        Returns:
            Tuple of (optimal_difficulty, expected_information_gain)
        """
        try:
            if target_information is None:
                target_information = 1.0  # Standard target
            
            # For 2PL model, optimal difficulty is close to current theta
            optimal_difficulty = current_theta
            
            # Adjust based on answered questions to avoid redundancy
            if answered_questions:
                asked_difficulties = [q.get('difficulty', 0.0) for q in answered_questions]
                
                # Find gaps in coverage
                min_asked = min(asked_difficulties)
                max_asked = max(asked_difficulties)
                
                # If theta is outside asked range, target edge
                if current_theta < min_asked:
                    optimal_difficulty = current_theta - 0.5
                elif current_theta > max_asked:
                    optimal_difficulty = current_theta + 0.5
                else:
                    # Find largest gap
                    sorted_difficulties = sorted(asked_difficulties)
                    largest_gap = 0.0
                    gap_center = current_theta
                    
                    for i in range(len(sorted_difficulties) - 1):
                        gap_size = sorted_difficulties[i + 1] - sorted_difficulties[i]
                        if gap_size > largest_gap and abs(
                            (sorted_difficulties[i] + sorted_difficulties[i + 1]) / 2 - current_theta
                        ) < 1.0:
                            largest_gap = gap_size
                            gap_center = (sorted_difficulties[i] + sorted_difficulties[i + 1]) / 2
                    
                    optimal_difficulty = gap_center
            
            # Calculate expected information gain
            expected_information = self.calculate_question_information(
                theta=current_theta,
                question_difficulty=optimal_difficulty,
                question_discrimination=self.default_discrimination
            )
            
            return optimal_difficulty, expected_information
            
        except Exception as e:
            logger.error(f"Error calculating optimal difficulty: {str(e)}")
            return current_theta, 0.0
    
    # Private methods for IRT models
    
    def _rasch_model(self, theta: float, difficulty: float) -> float:
        """1-Parameter Logistic (Rasch) Model"""
        return self._rasch_probability(theta, difficulty)
    
    def _two_param_model(self, theta: float, difficulty: float, discrimination: float) -> float:
        """2-Parameter Logistic Model"""
        return self._two_param_probability(theta, difficulty, discrimination)
    
    def _three_param_model(self, theta: float, difficulty: float, 
                          discrimination: float, guessing: float) -> float:
        """3-Parameter Logistic Model"""
        return self._three_param_probability(theta, difficulty, discrimination, guessing)
    
    def _graded_response_model(self, theta: float, difficulty: float, 
                              discrimination: float, thresholds: List[float]) -> List[float]:
        """Graded Response Model for polytomous items"""
        probabilities = []
        
        for threshold in thresholds:
            prob = self._two_param_probability(theta, threshold, discrimination)
            probabilities.append(prob)
        
        return probabilities
    
    def _rasch_probability(self, theta: float, difficulty: float) -> float:
        """Calculate probability for Rasch model"""
        try:
            exponent = theta - difficulty
            if exponent > 700:  # Prevent overflow
                return 1.0
            elif exponent < -700:
                return 0.0
            return 1.0 / (1.0 + np.exp(-exponent))
        except (OverflowError, ZeroDivisionError):
            return 0.5
    
    def _two_param_probability(self, theta: float, difficulty: float, discrimination: float) -> float:
        """Calculate probability for 2PL model"""
        try:
            exponent = discrimination * (theta - difficulty)
            if exponent > 700:
                return 1.0
            elif exponent < -700:
                return 0.0
            return 1.0 / (1.0 + np.exp(-exponent))
        except (OverflowError, ZeroDivisionError):
            return 0.5
    
    def _three_param_probability(self, theta: float, difficulty: float, 
                                discrimination: float, guessing: float) -> float:
        """Calculate probability for 3PL model"""
        try:
            two_pl_prob = self._two_param_probability(theta, difficulty, discrimination)
            return guessing + (1 - guessing) * two_pl_prob
        except Exception:
            return 0.5
    
    def _calculate_mle_theta(
        self,
        current_theta: float,
        question_difficulty: float,
        question_discrimination: float,
        response_score: float,
        model: str
    ) -> float:
        """Calculate Maximum Likelihood Estimate for theta"""
        try:
            # Define likelihood function for optimization
            def neg_log_likelihood(theta):
                prob = self._two_param_probability(theta, question_difficulty, question_discrimination)
                prob = max(min(prob, 0.999), 0.001)  # Avoid log(0)
                
                if response_score == 1.0:
                    return -np.log(prob)
                elif response_score == 0.0:
                    return -np.log(1 - prob)
                else:
                    # For partial credit, use weighted likelihood
                    return -(response_score * np.log(prob) + (1 - response_score) * np.log(1 - prob))
            
            # Optimize using bounded method
            result = opt.minimize_scalar(
                neg_log_likelihood,
                bounds=(current_theta - 3.0, current_theta + 3.0),
                method='bounded'
            )
            
            if result.success:
                return result.x
            else:
                # Fallback to simple update
                return current_theta + 0.1 * (response_score - 0.5)
                
        except Exception as e:
            logger.error(f"MLE calculation failed: {str(e)}")
            return current_theta
    
    def _calculate_standard_error(
        self,
        theta: float,
        question_difficulty: float,
        question_discrimination: float,
        model: str
    ) -> float:
        """Calculate standard error for theta estimate"""
        try:
            information = self.calculate_question_information(
                theta, question_difficulty, question_discrimination, model
            )
            
            if information > 0:
                return 1.0 / np.sqrt(information)
            else:
                return 1.0  # Default high uncertainty
                
        except Exception:
            return 1.0
    
    def _bayesian_se_update(self, current_se: float, new_se: float) -> float:
        """Update standard error using Bayesian approach"""
        try:
            # Information is inverse of variance (se^2)
            current_info = 1.0 / (current_se ** 2) if current_se > 0 else 0.0
            new_info = 1.0 / (new_se ** 2) if new_se > 0 else 0.0
            
            # Combined information
            combined_info = current_info + new_info
            
            if combined_info > 0:
                return 1.0 / np.sqrt(combined_info)
            else:
                return max(current_se, new_se)
                
        except Exception:
            return max(current_se, new_se)
    
    def _calculate_confidence_interval(self, theta: float, se: float, alpha: float = 0.05) -> Tuple[float, float]:
        """Calculate confidence interval for theta estimate"""
        try:
            z_score = norm.ppf(1 - alpha / 2)  # 1.96 for 95% CI
            margin = z_score * se
            return (theta - margin, theta + margin)
        except Exception:
            return (theta - 1.96 * se, theta + 1.96 * se)
    
    def _adjust_for_quality(self, response_score: float, quality_score: float) -> float:
        """Adjust response score based on AI-assessed quality"""
        try:
            # Quality adjustment factor (0.8 to 1.2 multiplier)
            quality_factor = 0.8 + 0.4 * quality_score
            
            # Apply adjustment while keeping score in valid range
            adjusted_score = response_score * quality_factor
            return max(0.0, min(1.0, adjusted_score))
            
        except Exception:
            return response_score
