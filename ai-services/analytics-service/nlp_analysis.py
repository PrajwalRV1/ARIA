#!/usr/bin/env python3
"""
ARIA NLP Analysis Module

Advanced natural language processing for interview text analysis including:
- Sentiment analysis and emotion detection
- Technical accuracy assessment
- Communication quality evaluation
- Language complexity analysis
- Bias detection in text
- Named entity recognition
- Key topic extraction
- Professionalism scoring
"""

import asyncio
import logging
import re
import time
from typing import Dict, List, Optional, Tuple, Any
from dataclasses import dataclass
from collections import Counter
import numpy as np
import pandas as pd

# HuggingFace Transformers
from transformers import (
    AutoTokenizer, AutoModelForSequenceClassification,
    AutoModelForTokenClassification, pipeline,
    BertTokenizer, BertForSequenceClassification
)
import torch
import torch.nn.functional as F

# NLTK for text processing
import nltk
from nltk.sentiment import SentimentIntensityAnalyzer
from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize, sent_tokenize
from nltk.tag import pos_tag
from nltk.chunk import ne_chunk
from nltk.tree import Tree

# Spacy for advanced NLP
import spacy
from spacy import displacy

# TextBlob for additional sentiment analysis
from textblob import TextBlob

# Scikit-learn for text analysis
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.decomposition import LatentDirichletAllocation

# Custom modules
from models import EmotionScores

logger = logging.getLogger(__name__)


@dataclass
class TextAnalysisResult:
    """Complete text analysis result"""
    analysis_id: str
    sentiment_score: float
    sentiment_label: str
    emotion_scores: EmotionScores
    toxicity_score: float
    professionalism_score: float
    coherence_score: float
    technical_accuracy: float
    key_topics: List[str]
    named_entities: List[Dict[str, str]]
    language_complexity: float
    bias_indicators: List[str]
    processing_time: float


