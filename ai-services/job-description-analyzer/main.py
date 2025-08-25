#!/usr/bin/env python3
"""
Job Description Analyzer Service for ARIA Interview Platform
Provides NLP analysis of job descriptions to extract skills, technologies, and generate
contextual weights for enhanced question selection.
"""

import asyncio
import json
import logging
import re
from datetime import datetime
from typing import Dict, List, Optional, Any, Tuple
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import redis

# NLP and ML imports
try:
    import spacy
    import nltk
    from nltk.tokenize import word_tokenize, sent_tokenize
    from nltk.corpus import stopwords
    from nltk.stem import WordNetLemmatizer
    NLP_AVAILABLE = True
except ImportError:
    NLP_AVAILABLE = False
    logging.warning("NLP libraries not available")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Redis client for caching analysis results
redis_client = redis.Redis(host='localhost', port=6379, db=3, decode_responses=True)

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize NLP models on startup"""
    logger.info("Starting Job Description Analyzer Service...")
    
    if NLP_AVAILABLE:
        try:
            # Download required NLTK data
            nltk.download('punkt', quiet=True)
            nltk.download('stopwords', quiet=True)
            nltk.download('wordnet', quiet=True)
            nltk.download('averaged_perceptron_tagger', quiet=True)
            
            # Load spaCy model
            try:
                app.state.nlp_model = spacy.load("en_core_web_sm")
                logger.info("spaCy model loaded successfully")
            except OSError:
                logger.warning("spaCy 'en_core_web_sm' model not found. Installing...")
                import subprocess
                subprocess.run(["python", "-m", "spacy", "download", "en_core_web_sm"])
                app.state.nlp_model = spacy.load("en_core_web_sm")
            
            # Initialize NLTK components
            app.state.lemmatizer = WordNetLemmatizer()
            app.state.stop_words = set(stopwords.words('english'))
            
            logger.info("NLP models initialized successfully")
        except Exception as e:
            logger.error(f"Failed to initialize NLP models: {e}")
            app.state.nlp_model = None
    else:
        logger.warning("NLP libraries not available - service will run with limited functionality")
        app.state.nlp_model = None
    
    # Initialize analyzers
    app.state.skill_extractor = SkillExtractor(app.state.nlp_model if NLP_AVAILABLE else None)
    app.state.tech_detector = TechnologyDetector()
    app.state.job_analyzer = JobDescriptionAnalyzer(
        app.state.skill_extractor, 
        app.state.tech_detector,
        app.state.nlp_model if NLP_AVAILABLE else None
    )
    
    yield
    
    logger.info("Shutting down Job Description Analyzer Service...")

# Create FastAPI application
app = FastAPI(
    title="ARIA Job Description Analyzer Service",
    description="NLP analysis of job descriptions for enhanced interview question selection",
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

# Pydantic models
class JobAnalysisRequest(BaseModel):
    job_description: str
    key_responsibilities: Optional[str] = ""
    job_role: Optional[str] = ""
    company_context: Optional[str] = ""
    
class TechnicalSkill(BaseModel):
    name: str
    confidence: float = Field(..., ge=0.0, le=1.0)
    category: str  # programming, database, cloud, etc.
    importance_weight: float = Field(default=1.0, ge=0.0, le=2.0)

class SoftSkill(BaseModel):
    name: str
    confidence: float = Field(..., ge=0.0, le=1.0)
    category: str  # leadership, communication, etc.

class Technology(BaseModel):
    name: str
    confidence: float = Field(..., ge=0.0, le=1.0)
    category: str  # language, framework, tool, platform
    version: Optional[str] = None

class ResponsibilityCategory(BaseModel):
    category: str
    responsibilities: List[str]
    weight: float = Field(..., ge=0.0, le=1.0)

class QuestionCategoryWeight(BaseModel):
    category: str
    weight: float = Field(..., ge=0.0, le=1.0)
    reasoning: str

class JobAnalysisResult(BaseModel):
    technical_skills: List[TechnicalSkill]
    soft_skills: List[SoftSkill]
    technologies: List[Technology]
    responsibility_categories: List[ResponsibilityCategory]
    question_category_weights: List[QuestionCategoryWeight]
    estimated_seniority: str
    estimated_experience_years: int
    domain_expertise: List[str]
    key_competencies: List[str]
    analysis_metadata: Dict[str, Any]

# Core analyzer classes
class SkillExtractor:
    """Extracts technical and soft skills from job descriptions"""
    
    def __init__(self, nlp_model):
        self.nlp_model = nlp_model
        
        # Predefined skill databases
        self.technical_skills_db = {
            # Programming Languages
            'programming': [
                'python', 'java', 'javascript', 'typescript', 'c++', 'c#', 'go', 'rust',
                'kotlin', 'swift', 'php', 'ruby', 'scala', 'r', 'matlab', 'sql'
            ],
            # Frameworks & Libraries
            'frameworks': [
                'react', 'angular', 'vue.js', 'node.js', 'express', 'django', 'flask',
                'spring boot', 'spring', 'laravel', 'rails', 'tensorflow', 'pytorch',
                '.net', 'asp.net', 'jquery', 'bootstrap'
            ],
            # Databases
            'databases': [
                'mysql', 'postgresql', 'mongodb', 'redis', 'elasticsearch', 'cassandra',
                'dynamodb', 'oracle', 'sql server', 'sqlite', 'neo4j', 'influxdb'
            ],
            # Cloud & DevOps
            'cloud': [
                'aws', 'azure', 'google cloud', 'gcp', 'docker', 'kubernetes', 'jenkins',
                'terraform', 'ansible', 'chef', 'puppet', 'gitlab ci', 'github actions'
            ],
            # Tools & Technologies
            'tools': [
                'git', 'jira', 'confluence', 'postman', 'swagger', 'gradle', 'maven',
                'npm', 'webpack', 'babel', 'eslint', 'prettier', 'junit', 'pytest'
            ]
        }
        
        self.soft_skills_db = {
            'leadership': [
                'team leadership', 'project management', 'mentoring', 'coaching',
                'strategic planning', 'decision making', 'delegation'
            ],
            'communication': [
                'communication', 'presentation', 'public speaking', 'writing',
                'documentation', 'stakeholder management', 'client interaction'
            ],
            'collaboration': [
                'teamwork', 'collaboration', 'cross-functional', 'agile', 'scrum',
                'pair programming', 'code review', 'conflict resolution'
            ],
            'analytical': [
                'problem solving', 'analytical thinking', 'troubleshooting',
                'debugging', 'critical thinking', 'research', 'data analysis'
            ]
        }
    
    async def extract_technical_skills(self, text: str) -> List[TechnicalSkill]:
        """Extract technical skills from text"""
        skills = []
        text_lower = text.lower()
        
        for category, skill_list in self.technical_skills_db.items():
            for skill in skill_list:
                # Check for skill mentions with context
                confidence = self._calculate_skill_confidence(skill, text_lower)
                if confidence > 0.3:  # Threshold for inclusion
                    importance = self._calculate_skill_importance(skill, text_lower)
                    skills.append(TechnicalSkill(
                        name=skill,
                        confidence=confidence,
                        category=category,
                        importance_weight=importance
                    ))
        
        # Sort by confidence and return top matches
        skills.sort(key=lambda x: x.confidence * x.importance_weight, reverse=True)
        return skills[:20]  # Top 20 technical skills
    
    async def extract_soft_skills(self, text: str) -> List[SoftSkill]:
        """Extract soft skills from text"""
        skills = []
        text_lower = text.lower()
        
        for category, skill_list in self.soft_skills_db.items():
            for skill in skill_list:
                confidence = self._calculate_skill_confidence(skill, text_lower)
                if confidence > 0.2:  # Lower threshold for soft skills
                    skills.append(SoftSkill(
                        name=skill,
                        confidence=confidence,
                        category=category
                    ))
        
        skills.sort(key=lambda x: x.confidence, reverse=True)
        return skills[:15]  # Top 15 soft skills
    
    def _calculate_skill_confidence(self, skill: str, text: str) -> float:
        """Calculate confidence score for skill presence"""
        # Direct exact match
        if skill in text:
            confidence = 0.9
            
            # Boost confidence if mentioned multiple times
            count = text.count(skill)
            confidence = min(0.95, confidence + (count - 1) * 0.05)
            
            # Check for context indicators
            context_indicators = [
                f"experience with {skill}", f"expertise in {skill}",
                f"proficient in {skill}", f"skilled in {skill}",
                f"{skill} development", f"{skill} programming"
            ]
            
            for indicator in context_indicators:
                if indicator in text:
                    confidence = min(0.99, confidence + 0.1)
                    break
                    
            return confidence
        
        # Partial matches for compound skills
        skill_words = skill.split()
        if len(skill_words) > 1:
            matches = sum(1 for word in skill_words if word in text)
            if matches > 0:
                return 0.3 + (matches / len(skill_words)) * 0.4
        
        return 0.0
    
    def _calculate_skill_importance(self, skill: str, text: str) -> float:
        """Calculate importance weight based on context"""
        importance = 1.0
        
        # High importance indicators
        high_importance = [
            f"required {skill}", f"must have {skill}", f"essential {skill}",
            f"primary {skill}", f"core {skill}", f"lead {skill}"
        ]
        
        for indicator in high_importance:
            if indicator in text:
                return 2.0
        
        # Medium importance indicators
        medium_importance = [
            f"strong {skill}", f"advanced {skill}", f"senior {skill}",
            f"expert {skill}", f"deep {skill}"
        ]
        
        for indicator in medium_importance:
            if indicator in text:
                return 1.5
        
        return importance

class TechnologyDetector:
    """Detects specific technologies, tools, and platforms"""
    
    def __init__(self):
        self.tech_categories = {
            'programming_languages': [
                {'name': 'Python', 'aliases': ['python', 'py']},
                {'name': 'Java', 'aliases': ['java', 'jvm']},
                {'name': 'JavaScript', 'aliases': ['javascript', 'js', 'ecmascript']},
                {'name': 'TypeScript', 'aliases': ['typescript', 'ts']},
                {'name': 'Go', 'aliases': ['golang', 'go']},
                {'name': 'Rust', 'aliases': ['rust']},
                {'name': 'C++', 'aliases': ['c++', 'cpp', 'cplusplus']},
                {'name': 'C#', 'aliases': ['c#', 'csharp', 'c sharp']},
            ],
            'frameworks': [
                {'name': 'React', 'aliases': ['react', 'reactjs', 'react.js']},
                {'name': 'Angular', 'aliases': ['angular', 'angularjs']},
                {'name': 'Vue.js', 'aliases': ['vue', 'vue.js', 'vuejs']},
                {'name': 'Spring Boot', 'aliases': ['spring boot', 'springboot']},
                {'name': 'Django', 'aliases': ['django']},
                {'name': 'Flask', 'aliases': ['flask']},
                {'name': 'Node.js', 'aliases': ['node', 'node.js', 'nodejs']},
            ],
            'databases': [
                {'name': 'PostgreSQL', 'aliases': ['postgresql', 'postgres']},
                {'name': 'MySQL', 'aliases': ['mysql']},
                {'name': 'MongoDB', 'aliases': ['mongodb', 'mongo']},
                {'name': 'Redis', 'aliases': ['redis']},
                {'name': 'Elasticsearch', 'aliases': ['elasticsearch', 'elastic']},
            ],
            'cloud_platforms': [
                {'name': 'AWS', 'aliases': ['aws', 'amazon web services']},
                {'name': 'Azure', 'aliases': ['azure', 'microsoft azure']},
                {'name': 'Google Cloud', 'aliases': ['gcp', 'google cloud', 'google cloud platform']},
            ],
            'tools': [
                {'name': 'Docker', 'aliases': ['docker', 'containerization']},
                {'name': 'Kubernetes', 'aliases': ['kubernetes', 'k8s']},
                {'name': 'Git', 'aliases': ['git', 'version control']},
                {'name': 'Jenkins', 'aliases': ['jenkins', 'ci/cd']},
            ]
        }
    
    async def detect_technologies(self, text: str) -> List[Technology]:
        """Detect technologies mentioned in text"""
        technologies = []
        text_lower = text.lower()
        
        for category, tech_list in self.tech_categories.items():
            for tech_info in tech_list:
                confidence = self._detect_technology_confidence(tech_info, text_lower)
                if confidence > 0.0:
                    version = self._extract_version(tech_info['name'], text_lower)
                    technologies.append(Technology(
                        name=tech_info['name'],
                        confidence=confidence,
                        category=category,
                        version=version
                    ))
        
        technologies.sort(key=lambda x: x.confidence, reverse=True)
        return technologies
    
    def _detect_technology_confidence(self, tech_info: Dict, text: str) -> float:
        """Calculate confidence for technology detection"""
        max_confidence = 0.0
        
        for alias in tech_info['aliases']:
            if alias in text:
                confidence = 0.8
                
                # Check for version indicators
                if re.search(rf"{alias}\s*\d+", text):
                    confidence = 0.95
                
                # Check for experience indicators
                experience_patterns = [
                    rf"experience with {alias}",
                    rf"expertise in {alias}",
                    rf"{alias} experience",
                    rf"years of {alias}"
                ]
                
                for pattern in experience_patterns:
                    if re.search(pattern, text):
                        confidence = min(0.99, confidence + 0.1)
                        break
                
                max_confidence = max(max_confidence, confidence)
        
        return max_confidence
    
    def _extract_version(self, tech_name: str, text: str) -> Optional[str]:
        """Extract version information if available"""
        # Look for version patterns like "Python 3.8", "Node.js 16", etc.
        version_pattern = rf"{tech_name.lower()}\s*(\d+(?:\.\d+)*)"
        match = re.search(version_pattern, text, re.IGNORECASE)
        if match:
            return match.group(1)
        return None

class JobDescriptionAnalyzer:
    """Main analyzer that coordinates all analysis components"""
    
    def __init__(self, skill_extractor: SkillExtractor, tech_detector: TechnologyDetector, nlp_model):
        self.skill_extractor = skill_extractor
        self.tech_detector = tech_detector
        self.nlp_model = nlp_model
    
    async def analyze_job_description(self, job_description: str, key_responsibilities: str = "") -> JobAnalysisResult:
        """Perform comprehensive job description analysis"""
        try:
            # Combine job description and responsibilities
            full_text = f"{job_description} {key_responsibilities}".strip()
            
            # Extract components
            technical_skills = await self.skill_extractor.extract_technical_skills(full_text)
            soft_skills = await self.skill_extractor.extract_soft_skills(full_text)
            technologies = await self.tech_detector.detect_technologies(full_text)
            
            # Analyze responsibilities
            responsibility_categories = await self._analyze_responsibilities(key_responsibilities)
            
            # Estimate seniority and experience
            seniority, experience_years = self._estimate_seniority_level(full_text)
            
            # Extract domain expertise
            domain_expertise = self._extract_domain_expertise(full_text)
            
            # Generate key competencies
            key_competencies = self._generate_key_competencies(technical_skills, soft_skills)
            
            # Generate question category weights
            question_weights = await self._generate_question_category_weights(
                technical_skills, soft_skills, technologies, responsibility_categories, seniority
            )
            
            # Create analysis metadata
            metadata = {
                'analysis_timestamp': datetime.now().isoformat(),
                'text_length': len(full_text),
                'skills_found': len(technical_skills) + len(soft_skills),
                'technologies_found': len(technologies),
                'nlp_model_used': 'spacy' if self.nlp_model else 'rule-based'
            }
            
            return JobAnalysisResult(
                technical_skills=technical_skills,
                soft_skills=soft_skills,
                technologies=technologies,
                responsibility_categories=responsibility_categories,
                question_category_weights=question_weights,
                estimated_seniority=seniority,
                estimated_experience_years=experience_years,
                domain_expertise=domain_expertise,
                key_competencies=key_competencies,
                analysis_metadata=metadata
            )
            
        except Exception as e:
            logger.error(f"Error in job description analysis: {e}")
            raise HTTPException(status_code=500, detail=f"Analysis failed: {str(e)}")
    
    async def _analyze_responsibilities(self, responsibilities: str) -> List[ResponsibilityCategory]:
        """Analyze and categorize job responsibilities"""
        if not responsibilities.strip():
            return []
        
        categories = {
            'development': {
                'keywords': ['develop', 'build', 'code', 'implement', 'create', 'program'],
                'responsibilities': [],
                'weight': 0.0
            },
            'architecture': {
                'keywords': ['design', 'architect', 'system', 'structure', 'patterns'],
                'responsibilities': [],
                'weight': 0.0
            },
            'leadership': {
                'keywords': ['lead', 'manage', 'mentor', 'guide', 'coordinate'],
                'responsibilities': [],
                'weight': 0.0
            },
            'collaboration': {
                'keywords': ['collaborate', 'work with', 'communicate', 'stakeholder'],
                'responsibilities': [],
                'weight': 0.0
            },
            'maintenance': {
                'keywords': ['maintain', 'support', 'troubleshoot', 'debug', 'fix'],
                'responsibilities': [],
                'weight': 0.0
            }
        }
        
        # Split responsibilities into sentences
        sentences = responsibilities.split('\n') if '\n' in responsibilities else [responsibilities]
        
        for sentence in sentences:
            sentence = sentence.strip()
            if not sentence:
                continue
                
            # Categorize each responsibility
            best_category = None
            best_score = 0
            
            for category, info in categories.items():
                score = sum(1 for keyword in info['keywords'] if keyword.lower() in sentence.lower())
                if score > best_score:
                    best_score = score
                    best_category = category
            
            if best_category and best_score > 0:
                categories[best_category]['responsibilities'].append(sentence)
                categories[best_category]['weight'] += best_score
        
        # Convert to result format
        result = []
        total_weight = sum(cat['weight'] for cat in categories.values())
        
        for category, info in categories.items():
            if info['responsibilities']:
                normalized_weight = info['weight'] / total_weight if total_weight > 0 else 0
                result.append(ResponsibilityCategory(
                    category=category,
                    responsibilities=info['responsibilities'],
                    weight=normalized_weight
                ))
        
        return result
    
    def _estimate_seniority_level(self, text: str) -> Tuple[str, int]:
        """Estimate seniority level and experience years"""
        text_lower = text.lower()
        
        # Look for explicit seniority indicators
        seniority_indicators = {
            'junior': ['junior', 'entry', 'associate', 'trainee', '0-2 years'],
            'mid': ['mid', 'intermediate', '2-5 years', '3-5 years'],
            'senior': ['senior', 'sr.', '5+ years', '5-8 years', 'experienced'],
            'lead': ['lead', 'principal', 'staff', '8+ years', '10+ years'],
            'architect': ['architect', 'distinguished', 'fellow', 'chief']
        }
        
        for level, indicators in seniority_indicators.items():
            for indicator in indicators:
                if indicator in text_lower:
                    experience_years = self._extract_experience_years(text_lower, level)
                    return level, experience_years
        
        # Estimate based on responsibilities and requirements complexity
        complexity_score = 0
        
        high_complexity_terms = [
            'architecture', 'design patterns', 'scalability', 'performance optimization',
            'microservices', 'distributed systems', 'team lead', 'mentoring'
        ]
        
        for term in high_complexity_terms:
            if term in text_lower:
                complexity_score += 1
        
        if complexity_score >= 4:
            return 'senior', 6
        elif complexity_score >= 2:
            return 'mid', 3
        else:
            return 'junior', 1
    
    def _extract_experience_years(self, text: str, seniority: str) -> int:
        """Extract experience years from text"""
        # Look for explicit year mentions
        year_patterns = [
            r'(\d+)\+?\s*years?', r'(\d+)-(\d+)\s*years?'
        ]
        
        for pattern in year_patterns:
            matches = re.findall(pattern, text)
            if matches:
                if isinstance(matches[0], tuple):
                    return int(matches[0][1])  # Take upper bound
                else:
                    return int(matches[0])
        
        # Default based on seniority level
        defaults = {
            'junior': 1,
            'mid': 4,
            'senior': 7,
            'lead': 10,
            'architect': 12
        }
        
        return defaults.get(seniority, 3)
    
    def _extract_domain_expertise(self, text: str) -> List[str]:
        """Extract domain/industry expertise"""
        domains = {
            'fintech': ['financial', 'banking', 'payment', 'trading', 'fintech'],
            'healthcare': ['healthcare', 'medical', 'clinical', 'pharmaceutical'],
            'ecommerce': ['ecommerce', 'retail', 'shopping', 'marketplace'],
            'gaming': ['gaming', 'game', 'entertainment', 'mobile games'],
            'enterprise': ['enterprise', 'b2b', 'saas', 'business software'],
            'security': ['security', 'cybersecurity', 'encryption', 'authentication'],
            'ai_ml': ['machine learning', 'artificial intelligence', 'data science', 'ml'],
            'devops': ['devops', 'infrastructure', 'deployment', 'automation']
        }
        
        text_lower = text.lower()
        found_domains = []
        
        for domain, keywords in domains.items():
            for keyword in keywords:
                if keyword in text_lower:
                    found_domains.append(domain)
                    break
        
        return found_domains
    
    def _generate_key_competencies(self, technical_skills: List[TechnicalSkill], soft_skills: List[SoftSkill]) -> List[str]:
        """Generate key competencies based on skills analysis"""
        competencies = []
        
        # Top technical competencies
        top_technical = sorted(technical_skills, key=lambda x: x.confidence * x.importance_weight, reverse=True)[:5]
        competencies.extend([skill.name for skill in top_technical])
        
        # Top soft skills
        top_soft = sorted(soft_skills, key=lambda x: x.confidence, reverse=True)[:3]
        competencies.extend([skill.name for skill in top_soft])
        
        return competencies
    
    async def _generate_question_category_weights(
        self, 
        technical_skills: List[TechnicalSkill], 
        soft_skills: List[SoftSkill], 
        technologies: List[Technology], 
        responsibilities: List[ResponsibilityCategory],
        seniority: str
    ) -> List[QuestionCategoryWeight]:
        """Generate weights for different question categories"""
        
        # Initialize category weights
        categories = {
            'technical_fundamentals': {'weight': 0.3, 'reasoning': 'Basic technical concepts'},
            'programming_skills': {'weight': 0.0, 'reasoning': 'Programming and coding abilities'},
            'system_design': {'weight': 0.0, 'reasoning': 'System architecture and design'},
            'problem_solving': {'weight': 0.2, 'reasoning': 'Analytical and problem-solving skills'},
            'behavioral': {'weight': 0.15, 'reasoning': 'Soft skills and cultural fit'},
            'leadership': {'weight': 0.0, 'reasoning': 'Leadership and management skills'},
            'domain_specific': {'weight': 0.0, 'reasoning': 'Role-specific domain knowledge'},
            'communication': {'weight': 0.1, 'reasoning': 'Communication and collaboration'}
        }
        
        # Adjust based on technical skills
        programming_weight = 0.0
        for skill in technical_skills:
            if skill.category == 'programming':
                programming_weight += skill.confidence * skill.importance_weight * 0.1
        categories['programming_skills']['weight'] = min(0.4, programming_weight)
        
        # Adjust based on technologies
        tech_complexity = len([t for t in technologies if t.confidence > 0.7])
        if tech_complexity >= 3:
            categories['system_design']['weight'] = 0.25
            categories['technical_fundamentals']['weight'] = 0.2
        
        # Adjust based on responsibilities
        for resp_cat in responsibilities:
            if resp_cat.category == 'leadership' and resp_cat.weight > 0.2:
                categories['leadership']['weight'] = 0.2
            elif resp_cat.category == 'architecture' and resp_cat.weight > 0.3:
                categories['system_design']['weight'] = max(categories['system_design']['weight'], 0.3)
        
        # Adjust based on seniority
        if seniority in ['senior', 'lead', 'architect']:
            categories['system_design']['weight'] = max(categories['system_design']['weight'], 0.2)
            categories['leadership']['weight'] = max(categories['leadership']['weight'], 0.15)
            categories['behavioral']['weight'] = 0.2
        elif seniority == 'junior':
            categories['technical_fundamentals']['weight'] = 0.4
            categories['programming_skills']['weight'] = max(categories['programming_skills']['weight'], 0.3)
        
        # Normalize weights to sum to 1.0
        total_weight = sum(cat['weight'] for cat in categories.values())
        if total_weight > 1.0:
            for cat in categories.values():
                cat['weight'] /= total_weight
        
        # Convert to result format
        result = []
        for category, info in categories.items():
            if info['weight'] > 0.05:  # Only include categories with significant weight
                result.append(QuestionCategoryWeight(
                    category=category,
                    weight=info['weight'],
                    reasoning=info['reasoning']
                ))
        
        return sorted(result, key=lambda x: x.weight, reverse=True)

# API Endpoints
@app.get("/")
async def root():
    """Health check endpoint"""
    return {
        "service": "ARIA Job Description Analyzer Service",
        "status": "healthy",
        "version": "1.0.0",
        "nlp_available": NLP_AVAILABLE,
        "timestamp": datetime.now().isoformat()
    }

@app.post("/analyze", response_model=JobAnalysisResult)
async def analyze_job_description(request: JobAnalysisRequest):
    """Analyze a job description and extract relevant information"""
    try:
        # Check cache first
        cache_key = f"job_analysis:{hash(request.job_description + request.key_responsibilities)}"
        cached_result = redis_client.get(cache_key)
        
        if cached_result:
            logger.info("Returning cached job analysis result")
            return JobAnalysisResult.parse_raw(cached_result)
        
        # Perform analysis
        logger.info(f"Analyzing job description: {len(request.job_description)} characters")
        result = await app.state.job_analyzer.analyze_job_description(
            request.job_description,
            request.key_responsibilities or ""
        )
        
        # Cache result for 1 hour
        redis_client.setex(cache_key, 3600, result.json())
        
        logger.info(f"Analysis complete: {len(result.technical_skills)} technical skills, "
                   f"{len(result.technologies)} technologies found")
        
        return result
        
    except Exception as e:
        logger.error(f"Error analyzing job description: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/preview-questions")
async def preview_questions_for_job(request: JobAnalysisRequest):
    """Preview what types of questions would be selected for this job"""
    try:
        # Analyze job description
        analysis = await app.state.job_analyzer.analyze_job_description(
            request.job_description,
            request.key_responsibilities or ""
        )
        
        # Generate question preview based on weights
        preview = {
            'recommended_question_categories': [
                {
                    'category': weight.category,
                    'weight': weight.weight,
                    'reasoning': weight.reasoning,
                    'estimated_questions': max(1, int(weight.weight * 20))  # Estimate questions out of 20
                }
                for weight in analysis.question_category_weights
            ],
            'key_focus_areas': analysis.key_competencies[:5],
            'technical_skills_to_test': [skill.name for skill in analysis.technical_skills[:8]],
            'estimated_difficulty': analysis.estimated_seniority,
            'suggested_interview_duration': f"{30 + analysis.estimated_experience_years * 5} minutes"
        }
        
        return preview
        
    except Exception as e:
        logger.error(f"Error generating question preview: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/health")
async def health_check():
    """Detailed health check"""
    try:
        redis_status = "healthy" if redis_client.ping() else "unhealthy"
        
        return {
            "status": "healthy" if NLP_AVAILABLE else "degraded",
            "components": {
                "redis": redis_status,
                "nlp_models": "available" if NLP_AVAILABLE else "unavailable",
                "skill_extractor": "healthy",
                "technology_detector": "healthy",
                "job_analyzer": "healthy"
            },
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        return {
            "status": "unhealthy",
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }

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
            host="0.0.0.0", 
            port=8005, 
            log_level="info",
            ssl_keyfile=ssl_key_path,
            ssl_certfile=ssl_cert_path
        )
    else:
        # Fallback to HTTP
        print("Warning: SSL certificates not found, running with HTTP")
        uvicorn.run(app, host="0.0.0.0", port=8005, log_level="info")
