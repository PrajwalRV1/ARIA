#!/usr/bin/env python3
"""
Bias Detection Module for ARIA Adaptive Interview Platform
Implements comprehensive bias detection algorithms to ensure fairness
across demographic dimensions in question selection and response evaluation.
"""

import numpy as np
import logging
from typing import Dict, List, Optional, Any, Tuple
from datetime import datetime, timedelta
from dataclasses import dataclass
from enum import Enum
from collections import defaultdict, Counter
import json
import redis
import mysql.connector
from scipy import stats
import asyncio

logger = logging.getLogger(__name__)

class BiasType(Enum):
    """Types of bias that can be detected"""
    DEMOGRAPHIC = "demographic"
    CULTURAL = "cultural"
    LINGUISTIC = "linguistic"
    SOCIOECONOMIC = "socioeconomic"
    EDUCATIONAL = "educational"
    GEOGRAPHIC = "geographic"
    GENDER = "gender"
    AGE = "age"
    EXPERIENCE = "experience"
    SKILL_MISMATCH = "skill_mismatch"

@dataclass
class BiasDetectionResult:
    """Result of bias detection analysis"""
    bias_detected: bool
    bias_score: float  # 0.0 = no bias, 1.0 = maximum bias
    bias_types: List[BiasType]
    confidence: float
    affected_groups: List[str]
    recommendation: str
    details: Dict[str, Any]
    timestamp: datetime

@dataclass
class DemographicGroup:
    """Represents a demographic group for bias analysis"""
    group_id: str
    group_name: str
    characteristics: Dict[str, Any]
    sample_size: int
    average_performance: float
    performance_variance: float

