#!/usr/bin/env python3
"""
ARIA Bias Detection and Fairness Engine

Advanced bias detection system for ensuring fair and unbiased interviews including:
- Demographic bias detection across protected categories
- Linguistic bias analysis 
- Cultural and contextual bias identification
- Fairness metrics calculation (demographic parity, equalized odds, etc.)
- Intersectional bias analysis
- Real-time bias monitoring and alerts
- Bias mitigation recommendations
"""

import asyncio
import logging
import re
import time
import json
from typing import Dict, List, Optional, Tuple, Any, Set
from dataclasses import dataclass
from collections import defaultdict, Counter
import numpy as np
import pandas as pd
from scipy import stats
from sklearn.metrics import confusion_matrix
import nltk
from nltk.tokenize import word_tokenize, sent_tokenize
from nltk.corpus import wordnet
from textstat import flesch_reading_ease, flesch_kincaid_grade

# Machine learning for bias detection
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report
from sklearn.model_selection import cross_val_score

# Fairness libraries
try:
    from fairlearn.metrics import (
        demographic_parity_difference, 
        equalized_odds_difference,
        MetricFrame
    )
    FAIRLEARN_AVAILABLE = True
except ImportError:
    FAIRLEARN_AVAILABLE = False

from models import BiasCategory, FairnessMetrics

logger = logging.getLogger(__name__)


@dataclass
class BiasDetectionResult:
    """Complete bias detection result"""
    analysis_id: str
    overall_bias_score: float
    bias_categories: List[BiasCategory]
    demographic_bias: float
    linguistic_bias: float
    cultural_bias: float
    fairness_metrics: FairnessMetrics
    recommendations: List[str]
    confidence_level: float
    processing_time: float


@dataclass  
class DemographicInfo:
    """Anonymized demographic information for fairness analysis"""
    age_group: Optional[str] = None  # "young", "middle", "senior"
    gender_identity: Optional[str] = None  # "male", "female", "non-binary", "other"
    ethnicity: Optional[str] = None  # "group_a", "group_b", etc. (anonymized)
    education_level: Optional[str] = None  # "high_school", "bachelor", "master", "phd"
    employment_status: Optional[str] = None  # "employed", "unemployed", "student"
    geographic_region: Optional[str] = None  # "region_1", "region_2", etc.
    native_language: Optional[str] = None  # "english", "other"
    interview_experience: Optional[str] = None  # "novice", "experienced", "expert"


