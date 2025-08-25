#!/usr/bin/env python3
"""
ARIA Interview Flow Manager - Exact Implementation per Specifications

This module implements the exact interview flow as specified:
- Stage 1: Introduction & Setup (2-3 minutes)
- Stage 2: Technical Theory Questions (15-20 minutes)
- Stage 3: Coding Challenges (20-25 minutes)
- Stage 4: Cultural Fit & Behavioral Questions (10-15 minutes)
- Stage 5: Candidate Q&A Session (5-10 minutes)
- Stage 6: Interview Conclusion (2-3 minutes)
"""

import asyncio
import json
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
from dataclasses import dataclass
from enum import Enum
import aiohttp

logger = logging.getLogger(__name__)

# ==================== INTERVIEW FLOW ENUMS AND MODELS ====================

class InterviewStage(Enum):
    INTRODUCTION_SETUP = "introduction_setup"
    TECHNICAL_THEORY = "technical_theory"
    CODING_CHALLENGES = "coding_challenges"
    CULTURAL_BEHAVIORAL = "cultural_behavioral"
    CANDIDATE_QA = "candidate_qa"
    INTERVIEW_CONCLUSION = "interview_conclusion"

class QuestionType(Enum):
    INTRODUCTION = "introduction"
    TECHNICAL_THEORY = "technical_theory"
    CODING_PROBLEM = "coding_problem"
    BEHAVIORAL_SCENARIO = "behavioral_scenario"
    CULTURAL_FIT = "cultural_fit"
    CANDIDATE_QUESTION = "candidate_question"
    CONCLUSION = "conclusion"

@dataclass
class InterviewQuestion:
    """Represents a single interview question"""
    id: str
    text: str
    type: QuestionType
    stage: InterviewStage
    expected_duration_seconds: int
    difficulty_level: float
    topics: List[str]
    requires_code_editor: bool = False
    follow_up_enabled: bool = True
    scoring_criteria: Dict[str, Any] = None

@dataclass
class StageConfig:
    """Configuration for each interview stage"""
    stage: InterviewStage
    name: str
    duration_minutes: int
    min_duration_minutes: int
    max_duration_minutes: int
    description: str
    objectives: List[str]

# ==================== INTERVIEW FLOW MANAGER ====================