class NLPAnalyzer:
    """Advanced NLP analysis using multiple models and techniques"""
    
    def __init__(self, model_name: str = "cardiffnlp/twitter-roberta-base-sentiment-latest",
                 max_length: int = 10000, batch_size: int = 32):
        """
        Initialize NLP analyzer with transformers models
        
        Args:
            model_name: HuggingFace model for sentiment analysis
            max_length: Maximum text length to process
            batch_size: Batch size for model inference
        """
        self.model_name = model_name
        self.max_length = max_length
        self.batch_size = batch_size
        
        # Model components
        self.sentiment_analyzer = None
        self.emotion_analyzer = None
        self.toxicity_analyzer = None
        self.ner_model = None
        self.tokenizer = None
        self.sentiment_model = None
        
        # NLTK components
        self.sia = None  # Sentiment Intensity Analyzer
        self.stop_words = None
        
        # Spacy components
        self.nlp_model = None
        
        # Technical vocabulary
        self.technical_terms = self._load_technical_vocabulary()
        self.professional_terms = self._load_professional_vocabulary()
        self.bias_keywords = self._load_bias_keywords()
        
        # Text analysis tools
        self.tfidf_vectorizer = TfidfVectorizer(
            max_features=1000, 
            stop_words='english',
            ngram_range=(1, 3)
        )
        
        self.topic_model = LatentDirichletAllocation(
            n_components=10,
            random_state=42
        )
        
        # Analysis state
        self.is_initialized = False
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        
        logger.info("NLPAnalyzer initialized")
    
    async def initialize_models(self):
        """Load and initialize all NLP models"""
        try:
            logger.info("Loading NLP models...")
            
            # Download NLTK data
            await self._download_nltk_data()
            
            # Initialize HuggingFace pipelines
            await self._initialize_transformers()
            
            # Initialize NLTK components
            await self._initialize_nltk()
            
            # Initialize Spacy
            await self._initialize_spacy()
            
            self.is_initialized = True
            logger.info("All NLP models initialized successfully")
            
        except Exception as e:
            logger.error(f"Failed to initialize NLP models: {e}")
            raise
    
    async def _download_nltk_data(self):
        """Download required NLTK data"""
        nltk_data = [
            'vader_lexicon', 'punkt', 'averaged_perceptron_tagger',
            'stopwords', 'words', 'maxent_ne_chunker'
        ]
        
        for data in nltk_data:
            try:
                nltk.data.find(f'tokenizers/{data}')
            except LookupError:
                logger.info(f"Downloading NLTK data: {data}")
                nltk.download(data, quiet=True)
    
    async def _initialize_transformers(self):
        """Initialize HuggingFace transformers models"""
        try:
            # Sentiment analysis pipeline
            self.sentiment_analyzer = pipeline(
                "sentiment-analysis",
                model=self.model_name,
                device=0 if torch.cuda.is_available() else -1
            )
            
            # Emotion analysis pipeline
            self.emotion_analyzer = pipeline(
                "text-classification",
                model="j-hartmann/emotion-english-distilroberta-base",
                device=0 if torch.cuda.is_available() else -1
            )
            
            # Toxicity detection pipeline
            self.toxicity_analyzer = pipeline(
                "text-classification",
                model="unitary/toxic-bert",
                device=0 if torch.cuda.is_available() else -1
            )
            
            # Named Entity Recognition
            self.ner_model = pipeline(
                "ner",
                model="dbmdz/bert-large-cased-finetuned-conll03-english",
                aggregation_strategy="simple",
                device=0 if torch.cuda.is_available() else -1
            )
            
            # Load tokenizer and model for custom analysis
            self.tokenizer = AutoTokenizer.from_pretrained(self.model_name)
            self.sentiment_model = AutoModelForSequenceClassification.from_pretrained(
                self.model_name
            ).to(self.device)
            
            logger.info("HuggingFace models initialized")
            
        except Exception as e:
            logger.warning(f"Some HuggingFace models failed to load: {e}")
            # Create fallback analyzers
            await self._create_fallback_analyzers()
    
    async def _create_fallback_analyzers(self):
        """Create fallback analyzers if HuggingFace models fail"""
        logger.info("Creating fallback analyzers")
        
        # Simple sentiment analyzer using TextBlob
        self.sentiment_analyzer = self._textblob_sentiment
        self.emotion_analyzer = self._simple_emotion_analysis
        self.toxicity_analyzer = self._simple_toxicity_analysis
        self.ner_model = self._simple_ner
    
    def _textblob_sentiment(self, text: str) -> Dict:
        """Fallback sentiment analysis using TextBlob"""
        blob = TextBlob(text)
        polarity = blob.sentiment.polarity
        
        if polarity > 0.1:
            label = "POSITIVE"
        elif polarity < -0.1:
            label = "NEGATIVE"
        else:
            label = "NEUTRAL"
        
        return [{"label": label, "score": abs(polarity)}]
    
    def _simple_emotion_analysis(self, text: str) -> List[Dict]:
        """Simple emotion analysis fallback"""
        # Basic keyword-based emotion detection
        emotions = {
            'joy': ['happy', 'excited', 'pleased', 'thrilled', 'delighted'],
            'sadness': ['sad', 'disappointed', 'upset', 'unhappy'],
            'anger': ['angry', 'frustrated', 'annoyed', 'furious'],
            'fear': ['worried', 'anxious', 'nervous', 'concerned', 'afraid'],
            'surprise': ['surprised', 'amazed', 'shocked', 'astonished'],
            'disgust': ['disgusted', 'revolted', 'appalled']
        }
        
        text_lower = text.lower()
        emotion_scores = {}
        
        for emotion, keywords in emotions.items():
            score = sum(1 for keyword in keywords if keyword in text_lower)
            emotion_scores[emotion] = score / len(keywords)
        
        return [{"label": max(emotion_scores.keys(), key=emotion_scores.get), 
                "score": max(emotion_scores.values())}]
    
    def _simple_toxicity_analysis(self, text: str) -> List[Dict]:
        """Simple toxicity analysis fallback"""
        toxic_words = ['hate', 'stupid', 'idiot', 'terrible', 'awful', 'horrible']
        text_lower = text.lower()
        
        toxic_count = sum(1 for word in toxic_words if word in text_lower)
        toxicity_score = min(1.0, toxic_count / 10.0)
        
        return [{"label": "TOXIC" if toxicity_score > 0.5 else "NON_TOXIC", 
                "score": toxicity_score}]
    
    def _simple_ner(self, text: str) -> List[Dict]:
        """Simple NER fallback using NLTK"""
        try:
            tokens = word_tokenize(text)
            pos_tags = pos_tag(tokens)
            chunks = ne_chunk(pos_tags)
            
            entities = []
            for chunk in chunks:
                if isinstance(chunk, Tree):
                    entity_name = ' '.join([token for token, pos in chunk.leaves()])
                    entity_type = chunk.label()
                    entities.append({
                        'entity_group': entity_type,
                        'word': entity_name,
                        'start': 0,
                        'end': len(entity_name),
                        'score': 0.9
                    })
            
            return entities
            
        except Exception:
            return []
    
    async def _initialize_nltk(self):
        """Initialize NLTK components"""
        try:
            self.sia = SentimentIntensityAnalyzer()
            self.stop_words = set(stopwords.words('english'))
            logger.info("NLTK components initialized")
            
        except Exception as e:
            logger.warning(f"NLTK initialization failed: {e}")
    
    async def _initialize_spacy(self):
        """Initialize Spacy model"""
        try:
            # Try to load spacy model
            self.nlp_model = spacy.load("en_core_web_sm")
            logger.info("Spacy model loaded")
            
        except OSError:
            logger.warning("Spacy model not found. Install with: python -m spacy download en_core_web_sm")
            self.nlp_model = None
    
    def _load_technical_vocabulary(self) -> List[str]:
        """Load technical vocabulary for accuracy assessment"""
        return [
            # Programming concepts
            'algorithm', 'data structure', 'complexity', 'recursion', 'iteration',
            'polymorphism', 'inheritance', 'encapsulation', 'abstraction',
            'synchronization', 'asynchronous', 'multithreading', 'concurrency',
            'scalability', 'optimization', 'refactoring', 'debugging',
            
            # Technologies
            'javascript', 'python', 'java', 'react', 'angular', 'vue',
            'nodejs', 'express', 'django', 'flask', 'spring', 'hibernate',
            'mongodb', 'postgresql', 'mysql', 'redis', 'elasticsearch',
            'docker', 'kubernetes', 'aws', 'azure', 'gcp', 'microservices',
            'restful', 'graphql', 'websocket', 'oauth', 'jwt', 'ssl', 'https',
            
            # Methodologies
            'agile', 'scrum', 'kanban', 'devops', 'ci/cd', 'tdd', 'bdd',
            'mvc', 'mvvm', 'solid', 'design patterns', 'clean code',
            
            # System design
            'load balancer', 'caching', 'database sharding', 'replication',
            'consistency', 'availability', 'partition tolerance', 'cap theorem',
            'eventual consistency', 'acid properties', 'indexing', 'optimization'
        ]
    
    def _load_professional_vocabulary(self) -> List[str]:
        """Load professional vocabulary for communication assessment"""
        return [
            'collaborate', 'communicate', 'leadership', 'teamwork', 'initiative',
            'responsibility', 'accountability', 'problem-solving', 'critical thinking',
            'analytical', 'strategic', 'innovative', 'adaptable', 'flexible',
            'professional', 'reliable', 'efficient', 'effective', 'productive',
            'quality', 'excellence', 'improvement', 'optimization', 'best practices',
            'stakeholder', 'client', 'customer', 'user experience', 'requirements',
            'specification', 'documentation', 'presentation', 'demonstration',
            'feedback', 'review', 'evaluation', 'assessment', 'metrics', 'kpi'
        ]
    
    def _load_bias_keywords(self) -> Dict[str, List[str]]:
        """Load bias keywords for detection"""
        return {
            'age_bias': ['young', 'old', 'millennial', 'boomer', 'generation', 'experienced'],
            'gender_bias': ['he', 'she', 'guy', 'girl', 'man', 'woman', 'masculine', 'feminine'],
            'racial_bias': ['race', 'ethnicity', 'nationality', 'accent', 'foreign', 'native'],
            'religious_bias': ['religion', 'faith', 'belief', 'christian', 'muslim', 'jewish'],
            'appearance_bias': ['attractive', 'beautiful', 'handsome', 'appearance', 'looks'],
            'socioeconomic_bias': ['wealthy', 'poor', 'privileged', 'background', 'class'],
            'education_bias': ['ivy league', 'prestigious', 'elite', 'degree', 'college'],
            'family_bias': ['married', 'single', 'children', 'family', 'pregnant', 'mother', 'father']
        }
    
    def is_ready(self) -> bool:
        """Check if analyzer is ready for processing"""
        return self.is_initialized
    
    async def analyze_text(self, text: str, session_id: str, candidate_id: int,
                          context: Dict) -> TextAnalysisResult:
        """
        Perform comprehensive text analysis
        
        Args:
            text: Text to analyze
            session_id: Interview session ID
            candidate_id: Candidate ID
            context: Additional context information
            
        Returns:
            Complete text analysis results
        """
        start_time = time.time()
        analysis_id = f"{session_id}_{candidate_id}_{int(start_time)}"
        
        logger.info(f"Starting text analysis for {analysis_id}")
        
        try:
            # Preprocess text
            cleaned_text = self._preprocess_text(text)
            
            # Perform parallel analysis
            results = await asyncio.gather(
                self._analyze_sentiment(cleaned_text),
                self._analyze_emotions(cleaned_text),
                self._analyze_toxicity(cleaned_text),
                self._analyze_professionalism(cleaned_text),
                self._analyze_coherence(cleaned_text),
                self._analyze_technical_accuracy(cleaned_text, context),
                self._extract_key_topics(cleaned_text),
                self._extract_named_entities(cleaned_text),
                self._analyze_language_complexity(cleaned_text),
                self._detect_bias_indicators(cleaned_text),
                return_exceptions=True
            )
            
            # Unpack results
            (sentiment_result, emotion_result, toxicity_result, 
             professionalism_score, coherence_score, technical_accuracy,
             key_topics, named_entities, complexity_score, bias_indicators) = results
            
            # Handle any exceptions
            for i, result in enumerate(results):
                if isinstance(result, Exception):
                    logger.warning(f"Analysis component {i} failed: {result}")
            
            # Create emotion scores
            emotion_scores = self._create_emotion_scores(emotion_result)
            
            # Create final result
            analysis_result = TextAnalysisResult(
                analysis_id=analysis_id,
                sentiment_score=sentiment_result.get('score', 0.0) if isinstance(sentiment_result, dict) else 0.0,
                sentiment_label=sentiment_result.get('label', 'NEUTRAL') if isinstance(sentiment_result, dict) else 'NEUTRAL',
                emotion_scores=emotion_scores,
                toxicity_score=toxicity_result.get('score', 0.0) if isinstance(toxicity_result, dict) else 0.0,
                professionalism_score=professionalism_score if isinstance(professionalism_score, float) else 0.5,
                coherence_score=coherence_score if isinstance(coherence_score, float) else 0.5,
                technical_accuracy=technical_accuracy if isinstance(technical_accuracy, float) else 0.5,
                key_topics=key_topics if isinstance(key_topics, list) else [],
                named_entities=named_entities if isinstance(named_entities, list) else [],
                language_complexity=complexity_score if isinstance(complexity_score, float) else 0.5,
                bias_indicators=bias_indicators if isinstance(bias_indicators, list) else [],
                processing_time=time.time() - start_time
            )
            
            logger.info(f"Text analysis completed for {analysis_id}")
            return analysis_result
            
        except Exception as e:
            logger.error(f"Text analysis failed for {analysis_id}: {e}")
            raise
    
    def _preprocess_text(self, text: str) -> str:
        """Preprocess text for analysis"""
        # Remove extra whitespace
        text = ' '.join(text.split())
        
        # Remove URLs
        text = re.sub(r'http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*\\(\\),]|(?:%[0-9a-fA-F][0-9a-fA-F]))+', '', text)
        
        # Remove email addresses
        text = re.sub(r'\S+@\S+', '', text)
        
        # Remove excessive punctuation
        text = re.sub(r'[!]{2,}', '!', text)
        text = re.sub(r'[?]{2,}', '?', text)
        
        # Limit length
        if len(text) > self.max_length:
            text = text[:self.max_length]
        
        return text
    
    async def _analyze_sentiment(self, text: str) -> Dict:
        """Analyze sentiment using multiple approaches"""
        try:
            # Primary sentiment analysis
            if callable(self.sentiment_analyzer):
                # Fallback analyzer
                results = self.sentiment_analyzer(text)
            else:
                # HuggingFace pipeline
                results = self.sentiment_analyzer(text)
            
            # NLTK VADER sentiment as backup
            if self.sia:
                vader_scores = self.sia.polarity_scores(text)
                compound_score = vader_scores['compound']
                
                # Normalize VADER score to match HuggingFace format
                if compound_score >= 0.05:
                    vader_label = "POSITIVE"
                elif compound_score <= -0.05:
                    vader_label = "NEGATIVE"
                else:
                    vader_label = "NEUTRAL"
                
                # Combine with primary analysis if available
                if results and isinstance(results, list) and results:
                    primary_score = results[0]['score']
                    primary_label = results[0]['label']
                    
                    # Weight the scores (70% primary, 30% VADER)
                    combined_score = 0.7 * primary_score + 0.3 * abs(compound_score)
                    
                    return {
                        'score': combined_score,
                        'label': primary_label,
                        'confidence': combined_score,
                        'vader_compound': compound_score
                    }
                else:
                    return {
                        'score': abs(compound_score),
                        'label': vader_label,
                        'confidence': abs(compound_score),
                        'vader_compound': compound_score
                    }
            
            # Return primary result
            if results and isinstance(results, list) and results:
                return {
                    'score': results[0]['score'],
                    'label': results[0]['label'],
                    'confidence': results[0]['score']
                }
            
            # Default neutral sentiment
            return {'score': 0.0, 'label': 'NEUTRAL', 'confidence': 0.5}
            
        except Exception as e:
            logger.warning(f"Sentiment analysis failed: {e}")
            return {'score': 0.0, 'label': 'NEUTRAL', 'confidence': 0.0}
    
    async def _analyze_emotions(self, text: str) -> List[Dict]:
        """Analyze emotions in text"""
        try:
            if callable(self.emotion_analyzer):
                # Fallback analyzer
                return self.emotion_analyzer(text)
            else:
                # HuggingFace pipeline
                return self.emotion_analyzer(text)
            
        except Exception as e:
            logger.warning(f"Emotion analysis failed: {e}")
            return [{'label': 'neutral', 'score': 0.5}]
    
    async def _analyze_toxicity(self, text: str) -> Dict:
        """Analyze text toxicity"""
        try:
            if callable(self.toxicity_analyzer):
                # Fallback analyzer
                results = self.toxicity_analyzer(text)
            else:
                # HuggingFace pipeline
                results = self.toxicity_analyzer(text)
            
            if results and isinstance(results, list) and results:
                result = results[0]
                # Normalize toxicity score
                if result['label'] == 'TOXIC':
                    return {'score': result['score'], 'is_toxic': True}
                else:
                    return {'score': 1.0 - result['score'], 'is_toxic': False}
            
            return {'score': 0.0, 'is_toxic': False}
            
        except Exception as e:
            logger.warning(f"Toxicity analysis failed: {e}")
            return {'score': 0.0, 'is_toxic': False}
    
    async def _analyze_professionalism(self, text: str) -> float:
        """Analyze professionalism level of text"""
        try:
            score = 0.0
            total_words = len(word_tokenize(text))
            
            if total_words == 0:
                return 0.5
            
            # Check for professional vocabulary
            professional_word_count = 0
            casual_indicators = ['like', 'um', 'uh', 'yeah', 'okay', 'basically', 'actually']
            casual_count = 0
            
            words = word_tokenize(text.lower())
            
            for word in words:
                if word in self.professional_terms:
                    professional_word_count += 1
                if word in casual_indicators:
                    casual_count += 1
            
            # Calculate professional vocabulary ratio
            prof_ratio = professional_word_count / total_words
            score += min(0.4, prof_ratio * 10)  # Max 0.4 points
            
            # Penalize excessive casual language
            casual_ratio = casual_count / total_words
            score -= min(0.2, casual_ratio * 5)  # Max 0.2 penalty
            
            # Check sentence structure and grammar
            sentences = sent_tokenize(text)
            if sentences:
                avg_sentence_length = total_words / len(sentences)
                # Optimal sentence length for professional communication
                if 10 <= avg_sentence_length <= 25:
                    score += 0.3
                elif avg_sentence_length > 5:
                    score += 0.15
            
            # Check for proper capitalization
            capitalized_sentences = sum(1 for sent in sentences if sent and sent[0].isupper())
            if sentences:
                cap_ratio = capitalized_sentences / len(sentences)
                score += cap_ratio * 0.2
            
            # Check for question marks and exclamation points (moderate use is good)
            question_count = text.count('?')
            exclamation_count = text.count('!')
            total_marks = question_count + exclamation_count
            
            if total_marks > 0:
                if total_marks / len(sentences) <= 0.3:  # Moderate use
                    score += 0.1
                else:  # Excessive use
                    score -= 0.1
            
            return max(0.0, min(1.0, score + 0.5))  # Base score of 0.5
            
        except Exception as e:
            logger.warning(f"Professionalism analysis failed: {e}")
            return 0.5
    
    async def _analyze_coherence(self, text: str) -> float:
        """Analyze text coherence and flow"""
        try:
            sentences = sent_tokenize(text)
            if len(sentences) < 2:
                return 0.5
            
            # Analyze sentence transitions
            transition_words = [
                'however', 'therefore', 'furthermore', 'moreover', 'additionally',
                'consequently', 'meanwhile', 'nevertheless', 'thus', 'hence',
                'first', 'second', 'third', 'finally', 'in conclusion',
                'for example', 'specifically', 'in particular', 'such as'
            ]
            
            transition_count = 0
            for word in word_tokenize(text.lower()):
                if word in transition_words:
                    transition_count += 1
            
            # Calculate coherence metrics
            coherence_score = 0.0
            
            # Transition words indicate good flow
            transition_ratio = transition_count / len(sentences)
            coherence_score += min(0.3, transition_ratio * 2)
            
            # Check for repeated sentence patterns
            sentence_lengths = [len(word_tokenize(sent)) for sent in sentences]
            if sentence_lengths:
                length_variance = np.var(sentence_lengths)
                # Moderate variance indicates good rhythm
                if 5 <= length_variance <= 25:
                    coherence_score += 0.2
                elif length_variance > 0:
                    coherence_score += 0.1
            
            # Check for pronouns indicating continuity
            pronouns = ['it', 'this', 'that', 'these', 'those', 'they', 'them']
            pronoun_count = sum(1 for word in word_tokenize(text.lower()) if word in pronouns)
            pronoun_ratio = pronoun_count / len(word_tokenize(text))
            coherence_score += min(0.2, pronoun_ratio * 5)
            
            # Penalty for very short or very long responses without structure
            total_words = len(word_tokenize(text))
            if total_words < 10:
                coherence_score -= 0.2
            elif total_words > 500 and transition_count == 0:
                coherence_score -= 0.1
            
            return max(0.0, min(1.0, coherence_score + 0.3))  # Base score
            
        except Exception as e:
            logger.warning(f"Coherence analysis failed: {e}")
            return 0.5
    
    async def _analyze_technical_accuracy(self, text: str, context: Dict) -> float:
        """Analyze technical accuracy of response"""
        try:
            words = word_tokenize(text.lower())
            total_words = len(words)
            
            if total_words == 0:
                return 0.0
            
            technical_score = 0.0
            
            # Count technical terms
            technical_word_count = sum(1 for word in words if word in self.technical_terms)
            tech_ratio = technical_word_count / total_words
            technical_score += min(0.4, tech_ratio * 5)
            
            # Check for specific technical concepts based on context
            question_topic = context.get('topic', '').lower()
            
            if 'algorithm' in question_topic or 'data structure' in question_topic:
                algo_terms = ['complexity', 'big o', 'time complexity', 'space complexity', 
                             'logarithmic', 'linear', 'quadratic', 'exponential']
                algo_mentions = sum(1 for term in algo_terms if term in text.lower())
                if algo_mentions > 0:
                    technical_score += 0.2
            
            elif 'system design' in question_topic:
                system_terms = ['scalability', 'load balancer', 'database', 'caching',
                               'microservices', 'api', 'availability', 'consistency']
                system_mentions = sum(1 for term in system_terms if term in text.lower())
                if system_mentions > 0:
                    technical_score += 0.2
            
            elif 'database' in question_topic:
                db_terms = ['sql', 'nosql', 'acid', 'transaction', 'index', 'normalization',
                           'query optimization', 'joins', 'relationship']
                db_mentions = sum(1 for term in db_terms if term in text.lower())
                if db_mentions > 0:
                    technical_score += 0.2
            
            # Check for code examples or pseudocode
            code_indicators = ['function', 'class', 'method', 'variable', 'loop', 'if', 'else',
                              'return', 'import', 'def', 'const', 'let', 'var']
            code_mentions = sum(1 for term in code_indicators if term in text.lower())
            if code_mentions > 0:
                technical_score += min(0.2, code_mentions * 0.05)
            
            # Check for technical explanations
            explanation_terms = ['because', 'therefore', 'due to', 'as a result', 'since',
                               'this means', 'in other words', 'for instance', 'for example']
            explanation_count = sum(1 for term in explanation_terms if term in text.lower())
            if explanation_count > 0:
                technical_score += 0.1
            
            return max(0.0, min(1.0, technical_score + 0.1))  # Small base score
            
        except Exception as e:
            logger.warning(f"Technical accuracy analysis failed: {e}")
            return 0.5
    
    async def _extract_key_topics(self, text: str) -> List[str]:
        """Extract key topics from text"""
        try:
            # Use TF-IDF to find important terms
            words = word_tokenize(text.lower())
            filtered_words = [word for word in words if word.isalpha() and word not in self.stop_words]
            
            # Get word frequencies
            word_freq = Counter(filtered_words)
            
            # Extract top terms
            top_words = [word for word, freq in word_freq.most_common(10) if freq > 1]
            
            # Add technical terms found in text
            tech_terms_found = [term for term in self.technical_terms if term in text.lower()]
            
            # Combine and deduplicate
            all_topics = list(set(top_words + tech_terms_found))
            
            return all_topics[:15]  # Return top 15 topics
            
        except Exception as e:
            logger.warning(f"Topic extraction failed: {e}")
            return []
    
    async def _extract_named_entities(self, text: str) -> List[Dict[str, str]]:
        """Extract named entities from text"""
        try:
            if callable(self.ner_model):
                # Fallback NER
                return self.ner_model(text)
            else:
                # HuggingFace NER
                entities = self.ner_model(text)
                
                # Format entities
                formatted_entities = []
                for entity in entities:
                    formatted_entities.append({
                        'entity': entity['entity_group'],
                        'word': entity['word'],
                        'confidence': entity['score']
                    })
                
                return formatted_entities
            
        except Exception as e:
            logger.warning(f"Named entity recognition failed: {e}")
            return []
    
    async def _analyze_language_complexity(self, text: str) -> float:
        """Analyze language complexity"""
        try:
            words = word_tokenize(text)
            sentences = sent_tokenize(text)
            
            if not words or not sentences:
                return 0.0
            
            complexity_score = 0.0
            
            # Average sentence length
            avg_sentence_length = len(words) / len(sentences)
            
            # Flesch Reading Ease approximation
            avg_sentence_length_score = min(1.0, avg_sentence_length / 20)
            complexity_score += avg_sentence_length_score * 0.3
            
            # Vocabulary diversity (unique words / total words)
            unique_words = len(set(word.lower() for word in words if word.isalpha()))
            vocab_diversity = unique_words / len(words) if words else 0
            complexity_score += vocab_diversity * 0.3
            
            # Complex word count (words with more than 2 syllables - approximated)
            complex_words = sum(1 for word in words if len(word) > 6 and word.isalpha())
            complex_word_ratio = complex_words / len(words) if words else 0
            complexity_score += complex_word_ratio * 0.2
            
            # Subordinate clauses indicators
            subordinate_words = ['because', 'although', 'since', 'while', 'whereas',
                               'if', 'unless', 'before', 'after', 'when', 'where']
            subordinate_count = sum(1 for word in word_tokenize(text.lower()) 
                                  if word in subordinate_words)
            subordinate_ratio = subordinate_count / len(sentences) if sentences else 0
            complexity_score += min(0.2, subordinate_ratio * 2)
            
            return max(0.0, min(1.0, complexity_score))
            
        except Exception as e:
            logger.warning(f"Language complexity analysis failed: {e}")
            return 0.5
    
    async def _detect_bias_indicators(self, text: str) -> List[str]:
        """Detect potential bias indicators in text"""
        try:
            bias_indicators = []
            text_lower = text.lower()
            
            # Check each bias category
            for bias_type, keywords in self.bias_keywords.items():
                for keyword in keywords:
                    if keyword in text_lower:
                        bias_indicators.append(f"{bias_type}: {keyword}")
            
            # Check for potentially problematic phrases
            problematic_phrases = [
                'cultural fit', 'team player', 'aggressive', 'assertive',
                'emotional', 'dramatic', 'bossy', 'pushy', 'difficult'
            ]
            
            for phrase in problematic_phrases:
                if phrase in text_lower:
                    bias_indicators.append(f"potentially_biased_language: {phrase}")
            
            # Remove duplicates and return
            return list(set(bias_indicators))
            
        except Exception as e:
            logger.warning(f"Bias detection failed: {e}")
            return []
    
    def _create_emotion_scores(self, emotion_result: List[Dict]) -> EmotionScores:
        """Create EmotionScores object from analysis result"""
        # Initialize with neutral values
        emotion_mapping = {
            'happiness': 0.0, 'joy': 0.0,
            'sadness': 0.0,
            'anger': 0.0,
            'fear': 0.0,
            'surprise': 0.0,
            'disgust': 0.0,
            'neutral': 0.5
        }
        
        try:
            if emotion_result and isinstance(emotion_result, list):
                for emotion_data in emotion_result[:7]:  # Limit to top 7 emotions
                    label = emotion_data['label'].lower()
                    score = emotion_data['score']
                    
                    # Map emotion labels to standard format
                    if label in ['joy', 'happiness', 'happy']:
                        emotion_mapping['happiness'] = score
                    elif label in ['sadness', 'sad']:
                        emotion_mapping['sadness'] = score
                    elif label in ['anger', 'angry']:
                        emotion_mapping['anger'] = score
                    elif label in ['fear', 'afraid']:
                        emotion_mapping['fear'] = score
                    elif label in ['surprise', 'surprised']:
                        emotion_mapping['surprise'] = score
                    elif label in ['disgust', 'disgusted']:
                        emotion_mapping['disgust'] = score
                    elif label in ['neutral']:
                        emotion_mapping['neutral'] = score
            
            return EmotionScores(
                happiness=emotion_mapping['happiness'],
                sadness=emotion_mapping['sadness'],
                anger=emotion_mapping['anger'],
                fear=emotion_mapping['fear'],
                surprise=emotion_mapping['surprise'],
                disgust=emotion_mapping['disgust'],
                neutral=emotion_mapping['neutral']
            )
            
        except Exception as e:
            logger.warning(f"Error creating emotion scores: {e}")
            return EmotionScores(
                happiness=0.0, sadness=0.0, anger=0.0, fear=0.0,
                surprise=0.0, disgust=0.0, neutral=0.5
            )
    
    async def cleanup(self):
        """Cleanup resources"""
        # Clear model caches
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
        
        logger.info("NLPAnalyzer cleanup completed")


# Example usage and testing
async def test_nlp_analyzer():
    """Test the NLP analyzer with sample text"""
    analyzer = NLPAnalyzer()
    await analyzer.initialize_models()
    
    sample_text = """
    I believe the best approach for this system design problem would be to use a microservices 
    architecture with load balancers for scalability. We could implement caching strategies 
    using Redis and ensure database consistency through proper transaction management. 
    This would provide high availability and allow for horizontal scaling as the user base grows.
    """
    
    result = await analyzer.analyze_text(
        text=sample_text,
        session_id="test_session",
        candidate_id=123,
        context={"topic": "system_design"}
    )
    
    print(f"Analysis ID: {result.analysis_id}")
    print(f"Sentiment: {result.sentiment_label} ({result.sentiment_score:.2f})")
    print(f"Technical Accuracy: {result.technical_accuracy:.2f}")
    print(f"Professionalism: {result.professionalism_score:.2f}")
    print(f"Coherence: {result.coherence_score:.2f}")
    print(f"Key Topics: {result.key_topics}")
    print(f"Processing Time: {result.processing_time:.2f}s")
    
    await analyzer.cleanup()


if __name__ == "__main__":
    asyncio.run(test_nlp_analyzer())