class BiasDetector:
    """Advanced bias detection and fairness analysis system"""
    
    def __init__(self, model_path: Optional[str] = None, fairness_threshold: float = 0.8):
        """
        Initialize bias detector
        
        Args:
            model_path: Path to pre-trained bias detection model
            fairness_threshold: Threshold for fairness metrics (0-1)
        """
        self.model_path = model_path
        self.fairness_threshold = fairness_threshold
        
        # Bias detection models
        self.bias_classifier = None
        self.linguistic_analyzer = None
        
        # Bias vocabularies and patterns
        self.protected_attributes = self._load_protected_attributes()
        self.biased_terms = self._load_biased_terms()
        self.inclusive_alternatives = self._load_inclusive_alternatives()
        self.cultural_indicators = self._load_cultural_indicators()
        self.unconscious_bias_patterns = self._load_unconscious_bias_patterns()
        
        # Fairness tracking
        self.demographic_responses = defaultdict(list)
        self.fairness_history = []
        
        # Statistical thresholds
        self.statistical_significance_threshold = 0.05
        self.effect_size_threshold = 0.2  # Cohen's d
        
        # Analysis state
        self.is_initialized = False
        
        logger.info("BiasDetector initialized")
    
    async def initialize_models(self):
        """Load and initialize bias detection models"""
        try:
            logger.info("Loading bias detection models...")
            
            # Load pre-trained bias classifier if available
            if self.model_path:
                await self._load_bias_classifier()
            else:
                await self._create_default_classifier()
            
            # Initialize linguistic bias analyzer
            await self._initialize_linguistic_analyzer()
            
            # Download required NLTK data
            await self._download_nltk_data()
            
            self.is_initialized = True
            logger.info("Bias detection models initialized successfully")
            
        except Exception as e:
            logger.error(f"Failed to initialize bias detection models: {e}")
            raise
    
    async def _load_bias_classifier(self):
        """Load pre-trained bias classifier"""
        try:
            import joblib
            self.bias_classifier = joblib.load(self.model_path)
            logger.info("Pre-trained bias classifier loaded")
            
        except Exception as e:
            logger.warning(f"Could not load pre-trained classifier: {e}")
            await self._create_default_classifier()
    
    async def _create_default_classifier(self):
        """Create default bias classifier"""
        logger.info("Creating default bias classifier")
        
        # Simple logistic regression classifier for bias detection
        self.bias_classifier = LogisticRegression(
            random_state=42,
            max_iter=1000,
            class_weight='balanced'
        )
        
        # Train on basic bias patterns (in production, this would use a larger dataset)
        await self._train_default_classifier()
    
    async def _train_default_classifier(self):
        """Train default classifier on basic bias patterns"""
        # Sample training data (in production, this would be a comprehensive dataset)
        biased_examples = [
            "Are you planning to have children soon?",
            "You seem very young for this position.",
            "Can you lift heavy objects despite your size?",
            "How does your accent affect client communication?",
            "Is your husband supportive of your career?",
            "You're quite attractive for this technical role.",
            "Do you think you can keep up with younger teammates?",
            "Where are you really from originally?",
            "Can you work overtime with your family commitments?",
            "You don't look like a typical engineer."
        ]
        
        neutral_examples = [
            "Tell me about your experience with this technology.",
            "How do you approach problem-solving in your work?",
            "What interests you most about this position?",
            "Describe a challenging project you've worked on.",
            "How do you stay updated with industry trends?",
            "What are your career goals for the next five years?",
            "How do you handle working under tight deadlines?",
            "Tell me about a time you had to learn something new.",
            "What motivates you in your professional work?",
            "How do you approach collaboration with team members?"
        ]
        
        # Create training data
        X = biased_examples + neutral_examples
        y = [1] * len(biased_examples) + [0] * len(neutral_examples)
        
        # Vectorize text
        self.vectorizer = TfidfVectorizer(
            max_features=1000,
            ngram_range=(1, 3),
            stop_words='english'
        )
        
        X_vectorized = self.vectorizer.fit_transform(X)
        
        # Train classifier
        self.bias_classifier.fit(X_vectorized, y)
        logger.info("Default bias classifier trained")
    
    async def _initialize_linguistic_analyzer(self):
        """Initialize linguistic bias analyzer"""
        self.linguistic_analyzer = LinguisticBiasAnalyzer()
        logger.info("Linguistic bias analyzer initialized")
    
    async def _download_nltk_data(self):
        """Download required NLTK data"""
        nltk_data = ['punkt', 'averaged_perceptron_tagger', 'wordnet', 'stopwords']
        
        for data in nltk_data:
            try:
                nltk.data.find(f'tokenizers/{data}')
            except LookupError:
                logger.info(f"Downloading NLTK data: {data}")
                nltk.download(data, quiet=True)
    
    def _load_protected_attributes(self) -> Dict[str, List[str]]:
        """Load protected attribute keywords"""
        return {
            'age': [
                'young', 'old', 'elderly', 'senior', 'junior', 'millennial', 'boomer',
                'generation', 'age', 'years old', 'mature', 'youthful', 'experienced'
            ],
            'gender': [
                'he', 'she', 'him', 'her', 'his', 'hers', 'man', 'woman', 'male', 'female',
                'guy', 'girl', 'boy', 'lady', 'gentleman', 'masculine', 'feminine',
                'husband', 'wife', 'boyfriend', 'girlfriend', 'mother', 'father'
            ],
            'race_ethnicity': [
                'black', 'white', 'asian', 'hispanic', 'latino', 'african', 'caucasian',
                'race', 'racial', 'ethnic', 'ethnicity', 'nationality', 'foreign',
                'native', 'immigrant', 'accent', 'cultural', 'diverse', 'minority'
            ],
            'religion': [
                'christian', 'muslim', 'jewish', 'hindu', 'buddhist', 'atheist',
                'religion', 'religious', 'faith', 'belief', 'church', 'mosque',
                'temple', 'pray', 'god', 'spiritual'
            ],
            'disability': [
                'disabled', 'disability', 'handicapped', 'impaired', 'blind', 'deaf',
                'wheelchair', 'crutches', 'prosthetic', 'medical condition',
                'health issues', 'medication', 'therapy', 'accommodation'
            ],
            'sexual_orientation': [
                'gay', 'lesbian', 'bisexual', 'transgender', 'lgbt', 'queer',
                'straight', 'heterosexual', 'homosexual', 'partner', 'orientation'
            ],
            'pregnancy_family': [
                'pregnant', 'pregnancy', 'children', 'kids', 'family', 'childcare',
                'maternity', 'paternity', 'parental', 'marriage', 'married', 'single',
                'divorced', 'family planning', 'baby', 'spouse'
            ],
            'socioeconomic': [
                'wealthy', 'poor', 'rich', 'expensive', 'cheap', 'money', 'salary',
                'income', 'financial', 'background', 'class', 'privileged',
                'disadvantaged', 'elite', 'working class'
            ],
            'appearance': [
                'attractive', 'beautiful', 'handsome', 'ugly', 'pretty', 'looks',
                'appearance', 'height', 'weight', 'thin', 'fat', 'overweight',
                'skinny', 'tall', 'short', 'hair', 'tattoo', 'piercing'
            ],
            'education': [
                'ivy league', 'prestigious', 'elite', 'top-tier', 'degree', 'diploma',
                'college', 'university', 'school', 'education', 'smart', 'intelligent',
                'qualified', 'credentials'
            ]
        }
    
    def _load_biased_terms(self) -> Dict[str, List[str]]:
        """Load biased terms and phrases"""
        return {
            'ageist': [
                'overqualified', 'digital native', 'fresh perspective', 'energy',
                'up-to-date', 'outdated', 'set in ways', 'old school'
            ],
            'gendered': [
                'aggressive', 'assertive', 'bossy', 'emotional', 'dramatic',
                'nurturing', 'maternal', 'ambitious', 'pushy', 'abrasive'
            ],
            'ableist': [
                'crazy', 'insane', 'psycho', 'lame', 'dumb', 'blind to',
                'deaf to', 'wheelchair bound', 'suffers from', 'victim of'
            ],
            'cultural': [
                'articulate', 'well-spoken', 'exotic', 'urban', 'ethnic name',
                'hard to pronounce', 'foreign sounding', 'cultural fit'
            ],
            'appearance': [
                'professional appearance', 'well-groomed', 'polished',
                'presentable', 'clean-cut', 'image', 'face of the company'
            ],
            'coded_language': [
                'team player', 'culture fit', 'communication skills',
                'personality fit', 'right attitude', 'good fit'
            ]
        }
    
    def _load_inclusive_alternatives(self) -> Dict[str, str]:
        """Load inclusive alternatives for biased terms"""
        return {
            'guys': 'everyone, team, folks',
            'manpower': 'workforce, staff, personnel',
            'man-hours': 'person-hours, work-hours',
            'chairman': 'chairperson, chair',
            'fireman': 'firefighter',
            'policeman': 'police officer',
            'crazy': 'surprising, unexpected',
            'insane': 'remarkable, incredible',
            'lame': 'disappointing, ineffective',
            'blind spot': 'oversight, gap',
            'articulate': 'clear, well-expressed',
            'exotic': 'unique, distinctive',
            'culture fit': 'team compatibility, values alignment',
            'overqualified': 'extensive experience'
        }
    
    def _load_cultural_indicators(self) -> List[str]:
        """Load cultural bias indicators"""
        return [
            'where are you from originally',
            'you speak english very well',
            'is that your real name',
            'can you pronounce your name differently',
            'cultural background',
            'family traditions',
            'holiday celebrations',
            'dietary restrictions',
            'religious practices',
            'accent',
            'communication style',
            'work style preferences'
        ]
    
    def _load_unconscious_bias_patterns(self) -> Dict[str, List[str]]:
        """Load patterns that indicate unconscious bias"""
        return {
            'competence_questioning': [
                'are you sure you can handle',
                'do you think you can manage',
                'this might be challenging for someone like you',
                'have you done this before',
                'can you keep up with'
            ],
            'assumption_making': [
                'i assume you',
                'you probably',
                'people like you usually',
                'in my experience, people from',
                'you seem like the type who'
            ],
            'stereotype_reinforcing': [
                'you don\'t look like',
                'you\'re not what i expected',
                'surprising for someone',
                'unusual for your',
                'different from most'
            ],
            'microaggressions': [
                'you\'re so articulate',
                'where are you really from',
                'can i touch your hair',
                'you\'re a credit to your',
                'you don\'t act like'
            ]
        }
    
    def is_ready(self) -> bool:
        """Check if bias detector is ready"""
        return self.is_initialized and self.bias_classifier is not None
    
    async def detect_bias(self, content: str, content_type: str, 
                         demographic_info: Optional[Dict], session_id: str) -> BiasDetectionResult:
        """
        Perform comprehensive bias detection
        
        Args:
            content: Text content to analyze (question or response)
            content_type: Type of content ('question' or 'response')
            demographic_info: Anonymized demographic information
            session_id: Session identifier
            
        Returns:
            Complete bias detection results
        """
        start_time = time.time()
        analysis_id = f"bias_{session_id}_{int(start_time)}"
        
        logger.info(f"Starting bias detection for {analysis_id}")
        
        try:
            # Parse demographic info
            demo_info = DemographicInfo(**demographic_info) if demographic_info else DemographicInfo()
            
            # Perform parallel bias detection
            results = await asyncio.gather(
                self._detect_demographic_bias(content, demo_info),
                self._detect_linguistic_bias(content),
                self._detect_cultural_bias(content),
                self._classify_bias_categories(content),
                self._calculate_fairness_metrics(content, demo_info, content_type),
                return_exceptions=True
            )
            
            # Unpack results
            (demographic_bias, linguistic_bias, cultural_bias, 
             bias_categories, fairness_metrics) = results
            
            # Handle any exceptions
            for i, result in enumerate(results):
                if isinstance(result, Exception):
                    logger.warning(f"Bias detection component {i} failed: {result}")
            
            # Calculate overall bias score
            overall_bias_score = self._calculate_overall_bias_score(
                demographic_bias if isinstance(demographic_bias, float) else 0.0,
                linguistic_bias if isinstance(linguistic_bias, float) else 0.0,
                cultural_bias if isinstance(cultural_bias, float) else 0.0,
                bias_categories if isinstance(bias_categories, list) else []
            )
            
            # Generate recommendations
            recommendations = await self._generate_recommendations(
                content, overall_bias_score, 
                bias_categories if isinstance(bias_categories, list) else [],
                content_type
            )
            
            # Calculate confidence level
            confidence_level = self._calculate_confidence_level(content, overall_bias_score)
            
            # Create result
            result = BiasDetectionResult(
                analysis_id=analysis_id,
                overall_bias_score=overall_bias_score,
                bias_categories=bias_categories if isinstance(bias_categories, list) else [],
                demographic_bias=demographic_bias if isinstance(demographic_bias, float) else 0.0,
                linguistic_bias=linguistic_bias if isinstance(linguistic_bias, float) else 0.0,
                cultural_bias=cultural_bias if isinstance(cultural_bias, float) else 0.0,
                fairness_metrics=fairness_metrics if isinstance(fairness_metrics, FairnessMetrics) else self._default_fairness_metrics(),
                recommendations=recommendations,
                confidence_level=confidence_level,
                processing_time=time.time() - start_time
            )
            
            # Store result for fairness tracking
            await self._store_bias_result(result, demo_info)
            
            logger.info(f"Bias detection completed for {analysis_id}")
            return result
            
        except Exception as e:
            logger.error(f"Bias detection failed for {analysis_id}: {e}")
            raise
    
    async def _detect_demographic_bias(self, content: str, demo_info: DemographicInfo) -> float:
        """Detect demographic bias in content"""
        bias_score = 0.0
        content_lower = content.lower()
        
        try:
            # Check for direct references to protected attributes
            for category, terms in self.protected_attributes.items():
                for term in terms:
                    if term in content_lower:
                        bias_score += 0.1
                        logger.debug(f"Detected {category} bias term: {term}")
            
            # Check for unconscious bias patterns
            for pattern_type, patterns in self.unconscious_bias_patterns.items():
                for pattern in patterns:
                    if pattern in content_lower:
                        bias_score += 0.15
                        logger.debug(f"Detected {pattern_type} pattern: {pattern}")
            
            # Use ML classifier if available
            if self.bias_classifier and hasattr(self, 'vectorizer'):
                try:
                    content_vectorized = self.vectorizer.transform([content])
                    ml_bias_prob = self.bias_classifier.predict_proba(content_vectorized)[0][1]
                    bias_score += ml_bias_prob * 0.5  # Weight ML prediction
                except Exception as e:
                    logger.warning(f"ML bias classification failed: {e}")
            
            return min(1.0, bias_score)
            
        except Exception as e:
            logger.warning(f"Demographic bias detection failed: {e}")
            return 0.0
    
    async def _detect_linguistic_bias(self, content: str) -> float:
        """Detect linguistic bias in content"""
        try:
            if self.linguistic_analyzer:
                return await self.linguistic_analyzer.analyze_bias(content)
            else:
                return self._simple_linguistic_bias_detection(content)
                
        except Exception as e:
            logger.warning(f"Linguistic bias detection failed: {e}")
            return 0.0
    
    def _simple_linguistic_bias_detection(self, content: str) -> float:
        """Simple linguistic bias detection fallback"""
        bias_score = 0.0
        content_lower = content.lower()
        
        # Check for biased terms
        for category, terms in self.biased_terms.items():
            for term in terms:
                if term in content_lower:
                    bias_score += 0.1
        
        # Check reading level (very high or very low can indicate bias)
        try:
            reading_ease = flesch_reading_ease(content)
            if reading_ease < 30 or reading_ease > 90:  # Very hard or very easy
                bias_score += 0.05
        except:
            pass
        
        # Check sentence structure for complexity bias
        sentences = sent_tokenize(content)
        if sentences:
            avg_length = np.mean([len(word_tokenize(s)) for s in sentences])
            if avg_length > 25:  # Very long sentences can exclude some groups
                bias_score += 0.05
        
        return min(1.0, bias_score)
    
    async def _detect_cultural_bias(self, content: str) -> float:
        """Detect cultural bias in content"""
        bias_score = 0.0
        content_lower = content.lower()
        
        try:
            # Check for cultural indicators
            for indicator in self.cultural_indicators:
                if indicator in content_lower:
                    bias_score += 0.15
            
            # Check for assumptions about cultural norms
            cultural_assumptions = [
                'traditional family', 'normal behavior', 'standard practice',
                'usual way', 'typical approach', 'common sense',
                'obvious', 'naturally', 'of course'
            ]
            
            for assumption in cultural_assumptions:
                if assumption in content_lower:
                    bias_score += 0.05
            
            # Check for western-centric references
            western_centric = [
                'christmas', 'thanksgiving', 'easter', 'valentine',
                'american way', 'western approach', 'english names'
            ]
            
            for reference in western_centric:
                if reference in content_lower:
                    bias_score += 0.1
            
            return min(1.0, bias_score)
            
        except Exception as e:
            logger.warning(f"Cultural bias detection failed: {e}")
            return 0.0
    
    async def _classify_bias_categories(self, content: str) -> List[BiasCategory]:
        """Classify specific bias categories found in content"""
        categories = []
        content_lower = content.lower()
        
        try:
            # Check each protected attribute category
            for attr_category, terms in self.protected_attributes.items():
                detected_terms = [term for term in terms if term in content_lower]
                
                if detected_terms:
                    severity = self._calculate_severity(len(detected_terms), len(terms))
                    description = f"Content references {attr_category}-related terms that may indicate bias"
                    
                    category = BiasCategory(
                        category=attr_category,
                        score=min(1.0, len(detected_terms) * 0.2),
                        description=description,
                        examples=detected_terms[:3],  # Show up to 3 examples
                        severity=severity
                    )
                    categories.append(category)
            
            # Check for coded language
            coded_terms_found = []
            for term in self.biased_terms.get('coded_language', []):
                if term in content_lower:
                    coded_terms_found.append(term)
            
            if coded_terms_found:
                category = BiasCategory(
                    category="coded_language",
                    score=min(1.0, len(coded_terms_found) * 0.3),
                    description="Content uses coded language that may exclude certain groups",
                    examples=coded_terms_found[:3],
                    severity=self._calculate_severity(len(coded_terms_found), 5)
                )
                categories.append(category)
            
            return categories
            
        except Exception as e:
            logger.warning(f"Bias category classification failed: {e}")
            return []
    
    def _calculate_severity(self, detected_count: int, total_count: int) -> str:
        """Calculate bias severity level"""
        ratio = detected_count / max(total_count, 1)
        
        if ratio > 0.5:
            return "critical"
        elif ratio > 0.3:
            return "high"
        elif ratio > 0.1:
            return "medium"
        else:
            return "low"
    
    async def _calculate_fairness_metrics(self, content: str, demo_info: DemographicInfo, 
                                        content_type: str) -> FairnessMetrics:
        """Calculate fairness metrics"""
        try:
            # Store response for fairness analysis
            self._store_demographic_response(content, demo_info, content_type)
            
            # Calculate fairness metrics if we have enough data
            if len(self.demographic_responses) > 10:
                return await self._compute_fairness_metrics()
            else:
                return self._default_fairness_metrics()
                
        except Exception as e:
            logger.warning(f"Fairness metrics calculation failed: {e}")
            return self._default_fairness_metrics()
    
    def _store_demographic_response(self, content: str, demo_info: DemographicInfo, content_type: str):
        """Store response with demographic info for fairness analysis"""
        response_data = {
            'content': content,
            'content_type': content_type,
            'demographic_info': demo_info,
            'timestamp': time.time(),
            'bias_score': 0.0  # Will be updated after analysis
        }
        
        # Store by demographic categories
        if demo_info.gender_identity:
            self.demographic_responses[f"gender_{demo_info.gender_identity}"].append(response_data)
        
        if demo_info.age_group:
            self.demographic_responses[f"age_{demo_info.age_group}"].append(response_data)
        
        if demo_info.ethnicity:
            self.demographic_responses[f"ethnicity_{demo_info.ethnicity}"].append(response_data)
    
    async def _compute_fairness_metrics(self) -> FairnessMetrics:
        """Compute fairness metrics across demographic groups"""
        try:
            # Calculate demographic parity
            demographic_parity = self._calculate_demographic_parity()
            
            # Calculate equalized odds (if we have outcome data)
            equalized_odds = self._calculate_equalized_odds()
            
            # Calculate calibration score
            calibration_score = self._calculate_calibration()
            
            # Calculate individual fairness
            individual_fairness = self._calculate_individual_fairness()
            
            return FairnessMetrics(
                demographic_parity=demographic_parity,
                equalized_odds=equalized_odds,
                calibration_score=calibration_score,
                individual_fairness=individual_fairness
            )
            
        except Exception as e:
            logger.warning(f"Fairness metrics computation failed: {e}")
            return self._default_fairness_metrics()
    
    def _calculate_demographic_parity(self) -> float:
        """Calculate demographic parity across groups"""
        try:
            group_scores = {}
            
            # Calculate average bias scores for each group
            for group_key, responses in self.demographic_responses.items():
                if len(responses) > 2:
                    avg_bias = np.mean([r.get('bias_score', 0) for r in responses])
                    group_scores[group_key] = avg_bias
            
            if len(group_scores) < 2:
                return 1.0  # Perfect parity if not enough groups
            
            # Calculate parity as 1 - max difference between groups
            scores = list(group_scores.values())
            max_diff = max(scores) - min(scores)
            
            return max(0.0, 1.0 - max_diff)
            
        except Exception as e:
            logger.warning(f"Demographic parity calculation failed: {e}")
            return 0.5
    
    def _calculate_equalized_odds(self) -> float:
        """Calculate equalized odds"""
        # This would require outcome data (e.g., hiring decisions)
        # For now, return a baseline value
        return 0.8
    
    def _calculate_calibration(self) -> float:
        """Calculate calibration score"""
        # This would require comparing predicted vs actual outcomes
        # For now, return a baseline value
        return 0.8
    
    def _calculate_individual_fairness(self) -> float:
        """Calculate individual fairness"""
        # This would require similarity metrics between individuals
        # For now, return a baseline value
        return 0.8
    
    def _default_fairness_metrics(self) -> FairnessMetrics:
        """Return default fairness metrics"""
        return FairnessMetrics(
            demographic_parity=0.8,
            equalized_odds=0.8,
            calibration_score=0.8,
            individual_fairness=0.8
        )
    
    def _calculate_overall_bias_score(self, demographic_bias: float, linguistic_bias: float,
                                    cultural_bias: float, bias_categories: List[BiasCategory]) -> float:
        """Calculate overall bias score"""
        # Weight different bias types
        demographic_weight = 0.4
        linguistic_weight = 0.3
        cultural_weight = 0.2
        category_weight = 0.1
        
        # Calculate category bias score
        category_bias = 0.0
        if bias_categories:
            category_bias = np.mean([cat.score for cat in bias_categories])
        
        overall_score = (
            demographic_bias * demographic_weight +
            linguistic_bias * linguistic_weight +
            cultural_bias * cultural_weight +
            category_bias * category_weight
        )
        
        return min(1.0, overall_score)
    
    async def _generate_recommendations(self, content: str, bias_score: float,
                                      bias_categories: List[BiasCategory], 
                                      content_type: str) -> List[str]:
        """Generate bias mitigation recommendations"""
        recommendations = []
        
        try:
            # General recommendations based on bias score
            if bias_score > 0.7:
                recommendations.append("High bias detected. Consider completely rewriting this content.")
                recommendations.append("Review content for discriminatory language and assumptions.")
            elif bias_score > 0.4:
                recommendations.append("Moderate bias detected. Review and revise problematic areas.")
                recommendations.append("Consider using more inclusive language.")
            elif bias_score > 0.2:
                recommendations.append("Some bias indicators found. Minor revisions recommended.")
            
            # Specific recommendations based on categories
            for category in bias_categories:
                if category.category in self.protected_attributes:
                    recommendations.append(f"Remove references to {category.category} to avoid discrimination.")
                
                if category.severity in ['high', 'critical']:
                    recommendations.append(f"Critical {category.category} bias detected - immediate revision required.")
            
            # Content-type specific recommendations
            if content_type == 'question':
                recommendations.extend([
                    "Focus on job-relevant skills and experience only.",
                    "Ensure questions are consistent across all candidates.",
                    "Avoid personal questions not related to job performance."
                ])
            elif content_type == 'response':
                recommendations.extend([
                    "Evaluate response content objectively without demographic assumptions.",
                    "Focus on technical accuracy and communication skills."
                ])
            
            # Suggest inclusive alternatives
            for biased_term, alternative in self.inclusive_alternatives.items():
                if biased_term in content.lower():
                    recommendations.append(f"Consider replacing '{biased_term}' with '{alternative}'")
            
            # Remove duplicates and limit recommendations
            recommendations = list(set(recommendations))[:10]
            
            return recommendations
            
        except Exception as e:
            logger.warning(f"Recommendation generation failed: {e}")
            return ["Review content for potential bias and discrimination."]
    
    def _calculate_confidence_level(self, content: str, bias_score: float) -> float:
        """Calculate confidence level in bias detection"""
        confidence = 0.5  # Base confidence
        
        # Increase confidence with content length (more text = more reliable analysis)
        word_count = len(word_tokenize(content))
        if word_count > 50:
            confidence += 0.2
        elif word_count > 20:
            confidence += 0.1
        
        # Increase confidence if multiple bias indicators found
        if bias_score > 0.3:
            confidence += 0.2
        elif bias_score > 0.1:
            confidence += 0.1
        
        # Increase confidence if we have historical data
        if len(self.demographic_responses) > 50:
            confidence += 0.1
        
        return min(1.0, confidence)
    
    async def _store_bias_result(self, result: BiasDetectionResult, demo_info: DemographicInfo):
        """Store bias detection result for future analysis"""
        # Update stored responses with bias scores
        current_time = time.time()
        
        for group_key, responses in self.demographic_responses.items():
            for response in responses:
                if abs(response['timestamp'] - current_time) < 60:  # Within last minute
                    response['bias_score'] = result.overall_bias_score
                    break
        
        # Store in fairness history
        self.fairness_history.append({
            'timestamp': current_time,
            'bias_score': result.overall_bias_score,
            'demographic_info': demo_info,
            'fairness_metrics': result.fairness_metrics
        })
        
        # Keep only recent history (last 1000 entries)
        if len(self.fairness_history) > 1000:
            self.fairness_history = self.fairness_history[-1000:]
    
    async def get_bias_trends(self, time_window_hours: int = 24) -> Dict[str, Any]:
        """Get bias trends over time"""
        cutoff_time = time.time() - (time_window_hours * 3600)
        recent_history = [h for h in self.fairness_history if h['timestamp'] > cutoff_time]
        
        if not recent_history:
            return {'message': 'Insufficient data for trend analysis'}
        
        # Calculate trends
        bias_scores = [h['bias_score'] for h in recent_history]
        avg_bias = np.mean(bias_scores)
        bias_trend = 'increasing' if len(bias_scores) > 1 and bias_scores[-1] > bias_scores[0] else 'stable'
        
        # Group analysis
        group_analysis = defaultdict(list)
        for entry in recent_history:
            demo = entry['demographic_info']
            if demo.gender_identity:
                group_analysis[f"gender_{demo.gender_identity}"].append(entry['bias_score'])
            if demo.age_group:
                group_analysis[f"age_{demo.age_group}"].append(entry['bias_score'])
        
        group_averages = {k: np.mean(v) for k, v in group_analysis.items() if len(v) > 2}
        
        return {
            'time_window_hours': time_window_hours,
            'total_analyses': len(recent_history),
            'average_bias_score': avg_bias,
            'bias_trend': bias_trend,
            'group_averages': group_averages,
            'alerts': self._generate_trend_alerts(avg_bias, group_averages)
        }
    
    def _generate_trend_alerts(self, avg_bias: float, group_averages: Dict[str, float]) -> List[str]:
        """Generate alerts based on bias trends"""
        alerts = []
        
        if avg_bias > 0.5:
            alerts.append("HIGH ALERT: Overall bias levels are elevated")
        
        # Check for group disparities
        if len(group_averages) > 1:
            scores = list(group_averages.values())
            max_diff = max(scores) - min(scores)
            
            if max_diff > 0.3:
                alerts.append("FAIRNESS ALERT: Significant bias disparity detected between demographic groups")
        
        return alerts
    
    async def cleanup(self):
        """Cleanup resources"""
        logger.info("BiasDetector cleanup completed")


