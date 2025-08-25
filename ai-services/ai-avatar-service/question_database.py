"""
Alex AI Extended Question Database
================================

Comprehensive question sets organized by domains and difficulty levels,
replicating Alex AI's sophisticated questioning approach.
"""

from dataclasses import dataclass
from typing import Dict, List, Any
from enum import Enum

class QuestionType(Enum):
    TECHNICAL_CONCEPT = "technical_concept"
    CODING_CHALLENGE = "coding_challenge"
    SCENARIO_BASED = "scenario_based"
    FOLLOW_UP = "follow_up"
    CLARIFICATION = "clarification"
    BEHAVIORAL = "behavioral"

@dataclass
class InterviewQuestion:
    id: str
    text: str
    type: QuestionType
    domain: str
    difficulty: int  # 1-5 scale
    expected_concepts: List[str]
    follow_up_questions: List[str]
    evaluation_criteria: Dict[str, Any]
    max_response_time: int = 300

class AlexQuestionBank:
    """Extended question bank with comprehensive coverage"""
    
    @staticmethod
    def get_all_questions() -> Dict[str, List[InterviewQuestion]]:
        return {
            "python": [
                # Python Basic Concepts
                InterviewQuestion(
                    id="py_001",
                    text="Can you explain the difference between tuples and lists in Python? When would you use one over the other?",
                    type=QuestionType.TECHNICAL_CONCEPT,
                    domain="python",
                    difficulty=2,
                    expected_concepts=["mutability", "immutability", "performance", "use_cases", "memory"],
                    follow_up_questions=[
                        "Can you give me an example of when immutability would be beneficial?",
                        "How does Python handle tuple packing and unpacking?",
                        "What are the memory implications of using tuples vs lists?"
                    ],
                    evaluation_criteria={
                        "concept_understanding": 0.4,
                        "practical_examples": 0.3,
                        "technical_depth": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="py_002",
                    text="Walk me through how you would implement a decorator in Python. What problems do decorators solve?",
                    type=QuestionType.TECHNICAL_CONCEPT,
                    domain="python",
                    difficulty=3,
                    expected_concepts=["higher_order_functions", "wrapper_functions", "syntactic_sugar", "use_cases"],
                    follow_up_questions=[
                        "Can you show me how to create a decorator that measures execution time?",
                        "How would you handle decorators with arguments?",
                        "What's the difference between function decorators and class decorators?"
                    ],
                    evaluation_criteria={
                        "implementation_knowledge": 0.5,
                        "use_case_understanding": 0.3,
                        "syntax_accuracy": 0.2
                    }
                ),
                
                InterviewQuestion(
                    id="py_003",
                    text="Explain the Global Interpreter Lock (GIL) in Python. How does it affect multi-threading, and what alternatives would you consider?",
                    type=QuestionType.TECHNICAL_CONCEPT,
                    domain="python",
                    difficulty=4,
                    expected_concepts=["GIL", "threading", "multiprocessing", "concurrency", "performance"],
                    follow_up_questions=[
                        "When would you use threading vs multiprocessing?",
                        "How does asyncio relate to the GIL?",
                        "What are some ways to work around GIL limitations?"
                    ],
                    evaluation_criteria={
                        "advanced_understanding": 0.5,
                        "practical_solutions": 0.3,
                        "alternatives_knowledge": 0.2
                    }
                ),
                
                InterviewQuestion(
                    id="py_004",
                    text="How would you optimize a Python function that's running slowly? Walk me through your debugging process.",
                    type=QuestionType.SCENARIO_BASED,
                    domain="python",
                    difficulty=3,
                    expected_concepts=["profiling", "optimization", "debugging", "performance", "bottlenecks"],
                    follow_up_questions=[
                        "What tools would you use for profiling Python code?",
                        "How would you identify memory leaks?",
                        "What are some common performance pitfalls in Python?"
                    ],
                    evaluation_criteria={
                        "systematic_approach": 0.4,
                        "tool_knowledge": 0.3,
                        "optimization_techniques": 0.3
                    }
                ),
            ],
            
            "statistics": [
                InterviewQuestion(
                    id="stats_001",
                    text="Let's say we want to test whether a CEO's resignation impacts a company's stock price. How would you set up a hypothesis test for this?",
                    type=QuestionType.SCENARIO_BASED,
                    domain="statistics",
                    difficulty=3,
                    expected_concepts=["null_hypothesis", "alternative_hypothesis", "significance_level", "test_statistic"],
                    follow_up_questions=[
                        "What would your null and alternative hypotheses be?",
                        "How would you collect and structure the data?",
                        "What statistical test would be most appropriate and why?",
                        "How would you ensure the validity of your results?"
                    ],
                    evaluation_criteria={
                        "hypothesis_formulation": 0.3,
                        "methodology": 0.4,
                        "validity_considerations": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="stats_002",
                    text="Explain the difference between Type I and Type II errors. Can you give me a real-world example where each would be problematic?",
                    type=QuestionType.TECHNICAL_CONCEPT,
                    domain="statistics",
                    difficulty=2,
                    expected_concepts=["type_i_error", "type_ii_error", "false_positive", "false_negative", "power"],
                    follow_up_questions=[
                        "How would you balance the risk of Type I vs Type II errors?",
                        "What factors affect the power of a statistical test?",
                        "In what scenarios might a Type II error be more costly than a Type I error?"
                    ],
                    evaluation_criteria={
                        "conceptual_understanding": 0.4,
                        "real_world_application": 0.4,
                        "trade_off_awareness": 0.2
                    }
                ),
                
                InterviewQuestion(
                    id="stats_003",
                    text="You notice that your model's performance is great on training data but poor on test data. What might be happening and how would you address it?",
                    type=QuestionType.SCENARIO_BASED,
                    domain="statistics",
                    difficulty=3,
                    expected_concepts=["overfitting", "bias_variance_tradeoff", "cross_validation", "regularization"],
                    follow_up_questions=[
                        "What techniques would you use to detect overfitting?",
                        "How would you choose between different regularization methods?",
                        "What role does feature selection play in addressing overfitting?"
                    ],
                    evaluation_criteria={
                        "problem_identification": 0.3,
                        "solution_strategies": 0.4,
                        "prevention_methods": 0.3
                    }
                ),
            ],
            
            "data_science": [
                InterviewQuestion(
                    id="ds_001",
                    text="You're analyzing user behavior data and notice some unusual patterns. Walk me through your process for determining if these are outliers or legitimate data points.",
                    type=QuestionType.SCENARIO_BASED,
                    domain="data_science",
                    difficulty=3,
                    expected_concepts=["outlier_detection", "data_validation", "statistical_methods", "domain_knowledge"],
                    follow_up_questions=[
                        "What statistical methods would you use for outlier detection?",
                        "How would domain knowledge influence your decision?",
                        "What are the risks of removing vs keeping outliers?"
                    ],
                    evaluation_criteria={
                        "analytical_approach": 0.4,
                        "methodology": 0.3,
                        "business_impact": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="ds_002",
                    text="Describe how you would approach building a recommendation system for an e-commerce platform. What factors would you consider?",
                    type=QuestionType.SCENARIO_BASED,
                    domain="data_science",
                    difficulty=4,
                    expected_concepts=["collaborative_filtering", "content_based", "cold_start", "evaluation_metrics"],
                    follow_up_questions=[
                        "How would you handle the cold start problem?",
                        "What metrics would you use to evaluate recommendation quality?",
                        "How would you balance diversity vs accuracy in recommendations?"
                    ],
                    evaluation_criteria={
                        "system_design": 0.4,
                        "problem_awareness": 0.3,
                        "evaluation_approach": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="ds_003",
                    text="A business stakeholder asks you to predict customer churn. How would you approach this problem from start to finish?",
                    type=QuestionType.SCENARIO_BASED,
                    domain="data_science",
                    difficulty=3,
                    expected_concepts=["problem_definition", "feature_engineering", "model_selection", "business_metrics"],
                    follow_up_questions=[
                        "How would you define 'churn' for this business?",
                        "What features would be most predictive of churn?",
                        "How would you translate model predictions into business actions?"
                    ],
                    evaluation_criteria={
                        "problem_scoping": 0.3,
                        "technical_approach": 0.4,
                        "business_alignment": 0.3
                    }
                ),
            ],
            
            "machine_learning": [
                InterviewQuestion(
                    id="ml_001",
                    text="Explain the bias-variance tradeoff. How would you diagnose whether your model has high bias or high variance?",
                    type=QuestionType.TECHNICAL_CONCEPT,
                    domain="machine_learning",
                    difficulty=3,
                    expected_concepts=["bias", "variance", "underfitting", "overfitting", "learning_curves"],
                    follow_up_questions=[
                        "What would you do if your model had high bias?",
                        "How would you address high variance?",
                        "What role does regularization play in this tradeoff?"
                    ],
                    evaluation_criteria={
                        "conceptual_understanding": 0.4,
                        "diagnostic_skills": 0.3,
                        "solution_knowledge": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="ml_002",
                    text="You have a dataset with 1000 samples and 10,000 features. What challenges does this present and how would you address them?",
                    type=QuestionType.SCENARIO_BASED,
                    domain="machine_learning",
                    difficulty=4,
                    expected_concepts=["curse_of_dimensionality", "feature_selection", "regularization", "overfitting"],
                    follow_up_questions=[
                        "What feature selection techniques would you consider?",
                        "How would you prevent overfitting in this scenario?",
                        "What types of models work well with high-dimensional data?"
                    ],
                    evaluation_criteria={
                        "problem_recognition": 0.3,
                        "solution_strategies": 0.4,
                        "method_selection": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="ml_003",
                    text="Describe the difference between bagging and boosting ensemble methods. When would you choose one over the other?",
                    type=QuestionType.TECHNICAL_CONCEPT,
                    domain="machine_learning",
                    difficulty=3,
                    expected_concepts=["bagging", "boosting", "ensemble", "variance_reduction", "bias_reduction"],
                    follow_up_questions=[
                        "Can you explain how Random Forest implements bagging?",
                        "How does AdaBoost work differently from bagging?",
                        "What are the computational trade-offs between these methods?"
                    ],
                    evaluation_criteria={
                        "method_understanding": 0.4,
                        "comparison_ability": 0.3,
                        "practical_considerations": 0.3
                    }
                ),
            ],
            
            "sql_databases": [
                InterviewQuestion(
                    id="sql_001",
                    text="You need to find the top 3 customers by total purchase amount in the last 6 months. How would you write this query?",
                    type=QuestionType.CODING_CHALLENGE,
                    domain="sql_databases",
                    difficulty=2,
                    expected_concepts=["aggregation", "joins", "date_filtering", "ordering", "limit"],
                    follow_up_questions=[
                        "How would you handle ties in purchase amounts?",
                        "What if you needed to include customer information from another table?",
                        "How would you optimize this query for a large dataset?"
                    ],
                    evaluation_criteria={
                        "sql_syntax": 0.3,
                        "logic_correctness": 0.4,
                        "optimization_awareness": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="sql_002",
                    text="Explain the different types of SQL joins. Can you give me a scenario where you'd use a LEFT JOIN vs an INNER JOIN?",
                    type=QuestionType.TECHNICAL_CONCEPT,
                    domain="sql_databases",
                    difficulty=2,
                    expected_concepts=["inner_join", "left_join", "right_join", "full_join", "cross_join"],
                    follow_up_questions=[
                        "What happens when you join tables with duplicate keys?",
                        "How would you handle NULL values in joins?",
                        "When might you use a CROSS JOIN?"
                    ],
                    evaluation_criteria={
                        "join_understanding": 0.4,
                        "scenario_application": 0.3,
                        "edge_case_awareness": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="sql_003",
                    text="A query that normally runs in seconds is now taking minutes. How would you troubleshoot and optimize it?",
                    type=QuestionType.SCENARIO_BASED,
                    domain="sql_databases",
                    difficulty=3,
                    expected_concepts=["query_execution_plan", "indexing", "performance_tuning", "statistics"],
                    follow_up_questions=[
                        "What tools would you use to analyze query performance?",
                        "How do indexes help with query performance?",
                        "What are some common causes of query performance degradation?"
                    ],
                    evaluation_criteria={
                        "diagnostic_approach": 0.4,
                        "optimization_techniques": 0.3,
                        "tool_knowledge": 0.3
                    }
                ),
            ],
            
            "web_development": [
                InterviewQuestion(
                    id="web_001",
                    text="Explain the difference between server-side rendering and client-side rendering. What are the trade-offs?",
                    type=QuestionType.TECHNICAL_CONCEPT,
                    domain="web_development",
                    difficulty=3,
                    expected_concepts=["SSR", "CSR", "performance", "SEO", "user_experience"],
                    follow_up_questions=[
                        "When would you choose SSR over CSR?",
                        "How do modern frameworks like Next.js handle this?",
                        "What impact does this choice have on SEO?"
                    ],
                    evaluation_criteria={
                        "concept_understanding": 0.4,
                        "trade_off_analysis": 0.3,
                        "practical_application": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="web_002",
                    text="How would you handle authentication and authorization in a web application? Walk me through your security considerations.",
                    type=QuestionType.SCENARIO_BASED,
                    domain="web_development",
                    difficulty=4,
                    expected_concepts=["authentication", "authorization", "JWT", "sessions", "security"],
                    follow_up_questions=[
                        "What are the pros and cons of JWT vs session-based authentication?",
                        "How would you handle password security?",
                        "What measures would you take to prevent common attacks like XSS and CSRF?"
                    ],
                    evaluation_criteria={
                        "security_awareness": 0.4,
                        "implementation_knowledge": 0.3,
                        "best_practices": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="web_003",
                    text="Your web application is loading slowly. How would you identify and fix performance bottlenecks?",
                    type=QuestionType.SCENARIO_BASED,
                    domain="web_development",
                    difficulty=3,
                    expected_concepts=["performance_optimization", "profiling", "caching", "bundling", "lazy_loading"],
                    follow_up_questions=[
                        "What tools would you use to measure web performance?",
                        "How would you optimize bundle size?",
                        "What caching strategies would you implement?"
                    ],
                    evaluation_criteria={
                        "diagnostic_approach": 0.3,
                        "optimization_techniques": 0.4,
                        "tooling_knowledge": 0.3
                    }
                ),
            ],
            
            "system_design": [
                InterviewQuestion(
                    id="sys_001",
                    text="Design a URL shortening service like bit.ly. What are the key components and how would you scale it?",
                    type=QuestionType.SCENARIO_BASED,
                    domain="system_design",
                    difficulty=4,
                    expected_concepts=["scalability", "database_design", "caching", "load_balancing", "distributed_systems"],
                    follow_up_questions=[
                        "How would you generate unique short URLs?",
                        "How would you handle millions of requests per second?",
                        "What database would you choose and why?"
                    ],
                    evaluation_criteria={
                        "system_architecture": 0.4,
                        "scalability_design": 0.3,
                        "trade_off_decisions": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="sys_002",
                    text="Explain the CAP theorem. How would it influence your design decisions for a distributed database?",
                    type=QuestionType.TECHNICAL_CONCEPT,
                    domain="system_design",
                    difficulty=4,
                    expected_concepts=["consistency", "availability", "partition_tolerance", "distributed_systems"],
                    follow_up_questions=[
                        "Can you give examples of systems that prioritize CP vs AP?",
                        "How do modern databases handle the CAP theorem trade-offs?",
                        "What is eventual consistency and when would you use it?"
                    ],
                    evaluation_criteria={
                        "theoretical_understanding": 0.4,
                        "practical_examples": 0.3,
                        "design_implications": 0.3
                    }
                ),
            ],
            
            "behavioral": [
                InterviewQuestion(
                    id="beh_001",
                    text="Tell me about a time when you had to learn a new technology quickly for a project. How did you approach it?",
                    type=QuestionType.BEHAVIORAL,
                    domain="behavioral",
                    difficulty=2,
                    expected_concepts=["learning_ability", "adaptability", "problem_solving", "time_management"],
                    follow_up_questions=[
                        "What resources did you use to learn quickly?",
                        "How did you validate your understanding?",
                        "What would you do differently next time?"
                    ],
                    evaluation_criteria={
                        "learning_strategy": 0.4,
                        "execution": 0.3,
                        "self_reflection": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="beh_002",
                    text="Describe a situation where you disagreed with a technical decision made by your team. How did you handle it?",
                    type=QuestionType.BEHAVIORAL,
                    domain="behavioral",
                    difficulty=3,
                    expected_concepts=["communication", "collaboration", "technical_judgment", "conflict_resolution"],
                    follow_up_questions=[
                        "How did you present your alternative viewpoint?",
                        "What was the outcome?",
                        "How do you balance being assertive with being collaborative?"
                    ],
                    evaluation_criteria={
                        "communication_skills": 0.4,
                        "technical_reasoning": 0.3,
                        "team_dynamics": 0.3
                    }
                ),
                
                InterviewQuestion(
                    id="beh_003",
                    text="Tell me about a project that didn't go as planned. What went wrong and how did you handle it?",
                    type=QuestionType.BEHAVIORAL,
                    domain="behavioral",
                    difficulty=2,
                    expected_concepts=["resilience", "problem_solving", "accountability", "learning"],
                    follow_up_questions=[
                        "What early warning signs did you miss?",
                        "How did you communicate the issues to stakeholders?",
                        "What processes did you put in place to prevent similar issues?"
                    ],
                    evaluation_criteria={
                        "problem_handling": 0.4,
                        "accountability": 0.3,
                        "learning_from_failure": 0.3
                    }
                ),
            ]
        }
    
    @staticmethod
    def get_questions_by_domain(domain: str) -> List[InterviewQuestion]:
        """Get all questions for a specific domain"""
        all_questions = AlexQuestionBank.get_all_questions()
        return all_questions.get(domain, [])
    
    @staticmethod
    def get_questions_by_difficulty(domain: str, difficulty: int) -> List[InterviewQuestion]:
        """Get questions filtered by difficulty level"""
        domain_questions = AlexQuestionBank.get_questions_by_domain(domain)
        return [q for q in domain_questions if q.difficulty == difficulty]
    
    @staticmethod
    def get_adaptive_questions(domain: str, previous_scores: List[float]) -> List[InterviewQuestion]:
        """
        Get adaptive questions based on previous performance
        Similar to how Alex AI adjusts difficulty dynamically
        """
        domain_questions = AlexQuestionBank.get_questions_by_domain(domain)
        
        if not previous_scores:
            # Start with medium difficulty
            return [q for q in domain_questions if q.difficulty == 2]
        
        avg_score = sum(previous_scores) / len(previous_scores)
        
        if avg_score >= 0.8:
            # High performance, increase difficulty
            target_difficulty = min(5, max(q.difficulty for q in domain_questions if len([s for s in previous_scores if s >= 0.8]) >= 2))
        elif avg_score >= 0.6:
            # Medium performance, maintain or slightly increase
            target_difficulty = 3
        else:
            # Low performance, use easier questions or provide more support
            target_difficulty = 2
        
        return [q for q in domain_questions if q.difficulty == target_difficulty]
    
    @staticmethod
    def get_domain_coverage() -> Dict[str, int]:
        """Get question count by domain for analytics"""
        all_questions = AlexQuestionBank.get_all_questions()
        return {domain: len(questions) for domain, questions in all_questions.items()}
    
    @staticmethod
    def get_difficulty_distribution(domain: str = None) -> Dict[int, int]:
        """Get difficulty distribution for questions"""
        if domain:
            questions = AlexQuestionBank.get_questions_by_domain(domain)
        else:
            all_questions = AlexQuestionBank.get_all_questions()
            questions = [q for domain_questions in all_questions.values() for q in domain_questions]
        
        distribution = {}
        for q in questions:
            distribution[q.difficulty] = distribution.get(q.difficulty, 0) + 1
        
        return distribution
