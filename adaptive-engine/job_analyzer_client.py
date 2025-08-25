#!/usr/bin/env python3
"""
Job Analyzer Client for Adaptive Engine
Handles communication with the Job Description Analyzer Service
"""

import aiohttp
import asyncio
import json
import logging
from typing import Dict, List, Optional, Any
from datetime import datetime, timedelta
import redis

logger = logging.getLogger(__name__)

class JobAnalyzerClient:
    """Client for communicating with Job Description Analyzer Service"""
    
    def __init__(self, base_url: str = "http://localhost:8005"):
        self.base_url = base_url
        self.redis_client = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)
        self.cache_ttl = 3600  # 1 hour cache
        
    async def analyze_job_description(
        self, 
        job_description: str, 
        key_responsibilities: str = "",
        job_role: str = "",
        company_context: str = ""
    ) -> Optional[Dict[str, Any]]:
        """
        Analyze job description and get skills, technologies, and question weights
        
        Args:
            job_description: The job description text
            key_responsibilities: Key responsibilities text
            job_role: Job role/title
            company_context: Company context information
            
        Returns:
            Analysis results or None if analysis fails
        """
        try:
            # Check cache first
            cache_key = f"job_analysis:{hash(job_description + key_responsibilities)}"
            cached_result = self.redis_client.get(cache_key)
            
            if cached_result:
                logger.info("Returning cached job analysis result")
                return json.loads(cached_result)
            
            # Make request to job analyzer service
            async with aiohttp.ClientSession() as session:
                payload = {
                    "job_description": job_description,
                    "key_responsibilities": key_responsibilities,
                    "job_role": job_role,
                    "company_context": company_context
                }
                
                async with session.post(
                    f"{self.base_url}/analyze",
                    json=payload,
                    timeout=aiohttp.ClientTimeout(total=30)
                ) as response:
                    
                    if response.status == 200:
                        result = await response.json()
                        
                        # Cache the result
                        self.redis_client.setex(cache_key, self.cache_ttl, json.dumps(result))
                        
                        logger.info(f"Job analysis completed: {len(result.get('technical_skills', []))} "
                                   f"technical skills, {len(result.get('technologies', []))} technologies")
                        
                        return result
                    else:
                        logger.error(f"Job analyzer service returned status {response.status}")
                        return None
                        
        except asyncio.TimeoutError:
            logger.error("Timeout connecting to job analyzer service")
            return None
        except Exception as e:
            logger.error(f"Error analyzing job description: {str(e)}")
            return None
    
    async def get_question_category_weights(
        self,
        job_description: str,
        key_responsibilities: str = ""
    ) -> Dict[str, float]:
        """
        Get question category weights for adaptive engine
        
        Args:
            job_description: The job description text
            key_responsibilities: Key responsibilities text
            
        Returns:
            Dictionary mapping question categories to weights
        """
        try:
            analysis_result = await self.analyze_job_description(
                job_description, key_responsibilities
            )
            
            if not analysis_result:
                # Return default weights if analysis fails
                return self._get_default_category_weights()
            
            # Extract question category weights
            weights = {}
            for weight_info in analysis_result.get('question_category_weights', []):
                weights[weight_info['category']] = weight_info['weight']
            
            # Ensure all required categories have weights
            default_weights = self._get_default_category_weights()
            for category, default_weight in default_weights.items():
                if category not in weights:
                    weights[category] = default_weight
            
            return weights
            
        except Exception as e:
            logger.error(f"Error getting question category weights: {str(e)}")
            return self._get_default_category_weights()
    
    async def get_technical_skills_for_filtering(
        self,
        job_description: str,
        key_responsibilities: str = ""
    ) -> List[str]:
        """
        Get technical skills that should be prioritized in question selection
        
        Args:
            job_description: The job description text
            key_responsibilities: Key responsibilities text
            
        Returns:
            List of technical skills/technologies to prioritize
        """
        try:
            analysis_result = await self.analyze_job_description(
                job_description, key_responsibilities
            )
            
            if not analysis_result:
                return []
            
            # Get high-confidence technical skills
            technical_skills = []
            
            # Add technical skills with high confidence
            for skill in analysis_result.get('technical_skills', []):
                if skill['confidence'] > 0.6 and skill['importance_weight'] > 1.0:
                    technical_skills.append(skill['name'])
            
            # Add technologies with high confidence
            for tech in analysis_result.get('technologies', []):
                if tech['confidence'] > 0.7:
                    technical_skills.append(tech['name'].lower())
            
            # Add key competencies
            technical_skills.extend(analysis_result.get('key_competencies', [])[:5])
            
            return list(set(technical_skills))  # Remove duplicates
            
        except Exception as e:
            logger.error(f"Error getting technical skills: {str(e)}")
            return []
    
    async def get_interview_difficulty_adjustment(
        self,
        job_description: str,
        key_responsibilities: str = ""
    ) -> Dict[str, Any]:
        """
        Get difficulty adjustments based on job analysis
        
        Args:
            job_description: The job description text
            key_responsibilities: Key responsibilities text
            
        Returns:
            Dictionary with difficulty adjustment parameters
        """
        try:
            analysis_result = await self.analyze_job_description(
                job_description, key_responsibilities
            )
            
            if not analysis_result:
                return self._get_default_difficulty_adjustment()
            
            seniority = analysis_result.get('estimated_seniority', 'mid')
            experience_years = analysis_result.get('estimated_experience_years', 3)
            
            # Adjust difficulty range based on seniority
            difficulty_adjustments = {
                'junior': {'min': -2.0, 'max': 1.0, 'initial_theta': -0.5},
                'mid': {'min': -1.5, 'max': 2.0, 'initial_theta': 0.0},
                'senior': {'min': -1.0, 'max': 3.0, 'initial_theta': 0.5},
                'lead': {'min': 0.0, 'max': 3.0, 'initial_theta': 1.0},
                'architect': {'min': 0.5, 'max': 3.0, 'initial_theta': 1.5}
            }
            
            adjustment = difficulty_adjustments.get(seniority, difficulty_adjustments['mid'])
            
            return {
                'min_difficulty': adjustment['min'],
                'max_difficulty': adjustment['max'],
                'initial_theta': adjustment['initial_theta'],
                'estimated_seniority': seniority,
                'experience_years': experience_years,
                'adjustment_confidence': 0.8 if analysis_result else 0.3
            }
            
        except Exception as e:
            logger.error(f"Error getting difficulty adjustment: {str(e)}")
            return self._get_default_difficulty_adjustment()
    
    async def preview_interview_questions(
        self,
        job_description: str,
        key_responsibilities: str = ""
    ) -> Dict[str, Any]:
        """
        Preview what types of questions would be selected for this job
        
        Args:
            job_description: The job description text
            key_responsibilities: Key responsibilities text
            
        Returns:
            Preview of question selection strategy
        """
        try:
            async with aiohttp.ClientSession() as session:
                payload = {
                    "job_description": job_description,
                    "key_responsibilities": key_responsibilities
                }
                
                async with session.post(
                    f"{self.base_url}/preview-questions",
                    json=payload,
                    timeout=aiohttp.ClientTimeout(total=30)
                ) as response:
                    
                    if response.status == 200:
                        return await response.json()
                    else:
                        logger.error(f"Job analyzer preview returned status {response.status}")
                        return {}
                        
        except Exception as e:
            logger.error(f"Error getting question preview: {str(e)}")
            return {}
    
    async def check_service_health(self) -> bool:
        """
        Check if the job analyzer service is healthy
        
        Returns:
            True if service is healthy, False otherwise
        """
        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(
                    f"{self.base_url}/health",
                    timeout=aiohttp.ClientTimeout(total=10)
                ) as response:
                    
                    if response.status == 200:
                        health_data = await response.json()
                        return health_data.get('status') in ['healthy', 'degraded']
                    else:
                        return False
                        
        except Exception as e:
            logger.error(f"Error checking job analyzer service health: {str(e)}")
            return False
    
    def _get_default_category_weights(self) -> Dict[str, float]:
        """Get default question category weights when analysis fails"""
        return {
            'technical_fundamentals': 0.25,
            'programming_skills': 0.25,
            'system_design': 0.15,
            'problem_solving': 0.15,
            'behavioral': 0.10,
            'leadership': 0.05,
            'domain_specific': 0.03,
            'communication': 0.02
        }
    
    def _get_default_difficulty_adjustment(self) -> Dict[str, Any]:
        """Get default difficulty adjustment when analysis fails"""
        return {
            'min_difficulty': -1.5,
            'max_difficulty': 2.0,
            'initial_theta': 0.0,
            'estimated_seniority': 'mid',
            'experience_years': 3,
            'adjustment_confidence': 0.3
        }