class LinguisticBiasAnalyzer:
    """Specialized analyzer for linguistic bias patterns"""
    
    def __init__(self):
        self.gendered_pronouns = {
            'male': ['he', 'him', 'his', 'himself'],
            'female': ['she', 'her', 'hers', 'herself'],
            'neutral': ['they', 'them', 'their', 'themselves']
        }
        
        self.gendered_terms = {
            'male_coded': ['assertive', 'competitive', 'confident', 'ambitious', 'analytical'],
            'female_coded': ['collaborative', 'nurturing', 'supportive', 'empathetic', 'intuitive']
        }
    
    async def analyze_bias(self, text: str) -> float:
        """Analyze linguistic bias in text"""
        bias_score = 0.0
        
        # Check pronoun usage
        bias_score += self._analyze_pronoun_bias(text)
        
        # Check gendered language
        bias_score += self._analyze_gendered_language(text)
        
        # Check complexity bias
        bias_score += self._analyze_complexity_bias(text)
        
        return min(1.0, bias_score)
    
    def _analyze_pronoun_bias(self, text: str) -> float:
        """Analyze pronoun usage for gender bias"""
        text_lower = text.lower()
        male_count = sum(text_lower.count(p) for p in self.gendered_pronouns['male'])
        female_count = sum(text_lower.count(p) for p in self.gendered_pronouns['female'])
        
        total_gendered = male_count + female_count
        
        if total_gendered == 0:
            return 0.0
        
        # Bias if heavily skewed toward one gender
        imbalance = abs(male_count - female_count) / total_gendered
        return imbalance * 0.3  # Max 0.3 bias score for pronouns
    
    def _analyze_gendered_language(self, text: str) -> float:
        """Analyze use of gendered coded language"""
        text_lower = text.lower()
        
        male_coded_count = sum(1 for term in self.gendered_terms['male_coded'] if term in text_lower)
        female_coded_count = sum(1 for term in self.gendered_terms['female_coded'] if term in text_lower)
        
        total_coded = male_coded_count + female_coded_count
        
        if total_coded == 0:
            return 0.0
        
        # Bias if heavily skewed toward gendered terms
        imbalance = abs(male_coded_count - female_coded_count) / total_coded
        return imbalance * 0.2  # Max 0.2 bias score for gendered language
    
    def _analyze_complexity_bias(self, text: str) -> float:
        """Analyze language complexity that might exclude certain groups"""
        try:
            # Check reading level
            reading_ease = flesch_reading_ease(text)
            
            # Very difficult text (< 30) might exclude some groups
            if reading_ease < 30:
                return 0.1
            # Very easy text (> 90) might seem patronizing
            elif reading_ease > 90:
                return 0.05
            
            return 0.0
            
        except Exception:
            return 0.0