class InterviewFlowManager:
    """Manages the complete interview flow according to specifications"""
    
    def __init__(self, session_id: str, candidate_profile: Dict[str, Any], job_role: str, 
                 duration_minutes: int = 45):
        self.session_id = session_id
        self.candidate_profile = candidate_profile
        self.job_role = job_role
        self.duration_minutes = duration_minutes
        
        # Flow state
        self.current_stage = InterviewStage.INTRODUCTION_SETUP
        self.current_stage_index = 0
        self.current_question = None
        self.stage_start_time = None
        self.interview_start_time = datetime.now()
        self.questions_asked = 0
        
        # Conversation context
        self.conversation_history = []
        self.candidate_responses = []
        self.real_time_scores = []
        self.candidate_theta = 0.0  # IRT ability estimate
        
        # Flow configuration
        self._initialize_stage_configs()
        self._initialize_question_templates()
        
        # External service connections
        self.adaptive_engine_url = "http://localhost:8006"
        self.analytics_service_url = "http://localhost:8003"
        
        logger.info(f"🎯 Interview Flow Manager initialized for session: {session_id}")

    def _initialize_stage_configs(self):
        """Initialize stage configurations according to specifications"""
        self.stage_configs = {
            InterviewStage.INTRODUCTION_SETUP: StageConfig(
                stage=InterviewStage.INTRODUCTION_SETUP,
                name="Introduction & Setup",
                duration_minutes=3,
                min_duration_minutes=2,
                max_duration_minutes=4,
                description="Welcome, introduction, and readiness confirmation",
                objectives=["Welcome candidate", "Explain process", "Confirm readiness"]
            ),
            InterviewStage.TECHNICAL_THEORY: StageConfig(
                stage=InterviewStage.TECHNICAL_THEORY,
                name="Technical Theory Questions",
                duration_minutes=18,
                min_duration_minutes=15,
                max_duration_minutes=20,
                description="Adaptive technical questions with real-time scoring",
                objectives=["Assess technical knowledge", "Evaluate depth", "Progressive difficulty"]
            ),
            InterviewStage.CODING_CHALLENGES: StageConfig(
                stage=InterviewStage.CODING_CHALLENGES,
                name="Coding Challenges",
                duration_minutes=23,
                min_duration_minutes=20,
                max_duration_minutes=25,
                description="Live coding with Monaco editor and real-time analysis",
                objectives=["Evaluate coding skills", "Assess problem-solving", "Live debugging"]
            ),
            InterviewStage.CULTURAL_BEHAVIORAL: StageConfig(
                stage=InterviewStage.CULTURAL_BEHAVIORAL,
                name="Cultural Fit & Behavioral Questions",
                duration_minutes=13,
                min_duration_minutes=10,
                max_duration_minutes=15,
                description="Scenario-based cultural and behavioral assessment",
                objectives=["Assess cultural fit", "Evaluate teamwork", "Leadership potential"]
            ),
            InterviewStage.CANDIDATE_QA: StageConfig(
                stage=InterviewStage.CANDIDATE_QA,
                name="Candidate Q&A Session",
                duration_minutes=7,
                min_duration_minutes=5,
                max_duration_minutes=10,
                description="Candidate questions with professional boundaries",
                objectives=["Address candidate questions", "Provide role information", "Maintain boundaries"]
            ),
            InterviewStage.INTERVIEW_CONCLUSION: StageConfig(
                stage=InterviewStage.INTERVIEW_CONCLUSION,
                name="Interview Conclusion",
                duration_minutes=3,
                min_duration_minutes=2,
                max_duration_minutes=4,
                description="Professional closing and next steps",
                objectives=["Thank candidate", "Explain next steps", "Professional closure"]
            )
        }
    
    def _initialize_question_templates(self):
        """Initialize question templates for each stage"""
        self.question_templates = {
            InterviewStage.INTRODUCTION_SETUP: [
                InterviewQuestion(
                    id="intro_001",
                    text="Hello! I'm ARIA, your AI interviewer for today. I'll be conducting your Technical T1 interview for the {position} role. This interview will last approximately {duration} minutes and will cover technical knowledge, coding skills, and cultural fit. Are you ready to begin?",
                    type=QuestionType.INTRODUCTION,
                    stage=InterviewStage.INTRODUCTION_SETUP,
                    expected_duration_seconds=30,
                    difficulty_level=0.0,
                    topics=["welcome", "process_explanation"],
                    follow_up_enabled=False
                ),
                InterviewQuestion(
                    id="intro_002",
                    text="Great! Let's start with your introduction. Please tell me about yourself, your background, and your experience relevant to this {position} role.",
                    type=QuestionType.INTRODUCTION,
                    stage=InterviewStage.INTRODUCTION_SETUP,
                    expected_duration_seconds=120,
                    difficulty_level=0.0,
                    topics=["self_introduction", "background", "relevant_experience"],
                    follow_up_enabled=True
                )
            ],
            InterviewStage.TECHNICAL_THEORY: [
                # These will be dynamically generated by adaptive engine
                # Based on candidate profile, role, and experience level
            ],
            InterviewStage.CODING_CHALLENGES: [
                # These will be dynamically selected based on role and difficulty progression
            ],
            InterviewStage.CULTURAL_BEHAVIORAL: [
                # Scenario-based questions will be contextually generated
            ],
            InterviewStage.CANDIDATE_QA: [
                InterviewQuestion(
                    id="qa_001",
                    text="Those were all my questions. Now I'd like to give you the opportunity to ask me anything about the role, responsibilities, team structure, or company culture. What questions do you have?",
                    type=QuestionType.CANDIDATE_QUESTION,
                    stage=InterviewStage.CANDIDATE_QA,
                    expected_duration_seconds=300,
                    difficulty_level=0.0,
                    topics=["candidate_questions", "role_clarification", "company_info"],
                    follow_up_enabled=True
                )
            ],
            InterviewStage.INTERVIEW_CONCLUSION: [
                InterviewQuestion(
                    id="conclusion_001",
                    text="Thank you {candidate_name} for your time today. This completes our Technical T1 interview. Your responses have been recorded and will be reviewed by our recruitment team. You can expect to hear back within {timeframe}. Have a great day!",
                    type=QuestionType.CONCLUSION,
                    stage=InterviewStage.INTERVIEW_CONCLUSION,
                    expected_duration_seconds=30,
                    difficulty_level=0.0,
                    topics=["conclusion", "next_steps", "timeline"],
                    follow_up_enabled=False
                )
            ]
        }

    async def start_interview_flow(self) -> Dict[str, Any]:
        """Start the complete interview flow"""
        try:
            logger.info(f"🚀 Starting interview flow for session: {self.session_id}")
            
            # Initialize interview
            self.interview_start_time = datetime.now()
            self.current_stage = InterviewStage.INTRODUCTION_SETUP
            self.current_stage_index = 0
            
            # Begin with introduction stage
            return await self._execute_stage(InterviewStage.INTRODUCTION_SETUP)
            
        except Exception as e:
            logger.error(f"❌ Error starting interview flow: {e}")
            raise

    async def _execute_stage(self, stage: InterviewStage) -> Dict[str, Any]:
        """Execute a specific interview stage"""
        try:
            self.current_stage = stage
            self.stage_start_time = datetime.now()
            stage_config = self.stage_configs[stage]
            
            logger.info(f"🎯 Executing stage: {stage_config.name}")
            
            # Stage-specific execution
            if stage == InterviewStage.INTRODUCTION_SETUP:
                return await self._execute_introduction_stage()
            elif stage == InterviewStage.TECHNICAL_THEORY:
                return await self._execute_technical_theory_stage()
            elif stage == InterviewStage.CODING_CHALLENGES:
                return await self._execute_coding_challenges_stage()
            elif stage == InterviewStage.CULTURAL_BEHAVIORAL:
                return await self._execute_cultural_behavioral_stage()
            elif stage == InterviewStage.CANDIDATE_QA:
                return await self._execute_candidate_qa_stage()
            elif stage == InterviewStage.INTERVIEW_CONCLUSION:
                return await self._execute_conclusion_stage()
            else:
                raise ValueError(f"Unknown stage: {stage}")
                
        except Exception as e:
            logger.error(f"❌ Error executing stage {stage}: {e}")
            raise

    async def _execute_introduction_stage(self) -> Dict[str, Any]:
        """Execute Introduction & Setup stage (2-3 minutes)"""
        stage_config = self.stage_configs[InterviewStage.INTRODUCTION_SETUP]
        questions = self.question_templates[InterviewStage.INTRODUCTION_SETUP]
        
        stage_flow = []
        
        # Step 1: Welcome and process explanation
        welcome_question = questions[0]
        welcome_text = welcome_question.text.format(
            position=self.job_role,
            duration=self.duration_minutes
        )
        
        stage_flow.append({
            "action": "ask_question",
            "question": {
                "id": welcome_question.id,
                "text": welcome_text,
                "type": welcome_question.type.value,
                "expected_duration": welcome_question.expected_duration_seconds,
                "requires_response": True,
                "response_type": "confirmation"
            }
        })
        
        # Step 2: Self introduction request
        intro_question = questions[1]
        intro_text = intro_question.text.format(position=self.job_role)
        
        stage_flow.append({
            "action": "ask_question",
            "question": {
                "id": intro_question.id,
                "text": intro_text,
                "type": intro_question.type.value,
                "expected_duration": intro_question.expected_duration_seconds,
                "requires_response": True,
                "response_type": "detailed_response",
                "scoring_enabled": True
            }
        })
        
        return {
            "stage": InterviewStage.INTRODUCTION_SETUP.value,
            "stage_name": stage_config.name,
            "duration_minutes": stage_config.duration_minutes,
            "flow": stage_flow,
            "next_stage": InterviewStage.TECHNICAL_THEORY.value
        }

    async def _execute_technical_theory_stage(self) -> Dict[str, Any]:
        """Execute Technical Theory Questions stage (15-20 minutes)"""
        stage_config = self.stage_configs[InterviewStage.TECHNICAL_THEORY]
        
        # Get adaptive questions from the adaptive engine
        technical_questions = await self._get_adaptive_technical_questions()
        
        stage_flow = []
        
        for question in technical_questions:
            stage_flow.append({
                "action": "ask_question",
                "question": {
                    "id": question["id"],
                    "text": question["text"],
                    "type": QuestionType.TECHNICAL_THEORY.value,
                    "expected_duration": question["expected_duration"],
                    "difficulty": question["difficulty"],
                    "topics": question["topics"],
                    "requires_response": True,
                    "response_type": "technical_explanation",
                    "scoring_enabled": True,
                    "real_time_scoring": True,
                    "follow_up_enabled": True
                }
            })
            
            # Add follow-up capability
            stage_flow.append({
                "action": "conditional_follow_up",
                "condition": "response_quality < 7.0 OR requires_clarification",
                "follow_up_types": ["clarification", "deeper_dive", "example_request"]
            })
        
        return {
            "stage": InterviewStage.TECHNICAL_THEORY.value,
            "stage_name": stage_config.name,
            "duration_minutes": stage_config.duration_minutes,
            "flow": stage_flow,
            "features": {
                "adaptive_difficulty": True,
                "real_time_scoring": True,
                "progressive_difficulty": True,
                "follow_up_questions": True
            },
            "next_stage": InterviewStage.CODING_CHALLENGES.value
        }

    async def _execute_coding_challenges_stage(self) -> Dict[str, Any]:
        """Execute Coding Challenges stage (20-25 minutes)"""
        stage_config = self.stage_configs[InterviewStage.CODING_CHALLENGES]
        
        # Get coding problems based on role and current theta
        coding_problems = await self._get_adaptive_coding_problems()
        
        stage_flow = []
        
        # Monaco Editor setup
        stage_flow.append({
            "action": "initialize_code_editor",
            "editor_config": {
                "language": self._determine_primary_language(),
                "theme": "vs-dark",
                "features": ["live_analysis", "syntax_highlighting", "auto_completion"],
                "multi_language_support": True
            }
        })
        
        for i, problem in enumerate(coding_problems, 1):
            stage_flow.append({
                "action": "present_coding_problem",
                "problem": {
                    "id": problem["id"],
                    "title": problem["title"],
                    "description": problem["description"],
                    "difficulty": problem["difficulty"],
                    "expected_duration": problem["expected_duration"],
                    "language": problem["language"],
                    "starter_code": problem.get("starter_code", ""),
                    "test_cases": problem.get("test_cases", []),
                    "evaluation_criteria": [
                        "correctness",
                        "efficiency", 
                        "code_quality",
                        "best_practices"
                    ]
                }
            })
            
            stage_flow.append({
                "action": "live_code_analysis",
                "features": {
                    "syntax_checking": True,
                    "logic_analysis": True,
                    "efficiency_scoring": True,
                    "best_practices_check": True,
                    "real_time_feedback": True
                }
            })
            
            stage_flow.append({
                "action": "interactive_debugging",
                "prompts": [
                    "Can you walk me through your approach?",
                    "How would you optimize this solution?",
                    "What edge cases should we consider?"
                ]
            })
        
        return {
            "stage": InterviewStage.CODING_CHALLENGES.value,
            "stage_name": stage_config.name,
            "duration_minutes": stage_config.duration_minutes,
            "flow": stage_flow,
            "features": {
                "monaco_editor": True,
                "live_code_analysis": True,
                "interactive_debugging": True,
                "multi_language_support": True,
                "real_time_feedback": True
            },
            "next_stage": InterviewStage.CULTURAL_BEHAVIORAL.value
        }

    async def _execute_cultural_behavioral_stage(self) -> Dict[str, Any]:
        """Execute Cultural Fit & Behavioral Questions stage (10-15 minutes)"""
        stage_config = self.stage_configs[InterviewStage.CULTURAL_BEHAVIORAL]
        
        # Get scenario-based questions
        behavioral_questions = await self._get_behavioral_scenarios()
        
        stage_flow = []
        
        for question in behavioral_questions:
            stage_flow.append({
                "action": "ask_behavioral_question",
                "question": {
                    "id": question["id"],
                    "text": question["text"],
                    "type": "behavioral_scenario",
                    "scenario_type": question["scenario_type"],
                    "expected_duration": question["expected_duration"],
                    "assessment_criteria": [
                        "leadership_potential",
                        "teamwork_skills",
                        "cultural_alignment",
                        "problem_solving_approach",
                        "communication_style"
                    ],
                    "requires_response": True,
                    "response_type": "scenario_based",
                    "scoring_enabled": True,
                    "follow_up_enabled": True
                }
            })
            
            # STAR method evaluation
            stage_flow.append({
                "action": "evaluate_star_format",
                "criteria": {
                    "situation": "Clear context provided",
                    "task": "Specific responsibility identified",
                    "action": "Detailed actions described",
                    "result": "Measurable outcomes shared"
                }
            })
        
        return {
            "stage": InterviewStage.CULTURAL_BEHAVIORAL.value,
            "stage_name": stage_config.name,
            "duration_minutes": stage_config.duration_minutes,
            "flow": stage_flow,
            "features": {
                "scenario_based_questions": True,
                "cultural_alignment_assessment": True,
                "leadership_evaluation": True,
                "teamwork_assessment": True,
                "star_format_evaluation": True
            },
            "next_stage": InterviewStage.CANDIDATE_QA.value
        }

    async def _execute_candidate_qa_stage(self) -> Dict[str, Any]:
        """Execute Candidate Q&A Session stage (5-10 minutes)"""
        stage_config = self.stage_configs[InterviewStage.CANDIDATE_QA]
        questions = self.question_templates[InterviewStage.CANDIDATE_QA]
        
        stage_flow = []
        
        # Initial Q&A invitation
        qa_question = questions[0]
        stage_flow.append({
            "action": "ask_question",
            "question": {
                "id": qa_question.id,
                "text": qa_question.text,
                "type": qa_question.type.value,
                "expected_duration": qa_question.expected_duration_seconds,
                "requires_response": True,
                "response_type": "candidate_questions",
                "scoring_enabled": False
            }
        })
        
        # Boundary management for inappropriate questions
        stage_flow.append({
            "action": "handle_candidate_questions",
            "boundary_responses": {
                "technical_solutions": "I'm here to assess your technical knowledge, so I can't provide answers to the technical questions we just discussed. However, I'm happy to answer questions about the role requirements, daily responsibilities, team dynamics, or growth opportunities. What would you like to know?",
                "interview_feedback": "I cannot provide feedback on your performance during the interview, but I can discuss the role expectations and what success looks like in this position.",
                "salary_benefits": "For specific compensation and benefits information, you'll be connected with our HR team during the next steps. I can discuss the role scope and growth opportunities.",
                "appropriate_topics": [
                    "role_responsibilities",
                    "team_structure", 
                    "company_culture",
                    "growth_opportunities",
                    "work_environment",
                    "project_types",
                    "technologies_used",
                    "team_collaboration"
                ]
            }
        })
        
        return {
            "stage": InterviewStage.CANDIDATE_QA.value,
            "stage_name": stage_config.name,
            "duration_minutes": stage_config.duration_minutes,
            "flow": stage_flow,
            "features": {
                "open_qa_format": True,
                "boundary_management": True,
                "professional_responses": True,
                "topic_filtering": True
            },
            "next_stage": InterviewStage.INTERVIEW_CONCLUSION.value
        }

    async def _execute_conclusion_stage(self) -> Dict[str, Any]:
        """Execute Interview Conclusion stage (2-3 minutes)"""
        stage_config = self.stage_configs[InterviewStage.INTERVIEW_CONCLUSION]
        questions = self.question_templates[InterviewStage.INTERVIEW_CONCLUSION]
        
        stage_flow = []
        
        # Professional conclusion
        conclusion_question = questions[0]
        conclusion_text = conclusion_question.text.format(
            candidate_name=self.candidate_profile.get("name", ""),
            timeframe="3-5 business days"
        )
        
        stage_flow.append({
            "action": "deliver_conclusion",
            "message": {
                "id": conclusion_question.id,
                "text": conclusion_text,
                "type": conclusion_question.type.value,
                "duration": conclusion_question.expected_duration_seconds
            }
        })
        
        # Generate final report
        stage_flow.append({
            "action": "generate_final_report",
            "report_components": [
                "overall_assessment",
                "technical_skills_breakdown",
                "behavioral_evaluation",
                "cultural_fit_analysis",
                "hiring_recommendation",
                "detailed_scoring",
                "bias_analysis",
                "conversation_transcript"
            ]
        })
        
        return {
            "stage": InterviewStage.INTERVIEW_CONCLUSION.value,
            "stage_name": stage_config.name,
            "duration_minutes": stage_config.duration_minutes,
            "flow": stage_flow,
            "features": {
                "professional_closure": True,
                "final_report_generation": True,
                "comprehensive_analysis": True,
                "next_steps_communication": True
            },
            "next_stage": None  # Interview complete
        }

    async def _get_adaptive_technical_questions(self) -> List[Dict[str, Any]]:
        """Get adaptive technical questions from the adaptive engine"""
        try:
            async with aiohttp.ClientSession() as session:
                payload = {
                    "session_id": self.session_id,
                    "candidate_profile": self.candidate_profile,
                    "job_role": self.job_role,
                    "current_theta": self.candidate_theta,
                    "stage": "technical_theory",
                    "duration_minutes": self.stage_configs[InterviewStage.TECHNICAL_THEORY].duration_minutes,
                    "answered_questions": [q.id for q in self.candidate_responses]
                }
                
                async with session.post(
                    f"{self.adaptive_engine_url}/get-technical-questions",
                    json=payload
                ) as response:
                    if response.status == 200:
                        result = await response.json()
                        return result.get("questions", [])
                    else:
                        logger.warning("Failed to get adaptive questions, using fallback")
                        return self._get_fallback_technical_questions()
                        
        except Exception as e:
            logger.error(f"Error getting adaptive technical questions: {e}")
            return self._get_fallback_technical_questions()

    async def _get_adaptive_coding_problems(self) -> List[Dict[str, Any]]:
        """Get adaptive coding problems based on current performance"""
        try:
            async with aiohttp.ClientSession() as session:
                payload = {
                    "session_id": self.session_id,
                    "candidate_profile": self.candidate_profile,
                    "job_role": self.job_role,
                    "current_theta": self.candidate_theta,
                    "stage": "coding_challenges",
                    "primary_language": self._determine_primary_language(),
                    "duration_minutes": self.stage_configs[InterviewStage.CODING_CHALLENGES].duration_minutes
                }
                
                async with session.post(
                    f"{self.adaptive_engine_url}/get-coding-problems",
                    json=payload
                ) as response:
                    if response.status == 200:
                        result = await response.json()
                        return result.get("problems", [])
                    else:
                        return self._get_fallback_coding_problems()
                        
        except Exception as e:
            logger.error(f"Error getting coding problems: {e}")
            return self._get_fallback_coding_problems()

    async def _get_behavioral_scenarios(self) -> List[Dict[str, Any]]:
        """Get behavioral scenario questions"""
        try:
            # Generate contextual behavioral questions
            scenarios = [
                {
                    "id": "behavioral_001",
                    "text": "Tell me about a time when you had to work under pressure to meet a tight deadline. How did you handle the situation and what was the outcome?",
                    "scenario_type": "pressure_handling",
                    "expected_duration": 180
                },
                {
                    "id": "behavioral_002", 
                    "text": "Describe a situation where you had to work with a difficult team member. How did you handle the disagreement and maintain team productivity?",
                    "scenario_type": "conflict_resolution",
                    "expected_duration": 180
                },
                {
                    "id": "behavioral_003",
                    "text": "Give me an example of a project you're particularly proud of. What was your role, what challenges did you face, and how did you overcome them?",
                    "scenario_type": "achievement_leadership",
                    "expected_duration": 200
                }
            ]
            return scenarios
            
        except Exception as e:
            logger.error(f"Error getting behavioral scenarios: {e}")
            return []

    def _determine_primary_language(self) -> str:
        """Determine primary programming language based on role and profile"""
        role_lower = self.job_role.lower()
        skills = self.candidate_profile.get("skills", [])
        
        # Language priority based on role
        if "backend" in role_lower or "java" in role_lower:
            return "java"
        elif "frontend" in role_lower or "react" in role_lower:
            return "javascript"
        elif "python" in role_lower or "data" in role_lower:
            return "python"
        elif "c#" in role_lower or ".net" in role_lower:
            return "csharp"
        
        # Check candidate skills
        for skill in skills:
            skill_lower = skill.lower()
            if skill_lower in ["java", "spring"]:
                return "java"
            elif skill_lower in ["javascript", "react", "node"]:
                return "javascript"
            elif skill_lower in ["python", "django", "flask"]:
                return "python"
        
        return "java"  # Default fallback

    def _get_fallback_technical_questions(self) -> List[Dict[str, Any]]:
        """Fallback technical questions"""
        return [
            {
                "id": "tech_001",
                "text": "Can you explain the difference between REST and GraphQL APIs? When would you choose one over the other?",
                "difficulty": 2.0,
                "expected_duration": 180,
                "topics": ["api_design", "rest", "graphql"]
            },
            {
                "id": "tech_002", 
                "text": "How do you handle error handling and exception management in your applications?",
                "difficulty": 2.5,
                "expected_duration": 200,
                "topics": ["error_handling", "exceptions", "best_practices"]
            },
            {
                "id": "tech_003",
                "text": "What are your thoughts on microservices vs monolithic architecture? What factors influence your choice?",
                "difficulty": 3.0,
                "expected_duration": 220,
                "topics": ["architecture", "microservices", "monolith"]
            }
        ]

    def _get_fallback_coding_problems(self) -> List[Dict[str, Any]]:
        """Fallback coding problems"""
        return [
            {
                "id": "code_001",
                "title": "Array Sum Problem",
                "description": "Given an array of integers, find two numbers that add up to a specific target.",
                "difficulty": 2.0,
                "expected_duration": 600,
                "language": self._determine_primary_language(),
                "starter_code": "// Write your solution here\n",
                "test_cases": ["[2,7,11,15], target=9 -> [0,1]"]
            },
            {
                "id": "code_002",
                "title": "String Reversal",
                "description": "Write a function to reverse a string without using built-in reverse methods.",
                "difficulty": 1.5,
                "expected_duration": 300,
                "language": self._determine_primary_language(),
                "starter_code": "// Implement string reversal\n"
            }
        ]

    async def get_next_stage(self) -> Optional[Dict[str, Any]]:
        """Get the next stage in the interview flow"""
        try:
            stages = list(InterviewStage)
            current_index = stages.index(self.current_stage)
            
            if current_index + 1 < len(stages):
                next_stage = stages[current_index + 1]
                self.current_stage_index = current_index + 1
                return await self._execute_stage(next_stage)
            else:
                logger.info("✅ Interview flow completed")
                return None
                
        except Exception as e:
            logger.error(f"❌ Error getting next stage: {e}")
            raise

    def get_current_stage_progress(self) -> Dict[str, Any]:
        """Get current stage progress information"""
        if not self.stage_start_time:
            return {"stage": "not_started", "progress": 0}
            
        elapsed_minutes = (datetime.now() - self.stage_start_time).total_seconds() / 60
        stage_config = self.stage_configs[self.current_stage]
        progress_percentage = min(100, (elapsed_minutes / stage_config.duration_minutes) * 100)
        
        return {
            "stage": self.current_stage.value,
            "stage_name": stage_config.name,
            "elapsed_minutes": round(elapsed_minutes, 1),
            "total_minutes": stage_config.duration_minutes,
            "progress_percentage": round(progress_percentage, 1),
            "questions_asked": self.questions_asked,
            "is_overtime": elapsed_minutes > stage_config.max_duration_minutes
        }

    def get_interview_summary(self) -> Dict[str, Any]:
        """Get complete interview summary"""
        total_duration = (datetime.now() - self.interview_start_time).total_seconds() / 60
        
        return {
            "session_id": self.session_id,
            "candidate_profile": self.candidate_profile,
            "job_role": self.job_role,
            "interview_duration_minutes": round(total_duration, 1),
            "current_stage": self.current_stage.value,
            "questions_asked": self.questions_asked,
            "candidate_theta": self.candidate_theta,
            "conversation_history": self.conversation_history,
            "real_time_scores": self.real_time_scores,
            "stage_progress": self.get_current_stage_progress(),
            "completed_stages": [s.value for s in list(InterviewStage)[:self.current_stage_index]],
            "interview_start_time": self.interview_start_time.isoformat(),
            "status": "in_progress" if self.current_stage != InterviewStage.INTERVIEW_CONCLUSION else "completed"
        }