class BiasDetector:
    """
    Comprehensive bias detection system for adaptive interviews
    Uses statistical analysis and ML techniques to identify unfair advantages/disadvantages
    """
    
    def __init__(self):
        """Initialize the bias detection module"""
        self.redis_client = redis.Redis(host='localhost', port=6379, db=2, decode_responses=True)
        
        # Bias detection thresholds
        self.bias_thresholds = {
            BiasType.DEMOGRAPHIC: 0.15,    # 15% performance difference threshold
            BiasType.CULTURAL: 0.12,
            BiasType.LINGUISTIC: 0.18,
            BiasType.SOCIOECONOMIC: 0.14,
            BiasType.EDUCATIONAL: 0.16,
            BiasType.GEOGRAPHIC: 0.10,
            BiasType.GENDER: 0.08,         # Lower threshold for gender bias
            BiasType.AGE: 0.12,
            BiasType.EXPERIENCE: 0.20,     # Higher threshold as experience is relevant
            BiasType.SKILL_MISMATCH: 0.25
        }
        
        # Statistical significance levels
        self.significance_levels = {
            'high': 0.01,      # p < 0.01
            'medium': 0.05,    # p < 0.05  
            'low': 0.10        # p < 0.10
        }
        
        # Minimum sample sizes for reliable bias detection
        self.min_sample_sizes = {
            'reliable': 50,
            'moderate': 30,
            'minimal': 15
        }
        
        # Database connection
        self.db_config = {
            'host': 'localhost',
            'user': 'aria_user', 
            'password': 'aria_password',
            'database': 'aria_interviews',
            'autocommit': True
        }
        
        # Cache for demographic performance data
        self.demographic_cache = {}
        self.cache_ttl = 3600  # 1 hour
        
        logger.info("Bias Detector initialized with comprehensive bias analysis")
    
    async def check_question_bias(
        self,
        question_id: int,
        candidate_profile: Dict[str, Any]
    ) -> BiasDetectionResult:
        """
        Check for potential bias in question selection for a specific candidate
        
        Args:
            question_id: ID of the question to analyze
            candidate_profile: Profile of the candidate including demographic info
            
        Returns:
            BiasDetectionResult with bias analysis
        """
        try:
            logger.debug(f"Checking question bias for question {question_id}")
            
            # Get question characteristics
            question_data = await self._get_question_data(question_id)
            if not question_data:
                return self._create_default_bias_result("Question data not found")
            
            # Extract demographic information (privacy-preserving)
            demographics = self._extract_demographics(candidate_profile)
            
            # Get historical performance data for this question across demographics
            performance_data = await self._get_question_performance_by_demographics(question_id)
            
            # Analyze bias across different dimensions
            bias_analyses = []
            
            # Check demographic bias
            demographic_bias = await self._analyze_demographic_bias(
                question_id, demographics, performance_data
            )
            bias_analyses.append(demographic_bias)
            
            # Check cultural bias
            cultural_bias = await self._analyze_cultural_bias(
                question_id, candidate_profile, performance_data
            )
            bias_analyses.append(cultural_bias)
            
            # Check linguistic bias
            linguistic_bias = await self._analyze_linguistic_bias(
                question_id, candidate_profile, question_data
            )
            bias_analyses.append(linguistic_bias)
            
            # Check educational bias
            educational_bias = await self._analyze_educational_bias(
                question_id, candidate_profile, performance_data
            )
            bias_analyses.append(educational_bias)
            
            # Combine bias analyses
            combined_result = self._combine_bias_analyses(bias_analyses)
            
            # Store bias check result
            await self._store_bias_check(question_id, candidate_profile, combined_result)
            
            return combined_result
            
        except Exception as e:
            logger.error(f"Error checking question bias: {str(e)}")
            return self._create_default_bias_result(f"Analysis error: {str(e)}")
    
    async def analyze_response_bias(
        self,
        candidate_id: int,
        question_id: int,
        response_data: Dict[str, Any],
        theta_change: float
    ) -> Dict[str, Any]:
        """
        Analyze potential bias in response evaluation
        
        Args:
            candidate_id: ID of the candidate
            question_id: ID of the question
            response_data: The candidate's response data
            theta_change: Change in theta from this response
            
        Returns:
            Dict with bias analysis results
        """
        try:
            logger.debug(f"Analyzing response bias for candidate {candidate_id}, question {question_id}")
            
            # Get candidate profile
            candidate_profile = await self._get_candidate_profile(candidate_id)
            if not candidate_profile:
                return {"bias_detected": False, "details": "No candidate profile found"}
            
            # Check for unusual theta changes
            theta_bias = self._analyze_theta_change_bias(
                candidate_profile, theta_change, question_id
            )
            
            # Check response evaluation consistency
            evaluation_bias = await self._analyze_evaluation_consistency(
                candidate_profile, response_data, question_id
            )
            
            # Check for systematic patterns
            systematic_bias = await self._analyze_systematic_response_bias(
                candidate_id, question_id, response_data
            )
            
            # Combine analyses
            overall_bias = max(theta_bias['bias_score'], 
                             evaluation_bias['bias_score'],
                             systematic_bias['bias_score'])
            
            result = {
                "bias_detected": overall_bias > 0.15,
                "bias_score": overall_bias,
                "details": {
                    "theta_bias": theta_bias,
                    "evaluation_bias": evaluation_bias, 
                    "systematic_bias": systematic_bias
                },
                "timestamp": datetime.now().isoformat()
            }
            
            # Store response bias analysis
            await self._store_response_bias_analysis(candidate_id, question_id, result)
            
            return result
            
        except Exception as e:
            logger.error(f"Error analyzing response bias: {str(e)}")
            return {"bias_detected": False, "error": str(e)}
    
    async def get_demographic_fairness_report(
        self,
        time_period_days: int = 30
    ) -> Dict[str, Any]:
        """
        Generate comprehensive demographic fairness report
        
        Args:
            time_period_days: Number of days to analyze
            
        Returns:
            Dict with fairness metrics across demographic groups
        """
        try:
            logger.info(f"Generating demographic fairness report for {time_period_days} days")
            
            # Get interview data for the time period
            end_date = datetime.now()
            start_date = end_date - timedelta(days=time_period_days)
            
            interview_data = await self._get_interview_data_by_period(start_date, end_date)
            
            if len(interview_data) < self.min_sample_sizes['minimal']:
                return {
                    "status": "insufficient_data",
                    "message": f"Need at least {self.min_sample_sizes['minimal']} interviews for analysis",
                    "sample_size": len(interview_data)
                }
            
            # Group by demographics
            demographic_groups = self._group_by_demographics(interview_data)
            
            # Calculate fairness metrics for each group
            fairness_metrics = {}
            overall_metrics = {}
            
            for group_id, group_data in demographic_groups.items():
                metrics = self._calculate_group_fairness_metrics(group_data)
                fairness_metrics[group_id] = metrics
            
            # Calculate overall fairness scores
            overall_metrics = self._calculate_overall_fairness(fairness_metrics)
            
            # Identify bias hotspots
            bias_hotspots = self._identify_bias_hotspots(fairness_metrics)
            
            # Generate recommendations
            recommendations = self._generate_fairness_recommendations(
                fairness_metrics, bias_hotspots
            )
            
            report = {
                "report_period": {
                    "start_date": start_date.isoformat(),
                    "end_date": end_date.isoformat(),
                    "days": time_period_days
                },
                "sample_size": len(interview_data),
                "demographic_groups": len(demographic_groups),
                "overall_fairness": overall_metrics,
                "group_metrics": fairness_metrics,
                "bias_hotspots": bias_hotspots,
                "recommendations": recommendations,
                "generated_at": datetime.now().isoformat()
            }
            
            # Cache the report
            cache_key = f"fairness_report:{time_period_days}d:{datetime.now().strftime('%Y%m%d')}"
            self.redis_client.setex(cache_key, 86400, json.dumps(report, default=str))
            
            return report
            
        except Exception as e:
            logger.error(f"Error generating fairness report: {str(e)}")
            return {"status": "error", "message": str(e)}
    
    async def get_question_bias_analysis(self, question_id: int) -> Dict[str, Any]:
        """
        Get comprehensive bias analysis for a specific question
        
        Args:
            question_id: ID of the question to analyze
            
        Returns:
            Dict with question-specific bias metrics
        """
        try:
            # Get question performance across demographics
            performance_data = await self._get_question_performance_by_demographics(question_id)
            
            # Calculate bias metrics
            bias_metrics = {}
            
            for bias_type in BiasType:
                metric = await self._calculate_question_bias_metric(
                    question_id, bias_type, performance_data
                )
                bias_metrics[bias_type.value] = metric
            
            # Overall bias score
            overall_bias = max([m.get('bias_score', 0.0) for m in bias_metrics.values()])
            
            return {
                "question_id": question_id,
                "overall_bias_score": overall_bias,
                "bias_detected": overall_bias > 0.15,
                "bias_by_type": bias_metrics,
                "analyzed_at": datetime.now().isoformat()
            }
            
        except Exception as e:
            logger.error(f"Error analyzing question bias: {str(e)}")
            return {"error": str(e)}
    
    # Private methods
    
    def _extract_demographics(self, candidate_profile: Dict[str, Any]) -> Dict[str, Any]:
        """Extract demographic information while preserving privacy"""
        demographics = {}
        
        # Extract relevant demographic features (anonymized/hashed where needed)
        if 'demographics' in candidate_profile:
            demo_data = candidate_profile['demographics']
            
            demographics.update({
                'region': demo_data.get('region', 'unknown'),
                'education_level': demo_data.get('education_level', 'unknown'),
                'experience_level': demo_data.get('experience_level', 'unknown'),
                'primary_language': demo_data.get('primary_language', 'unknown'),
                'age_range': demo_data.get('age_range', 'unknown')
            })
        
        # Add job-related demographics
        demographics.update({
            'job_role': candidate_profile.get('job_role', 'unknown'),
            'experience_years': candidate_profile.get('experience_years', 0),
            'technologies': candidate_profile.get('technologies', [])
        })
        
        return demographics
    
    async def _get_question_data(self, question_id: int) -> Optional[Dict[str, Any]]:
        """Get question data from database"""
        try:
            conn = mysql.connector.connect(**self.db_config)
            cursor = conn.cursor(dictionary=True)
            
            cursor.execute("""
                SELECT q.*, qm.difficulty, qm.discrimination, qm.guessing_parameter,
                       qm.category, qm.technologies, qm.expected_duration_minutes
                FROM questions q
                LEFT JOIN question_irt_parameters qm ON q.question_id = qm.question_id
                WHERE q.question_id = %s
            """, (question_id,))
            
            result = cursor.fetchone()
            cursor.close()
            conn.close()
            
            return result
            
        except Exception as e:
            logger.error(f"Error getting question data: {str(e)}")
            return None
    
    async def _get_question_performance_by_demographics(
        self, question_id: int
    ) -> Dict[str, List[Dict[str, Any]]]:
        """Get historical performance data for a question grouped by demographics"""
        try:
            conn = mysql.connector.connect(**self.db_config)
            cursor = conn.cursor(dictionary=True)
            
            cursor.execute("""
                SELECT cr.*, c.demographics, c.job_role, c.experience_years, c.technologies
                FROM candidate_responses cr
                JOIN candidates c ON cr.candidate_id = c.candidate_id  
                WHERE cr.question_id = %s
                AND cr.created_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
            """, (question_id,))
            
            results = cursor.fetchall()
            cursor.close()
            conn.close()
            
            # Group by demographic characteristics
            grouped_data = defaultdict(list)
            
            for result in results:
                # Create demographic key
                demo_key = self._create_demographic_key(result)
                grouped_data[demo_key].append(result)
            
            return dict(grouped_data)
            
        except Exception as e:
            logger.error(f"Error getting performance by demographics: {str(e)}")
            return {}
    
    def _create_demographic_key(self, candidate_data: Dict[str, Any]) -> str:
        """Create a demographic grouping key"""
        demographics = json.loads(candidate_data.get('demographics', '{}'))
        
        # Create key based on relevant demographic factors
        key_parts = [
            demographics.get('region', 'unknown'),
            demographics.get('education_level', 'unknown'),
            demographics.get('experience_level', 'unknown'),
            candidate_data.get('job_role', 'unknown')
        ]
        
        return '|'.join(key_parts)
    
    async def _analyze_demographic_bias(
        self,
        question_id: int,
        demographics: Dict[str, Any],
        performance_data: Dict[str, List[Dict[str, Any]]]
    ) -> Dict[str, Any]:
        """Analyze demographic bias for a question"""
        try:
            if len(performance_data) < 2:
                return {"bias_detected": False, "bias_score": 0.0, "reason": "insufficient_data"}
            
            # Calculate performance metrics for each demographic group
            group_performances = {}
            
            for group_key, group_data in performance_data.items():
                if len(group_data) >= self.min_sample_sizes['minimal']:
                    scores = [d.get('score', 0.0) for d in group_data]
                    group_performances[group_key] = {
                        'mean': np.mean(scores),
                        'std': np.std(scores),
                        'count': len(scores)
                    }
            
            if len(group_performances) < 2:
                return {"bias_detected": False, "bias_score": 0.0, "reason": "insufficient_groups"}
            
            # Calculate bias score based on performance differences
            performances = list(group_performances.values())
            means = [p['mean'] for p in performances]
            
            # Use coefficient of variation as bias metric
            overall_mean = np.mean(means)
            performance_std = np.std(means)
            bias_score = performance_std / overall_mean if overall_mean > 0 else 0.0
            
            # Perform statistical tests
            if len(means) >= 2:
                # ANOVA test for multiple groups
                group_scores = [
                    [d.get('score', 0.0) for d in group_data]
                    for group_data in performance_data.values()
                    if len(group_data) >= self.min_sample_sizes['minimal']
                ]
                
                if len(group_scores) >= 2:
                    f_stat, p_value = stats.f_oneway(*group_scores)
                    statistical_significance = p_value < self.significance_levels['medium']
                else:
                    statistical_significance = False
            else:
                statistical_significance = False
            
            bias_detected = (
                bias_score > self.bias_thresholds[BiasType.DEMOGRAPHIC] and 
                statistical_significance
            )
            
            return {
                "bias_detected": bias_detected,
                "bias_score": bias_score,
                "statistical_significance": statistical_significance,
                "group_count": len(group_performances),
                "performance_range": max(means) - min(means) if means else 0.0,
                "details": group_performances
            }
            
        except Exception as e:
            logger.error(f"Error analyzing demographic bias: {str(e)}")
            return {"bias_detected": False, "bias_score": 0.0, "error": str(e)}
    
    async def _analyze_cultural_bias(
        self,
        question_id: int,
        candidate_profile: Dict[str, Any],
        performance_data: Dict[str, List[Dict[str, Any]]]
    ) -> Dict[str, Any]:
        """Analyze cultural bias in question"""
        try:
            # Get question content to analyze for cultural references
            question_data = await self._get_question_data(question_id)
            if not question_data:
                return {"bias_detected": False, "bias_score": 0.0, "reason": "no_question_data"}
            
            question_text = question_data.get('question_text', '')
            
            # Check for cultural references, idioms, or region-specific knowledge
            cultural_indicators = self._detect_cultural_indicators(question_text)
            
            # Analyze performance by region/culture if data available
            regional_bias = self._analyze_regional_performance_differences(performance_data)
            
            bias_score = max(
                cultural_indicators.get('bias_score', 0.0),
                regional_bias.get('bias_score', 0.0)
            )
            
            return {
                "bias_detected": bias_score > self.bias_thresholds[BiasType.CULTURAL],
                "bias_score": bias_score,
                "cultural_indicators": cultural_indicators,
                "regional_bias": regional_bias
            }
            
        except Exception as e:
            logger.error(f"Error analyzing cultural bias: {str(e)}")
            return {"bias_detected": False, "bias_score": 0.0, "error": str(e)}
    
    def _detect_cultural_indicators(self, question_text: str) -> Dict[str, Any]:
        """Detect cultural indicators in question text"""
        # List of potentially biased cultural references
        cultural_references = [
            # Western cultural references
            'baseball', 'football', 'thanksgiving', 'christmas', 'halloween',
            # Financial/economic assumptions
            'checking account', 'credit card', 'mortgage', 'social security',
            # Educational assumptions  
            'high school', 'college', 'university', 'SAT', 'GPA',
            # Geographic references
            'downtown', 'suburban', 'mall', 'highway'
        ]
        
        text_lower = question_text.lower()
        found_references = [ref for ref in cultural_references if ref in text_lower]
        
        # Calculate bias score based on number of cultural references
        bias_score = min(len(found_references) * 0.1, 0.8)
        
        return {
            "bias_score": bias_score,
            "cultural_references_found": found_references,
            "reference_count": len(found_references)
        }
    
    def _analyze_regional_performance_differences(
        self, performance_data: Dict[str, List[Dict[str, Any]]]
    ) -> Dict[str, Any]:
        """Analyze performance differences by region"""
        try:
            # Group performance by region
            regional_groups = defaultdict(list)
            
            for group_key, group_data in performance_data.items():
                # Extract region from group key
                region = group_key.split('|')[0] if '|' in group_key else 'unknown'
                regional_groups[region].extend(group_data)
            
            if len(regional_groups) < 2:
                return {"bias_score": 0.0, "reason": "insufficient_regional_data"}
            
            # Calculate regional performance differences
            regional_means = {}
            for region, data in regional_groups.items():
                if len(data) >= 5:  # Minimum for regional analysis
                    scores = [d.get('score', 0.0) for d in data]
                    regional_means[region] = np.mean(scores)
            
            if len(regional_means) < 2:
                return {"bias_score": 0.0, "reason": "insufficient_regional_groups"}
            
            # Calculate coefficient of variation across regions
            means = list(regional_means.values())
            overall_mean = np.mean(means)
            regional_std = np.std(means)
            bias_score = regional_std / overall_mean if overall_mean > 0 else 0.0
            
            return {
                "bias_score": bias_score,
                "regional_differences": max(means) - min(means),
                "regional_means": regional_means
            }
            
        except Exception as e:
            logger.error(f"Error analyzing regional performance: {str(e)}")
            return {"bias_score": 0.0, "error": str(e)}
    
    async def _analyze_linguistic_bias(
        self,
        question_id: int,
        candidate_profile: Dict[str, Any],
        question_data: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Analyze linguistic bias in question"""
        try:
            question_text = question_data.get('question_text', '')
            
            # Check for complex language patterns
            linguistic_complexity = self._analyze_linguistic_complexity(question_text)
            
            # Check for idioms and colloquialisms
            idiom_usage = self._detect_idioms_and_colloquialisms(question_text)
            
            # Check for technical jargon that may be region-specific
            jargon_analysis = self._analyze_technical_jargon(question_text)
            
            # Combine scores
            bias_score = max(
                linguistic_complexity.get('complexity_bias', 0.0),
                idiom_usage.get('idiom_bias', 0.0),
                jargon_analysis.get('jargon_bias', 0.0)
            )
            
            return {
                "bias_detected": bias_score > self.bias_thresholds[BiasType.LINGUISTIC],
                "bias_score": bias_score,
                "linguistic_complexity": linguistic_complexity,
                "idiom_usage": idiom_usage,
                "jargon_analysis": jargon_analysis
            }
            
        except Exception as e:
            logger.error(f"Error analyzing linguistic bias: {str(e)}")
            return {"bias_detected": False, "bias_score": 0.0, "error": str(e)}
    
    def _analyze_linguistic_complexity(self, text: str) -> Dict[str, Any]:
        """Analyze linguistic complexity of question text"""
        words = text.split()
        sentences = text.split('.')
        
        # Calculate metrics
        avg_word_length = np.mean([len(word) for word in words]) if words else 0
        avg_sentence_length = np.mean([len(s.split()) for s in sentences]) if sentences else 0
        
        # Complex words (more than 6 characters)
        complex_words = [w for w in words if len(w) > 6]
        complex_word_ratio = len(complex_words) / len(words) if words else 0
        
        # Calculate complexity bias score
        complexity_score = (
            min(avg_word_length / 8.0, 1.0) * 0.3 +
            min(avg_sentence_length / 20.0, 1.0) * 0.4 +
            complex_word_ratio * 0.3
        )
        
        return {
            "complexity_bias": complexity_score,
            "avg_word_length": avg_word_length,
            "avg_sentence_length": avg_sentence_length,
            "complex_word_ratio": complex_word_ratio
        }
    
    def _detect_idioms_and_colloquialisms(self, text: str) -> Dict[str, Any]:
        """Detect idioms and colloquialisms that may cause linguistic bias"""
        # Common English idioms and colloquialisms
        idioms = [
            "piece of cake", "break a leg", "hit the nail on the head",
            "spill the beans", "break the ice", "cost an arm and a leg",
            "easy as pie", "it's raining cats and dogs", "kill two birds with one stone"
        ]
        
        text_lower = text.lower()
        found_idioms = [idiom for idiom in idioms if idiom in text_lower]
        
        idiom_bias = min(len(found_idioms) * 0.2, 0.8)
        
        return {
            "idiom_bias": idiom_bias,
            "idioms_found": found_idioms,
            "idiom_count": len(found_idioms)
        }
    
    def _analyze_technical_jargon(self, text: str) -> Dict[str, Any]:
        """Analyze technical jargon that may be region or culture specific"""
        # This would be expanded with more comprehensive jargon detection
        region_specific_terms = [
            ("lift", "elevator"), ("lorry", "truck"), ("biscuit", "cookie")
        ]
        
        # For now, return minimal bias
        return {
            "jargon_bias": 0.0,
            "region_specific_terms": [],
            "jargon_density": 0.0
        }
    
    async def _analyze_educational_bias(
        self,
        question_id: int,
        candidate_profile: Dict[str, Any],
        performance_data: Dict[str, List[Dict[str, Any]]]
    ) -> Dict[str, Any]:
        """Analyze educational bias in question"""
        try:
            # Group performance by education level
            education_groups = defaultdict(list)
            
            for group_key, group_data in performance_data.items():
                # Extract education level from group key
                education_level = group_key.split('|')[1] if '|' in group_key else 'unknown'
                education_groups[education_level].extend(group_data)
            
            if len(education_groups) < 2:
                return {"bias_detected": False, "bias_score": 0.0, "reason": "insufficient_education_data"}
            
            # Calculate performance differences by education level
            education_performance = {}
            for edu_level, data in education_groups.items():
                if len(data) >= self.min_sample_sizes['minimal']:
                    scores = [d.get('score', 0.0) for d in data]
                    education_performance[edu_level] = {
                        'mean': np.mean(scores),
                        'std': np.std(scores),
                        'count': len(scores)
                    }
            
            if len(education_performance) < 2:
                return {"bias_detected": False, "bias_score": 0.0, "reason": "insufficient_education_groups"}
            
            # Calculate educational bias score
            means = [perf['mean'] for perf in education_performance.values()]
            overall_mean = np.mean(means)
            education_std = np.std(means)
            bias_score = education_std / overall_mean if overall_mean > 0 else 0.0
            
            return {
                "bias_detected": bias_score > self.bias_thresholds[BiasType.EDUCATIONAL],
                "bias_score": bias_score,
                "education_performance": education_performance,
                "performance_range": max(means) - min(means)
            }
            
        except Exception as e:
            logger.error(f"Error analyzing educational bias: {str(e)}")
            return {"bias_detected": False, "bias_score": 0.0, "error": str(e)}
    
    def _combine_bias_analyses(self, analyses: List[Dict[str, Any]]) -> BiasDetectionResult:
        """Combine multiple bias analyses into a single result"""
        try:
            # Calculate overall bias score
            bias_scores = [analysis.get('bias_score', 0.0) for analysis in analyses]
            overall_bias_score = max(bias_scores) if bias_scores else 0.0
            
            # Determine if bias is detected
            bias_detected = any(analysis.get('bias_detected', False) for analysis in analyses)
            
            # Identify bias types
            detected_bias_types = []
            bias_type_mapping = [
                BiasType.DEMOGRAPHIC, BiasType.CULTURAL, 
                BiasType.LINGUISTIC, BiasType.EDUCATIONAL
            ]
            
            for i, analysis in enumerate(analyses):
                if analysis.get('bias_detected', False) and i < len(bias_type_mapping):
                    detected_bias_types.append(bias_type_mapping[i])
            
            # Generate recommendation
            recommendation = self._generate_bias_recommendation(
                overall_bias_score, detected_bias_types
            )
            
            # Calculate confidence based on data quality
            confidence = self._calculate_bias_confidence(analyses)
            
            return BiasDetectionResult(
                bias_detected=bias_detected,
                bias_score=overall_bias_score,
                bias_types=detected_bias_types,
                confidence=confidence,
                affected_groups=self._identify_affected_groups(analyses),
                recommendation=recommendation,
                details={
                    "demographic_analysis": analyses[0] if len(analyses) > 0 else {},
                    "cultural_analysis": analyses[1] if len(analyses) > 1 else {},
                    "linguistic_analysis": analyses[2] if len(analyses) > 2 else {},
                    "educational_analysis": analyses[3] if len(analyses) > 3 else {}
                },
                timestamp=datetime.now()
            )
            
        except Exception as e:
            logger.error(f"Error combining bias analyses: {str(e)}")
            return self._create_default_bias_result(f"Combination error: {str(e)}")
    
    def _generate_bias_recommendation(
        self, bias_score: float, bias_types: List[BiasType]
    ) -> str:
        """Generate recommendation based on bias analysis"""
        if bias_score < 0.1:
            return "No significant bias detected. Question appears fair across demographic groups."
        elif bias_score < 0.2:
            return "Minimal bias detected. Monitor performance across groups and consider question refinement."
        elif bias_score < 0.3:
            return "Moderate bias detected. Review question content and consider alternative phrasing or examples."
        else:
            return "High bias detected. Recommend removing or significantly revising this question."
    
    def _calculate_bias_confidence(self, analyses: List[Dict[str, Any]]) -> float:
        """Calculate confidence level in bias detection"""
        # Base confidence on data quality and statistical significance
        confidence_factors = []
        
        for analysis in analyses:
            if 'statistical_significance' in analysis:
                confidence_factors.append(0.8 if analysis['statistical_significance'] else 0.4)
            elif 'group_count' in analysis:
                # More groups provide higher confidence
                group_confidence = min(analysis['group_count'] / 5.0, 1.0)
                confidence_factors.append(group_confidence)
            else:
                confidence_factors.append(0.5)  # Default moderate confidence
        
        return np.mean(confidence_factors) if confidence_factors else 0.5
    
    def _identify_affected_groups(self, analyses: List[Dict[str, Any]]) -> List[str]:
        """Identify groups most affected by bias"""
        affected_groups = []
        
        for analysis in analyses:
            if 'details' in analysis and isinstance(analysis['details'], dict):
                # Extract group information from analysis details
                for group_key, group_data in analysis['details'].items():
                    if isinstance(group_data, dict) and group_data.get('mean', 1.0) < 0.7:
                        affected_groups.append(group_key)
        
        return list(set(affected_groups))  # Remove duplicates
    
    def _create_default_bias_result(self, reason: str) -> BiasDetectionResult:
        """Create default bias result for error cases"""
        return BiasDetectionResult(
            bias_detected=False,
            bias_score=0.0,
            bias_types=[],
            confidence=0.0,
            affected_groups=[],
            recommendation=f"Unable to complete bias analysis: {reason}",
            details={"error": reason},
            timestamp=datetime.now()
        )
    
    async def _store_bias_check(
        self, question_id: int, candidate_profile: Dict[str, Any], result: BiasDetectionResult
    ):
        """Store bias check result"""
        try:
            cache_key = f"bias_check:{question_id}:{hash(str(candidate_profile))}"
            bias_data = {
                "bias_detected": result.bias_detected,
                "bias_score": result.bias_score,
                "bias_types": [bt.value for bt in result.bias_types],
                "confidence": result.confidence,
                "recommendation": result.recommendation,
                "timestamp": result.timestamp.isoformat()
            }
            
            self.redis_client.setex(cache_key, 3600, json.dumps(bias_data, default=str))
            
        except Exception as e:
            logger.error(f"Error storing bias check: {str(e)}")
    
    def _analyze_theta_change_bias(
        self, candidate_profile: Dict[str, Any], theta_change: float, question_id: int
    ) -> Dict[str, Any]:
        """Analyze bias in theta change calculations"""
        # Check if theta change is unusually large compared to typical patterns
        # This would compare against historical theta changes for similar candidates
        
        expected_theta_change = 0.2  # Default expected change
        theta_deviation = abs(theta_change - expected_theta_change)
        
        bias_score = min(theta_deviation / 0.5, 1.0) if theta_deviation > 0.3 else 0.0
        
        return {
            "bias_score": bias_score,
            "theta_change": theta_change,
            "expected_change": expected_theta_change,
            "deviation": theta_deviation
        }
    
    async def _analyze_evaluation_consistency(
        self, candidate_profile: Dict[str, Any], response_data: Dict[str, Any], question_id: int
    ) -> Dict[str, Any]:
        """Analyze consistency in response evaluation"""
        # This would compare evaluation consistency across similar candidates
        # For now, return minimal bias
        return {
            "bias_score": 0.0,
            "consistency_score": 0.8,
            "evaluation_fair": True
        }
    
    async def _analyze_systematic_response_bias(
        self, candidate_id: int, question_id: int, response_data: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Analyze systematic patterns in response bias"""
        # This would look for systematic patterns that might indicate bias
        # For now, return minimal bias
        return {
            "bias_score": 0.0,
            "systematic_patterns": [],
            "pattern_strength": 0.0
        }
    
    async def _get_candidate_profile(self, candidate_id: int) -> Optional[Dict[str, Any]]:
        """Get candidate profile from database"""
        try:
            conn = mysql.connector.connect(**self.db_config)
            cursor = conn.cursor(dictionary=True)
            
            cursor.execute("""
                SELECT * FROM candidates WHERE candidate_id = %s
            """, (candidate_id,))
            
            result = cursor.fetchone()
            cursor.close()
            conn.close()
            
            return result
            
        except Exception as e:
            logger.error(f"Error getting candidate profile: {str(e)}")
            return None
    
    async def _store_response_bias_analysis(
        self, candidate_id: int, question_id: int, result: Dict[str, Any]
    ):
        """Store response bias analysis"""
        try:
            cache_key = f"response_bias:{candidate_id}:{question_id}"
            self.redis_client.setex(cache_key, 3600, json.dumps(result, default=str))
            
        except Exception as e:
            logger.error(f"Error storing response bias analysis: {str(e)}")
    
    # Additional methods for fairness reporting
    async def _get_interview_data_by_period(self, start_date: datetime, end_date: datetime) -> List[Dict]:
        """Get interview data for a specific time period"""
        # Implementation would query database for interview data
        return []
    
    def _group_by_demographics(self, interview_data: List[Dict]) -> Dict[str, List[Dict]]:
        """Group interview data by demographics"""
        # Implementation would group interviews by demographic characteristics
        return {}
    
    def _calculate_group_fairness_metrics(self, group_data: List[Dict]) -> Dict[str, Any]:
        """Calculate fairness metrics for a demographic group"""
        # Implementation would calculate comprehensive fairness metrics
        return {}
    
    def _calculate_overall_fairness(self, fairness_metrics: Dict[str, Dict]) -> Dict[str, Any]:
        """Calculate overall fairness scores"""
        # Implementation would aggregate fairness metrics
        return {}
    
    def _identify_bias_hotspots(self, fairness_metrics: Dict[str, Dict]) -> List[Dict[str, Any]]:
        """Identify bias hotspots"""
        # Implementation would identify areas of concern
        return []
    
    def _generate_fairness_recommendations(
        self, fairness_metrics: Dict[str, Dict], bias_hotspots: List[Dict]
    ) -> List[str]:
        """Generate fairness recommendations"""
        # Implementation would generate actionable recommendations
        return []
    
    async def _calculate_question_bias_metric(
        self, question_id: int, bias_type: BiasType, performance_data: Dict
    ) -> Dict[str, Any]:
        """Calculate bias metric for specific bias type"""
        # Implementation would calculate specific bias metrics
        return {"bias_score": 0.0}