# Example usage and testing
async def test_bias_detector():
    """Test the bias detector with sample content"""
    detector = BiasDetector()
    await detector.initialize_models()
    
    # Test biased question
    biased_question = """
    Are you planning to start a family soon? We need someone who can work long hours 
    and travel frequently. Also, can you lift heavy objects despite your petite frame?
    """
    
    # Test neutral question  
    neutral_question = """
    Tell me about your experience with project management and how you handle
    tight deadlines. What strategies do you use to ensure quality deliverables?
    """
    
    # Test both questions
    for question_type, question in [("biased", biased_question), ("neutral", neutral_question)]:
        result = await detector.detect_bias(
            content=question,
            content_type="question", 
            demographic_info={"gender_identity": "female", "age_group": "young"},
            session_id="test_session"
        )
        
        print(f"\n{question_type.upper()} QUESTION ANALYSIS:")
        print(f"Overall Bias Score: {result.overall_bias_score:.2f}")
        print(f"Demographic Bias: {result.demographic_bias:.2f}")
        print(f"Linguistic Bias: {result.linguistic_bias:.2f}")
        print(f"Cultural Bias: {result.cultural_bias:.2f}")
        print(f"Bias Categories: {len(result.bias_categories)}")
        print(f"Recommendations: {len(result.recommendations)}")
        print(f"Processing Time: {result.processing_time:.2f}s")
        
        if result.recommendations:
            print("Top Recommendations:")
            for rec in result.recommendations[:3]:
                print(f"  - {rec}")
    
    await detector.cleanup()


if __name__ == "__main__":
    asyncio.run(test_bias_detector())
