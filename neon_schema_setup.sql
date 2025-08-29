-- ARIA PostgreSQL Schema Setup for Neon Database
-- Converted from MySQL to PostgreSQL syntax

-- Drop tables if they exist (for clean setup)
DROP TABLE IF EXISTS candidate_responses CASCADE;
DROP TABLE IF EXISTS interview_sessions CASCADE;
DROP TABLE IF EXISTS learning_question_effectiveness CASCADE;
DROP TABLE IF EXISTS learning_interview_outcomes CASCADE;
DROP TABLE IF EXISTS question_irt_parameters CASCADE;
DROP TABLE IF EXISTS questions CASCADE;
DROP TABLE IF EXISTS candidates CASCADE;

-- Create experience level enum type
CREATE TYPE experience_level AS ENUM ('entry', 'junior', 'mid', 'senior', 'staff', 'principal');
CREATE TYPE question_type AS ENUM ('technical', 'problem_solving', 'coding', 'system_design', 'behavioral', 'conceptual', 'scenario');
CREATE TYPE interview_type AS ENUM ('technical', 'behavioral', 'mixed');
CREATE TYPE session_status AS ENUM ('scheduled', 'in_progress', 'completed', 'cancelled');

-- Create candidates table
CREATE TABLE candidates (
    candidate_id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    job_role VARCHAR(100) NOT NULL,
    experience_level experience_level NOT NULL,
    experience_years INTEGER DEFAULT 0,
    technologies JSONB,
    demographics JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for candidates
CREATE INDEX idx_candidates_job_role ON candidates(job_role);
CREATE INDEX idx_candidates_experience_level ON candidates(experience_level);

-- Create questions table
CREATE TABLE questions (
    question_id SERIAL PRIMARY KEY,
    question_text TEXT NOT NULL,
    question_type question_type NOT NULL,
    created_by INTEGER,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for questions
CREATE INDEX idx_questions_type ON questions(question_type);
CREATE INDEX idx_questions_active ON questions(active);

-- Create question IRT parameters table
CREATE TABLE question_irt_parameters (
    question_id INTEGER PRIMARY KEY,
    difficulty DECIMAL(5,3) NOT NULL, -- IRT difficulty parameter (b)
    discrimination DECIMAL(5,3) NOT NULL DEFAULT 1.000, -- IRT discrimination parameter (a)
    guessing_parameter DECIMAL(4,3) NOT NULL DEFAULT 0.000, -- IRT guessing parameter (c)
    category VARCHAR(100) NOT NULL DEFAULT 'general',
    technologies JSONB, -- Required technologies for this question
    skill_areas JSONB, -- Skills tested by this question
    expected_duration_minutes INTEGER DEFAULT 5,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE
);

-- Create indexes for question IRT parameters
CREATE INDEX idx_irt_difficulty ON question_irt_parameters(difficulty);
CREATE INDEX idx_irt_discrimination ON question_irt_parameters(discrimination);
CREATE INDEX idx_irt_category ON question_irt_parameters(category);

-- Create interview sessions table
CREATE TABLE interview_sessions (
    session_id VARCHAR(50) PRIMARY KEY,
    candidate_id INTEGER NOT NULL,
    recruiter_id INTEGER,
    interview_type interview_type DEFAULT 'technical',
    status session_status DEFAULT 'scheduled',
    current_theta DECIMAL(5,3) DEFAULT 0.000, -- Current ability estimate
    current_se DECIMAL(5,3) DEFAULT 1.000, -- Current standard error
    questions_asked INTEGER DEFAULT 0,
    total_duration_minutes INTEGER DEFAULT 0,
    final_score DECIMAL(4,2),
    confidence_level DECIMAL(4,3),
    bias_flags JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (candidate_id) REFERENCES candidates(candidate_id) ON DELETE CASCADE
);

-- Create indexes for interview sessions
CREATE INDEX idx_sessions_status ON interview_sessions(status);
CREATE INDEX idx_sessions_candidate_id ON interview_sessions(candidate_id);
CREATE INDEX idx_sessions_created_at ON interview_sessions(created_at);

-- Create candidate responses table
CREATE TABLE candidate_responses (
    response_id SERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    candidate_id INTEGER NOT NULL,
    question_id INTEGER NOT NULL,
    response_text TEXT,
    response_data JSONB, -- Contains score, time_taken, code_solution, etc.
    score DECIMAL(4,2), -- Question score (0.00 to 1.00)
    partial_credit DECIMAL(4,2),
    time_taken_seconds INTEGER,
    theta_before DECIMAL(5,3), -- Theta before this response
    theta_after DECIMAL(5,3), -- Theta after this response
    theta_change DECIMAL(5,3), -- Change in theta from this response
    information_gain DECIMAL(5,3), -- Information gained from this response
    bias_flags JSONB, -- Any bias detection flags for this response
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (candidate_id) REFERENCES candidates(candidate_id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE
);

-- Create indexes for candidate responses
CREATE INDEX idx_responses_session_id ON candidate_responses(session_id);
CREATE INDEX idx_responses_candidate_id ON candidate_responses(candidate_id);
CREATE INDEX idx_responses_question_id ON candidate_responses(question_id);
CREATE INDEX idx_responses_score ON candidate_responses(score);
CREATE INDEX idx_responses_created_at ON candidate_responses(created_at);

-- Create learning outcomes table
CREATE TABLE learning_interview_outcomes (
    outcome_id SERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    candidate_id INTEGER NOT NULL,
    final_theta DECIMAL(5,3) NOT NULL,
    confidence_level DECIMAL(4,3) NOT NULL,
    total_questions INTEGER NOT NULL,
    interview_duration INTEGER NOT NULL,
    candidate_feedback JSONB,
    recruiter_rating DECIMAL(3,2),
    hire_decision BOOLEAN,
    actual_performance DECIMAL(3,2), -- Post-hire performance if available
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for learning outcomes
CREATE INDEX idx_outcomes_session_id ON learning_interview_outcomes(session_id);
CREATE INDEX idx_outcomes_processed_at ON learning_interview_outcomes(processed_at);
CREATE INDEX idx_outcomes_hire_decision ON learning_interview_outcomes(hire_decision);

-- Create question effectiveness tracking table
CREATE TABLE learning_question_effectiveness (
    effectiveness_id SERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    question_id INTEGER NOT NULL,
    information_gain DECIMAL(5,3) NOT NULL,
    discrimination_accuracy DECIMAL(4,3) NOT NULL,
    bias_score DECIMAL(4,3) NOT NULL,
    candidate_satisfaction DECIMAL(3,2) NOT NULL,
    time_efficiency DECIMAL(4,3) NOT NULL,
    skill_coverage DECIMAL(4,3) NOT NULL,
    overall_score DECIMAL(4,3) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for question effectiveness
CREATE INDEX idx_effectiveness_question_id ON learning_question_effectiveness(question_id);
CREATE INDEX idx_effectiveness_overall_score ON learning_question_effectiveness(overall_score);
CREATE INDEX idx_effectiveness_processed_at ON learning_question_effectiveness(processed_at);

-- Insert sample questions for backend engineering interviews
INSERT INTO questions (question_text, question_type) VALUES
-- Technical Questions
('Explain the difference between HTTP and HTTPS protocols. What are the key security benefits of HTTPS?', 'technical'),
('What is database normalization and why is it important? Explain the first three normal forms.', 'technical'),
('Describe the principles of RESTful API design. What makes an API RESTful?', 'technical'),
('What is the difference between SQL and NoSQL databases? When would you choose one over the other?', 'technical'),
('Explain caching strategies in web applications. What are the different levels of caching?', 'technical'),

-- Problem Solving Questions
('You have a web application that is experiencing slow response times. Walk me through your debugging process.', 'problem_solving'),
('Design a system to handle 1 million concurrent users. What are the key considerations?', 'problem_solving'),
('How would you implement rate limiting for an API? Describe different approaches.', 'problem_solving'),
('You need to migrate a monolithic application to microservices. What is your approach?', 'problem_solving'),
('How would you handle data consistency in a distributed system?', 'problem_solving'),

-- Coding Questions
('Write a function to find the two numbers in an array that add up to a target sum.', 'coding'),
('Implement a simple LRU (Least Recently Used) cache with get and put operations.', 'coding'),
('Write a function to validate if a string is a valid JSON format.', 'coding'),
('Implement a binary search algorithm and analyze its time complexity.', 'coding'),
('Write a function to merge two sorted linked lists into one sorted list.', 'coding'),

-- System Design Questions
('Design a URL shortener service like bit.ly. Consider scalability and performance.', 'system_design'),
('Design a chat application that supports real-time messaging for millions of users.', 'system_design'),
('Design a notification system that can handle different types of notifications (email, push, SMS).', 'system_design'),
('Design a distributed file storage system like Google Drive or Dropbox.', 'system_design'),
('Design a recommendation system for an e-commerce platform.', 'system_design'),

-- Conceptual Questions
('What are the ACID properties in databases? Explain each property.', 'conceptual'),
('Explain the CAP theorem and its implications for distributed systems.', 'conceptual'),
('What is the difference between authentication and authorization?', 'conceptual'),
('Describe the concept of eventual consistency in distributed databases.', 'conceptual'),
('What are design patterns? Explain the Singleton and Factory patterns.', 'conceptual');

-- Insert IRT parameters for the sample questions
INSERT INTO question_irt_parameters (question_id, difficulty, discrimination, guessing_parameter, category, technologies, skill_areas, expected_duration_minutes) VALUES
-- Technical Questions (difficulty range: -1.5 to 1.5)
(1, -0.8, 1.2, 0.0, 'networking', '["http", "security"]', '["web_protocols", "security"]', 3),
(2, 0.2, 1.4, 0.0, 'database', '["sql", "database"]', '["database_design", "normalization"]', 4),
(3, -0.5, 1.3, 0.0, 'api_design', '["rest", "api"]', '["api_design", "web_services"]', 4),
(4, 0.1, 1.1, 0.0, 'database', '["sql", "nosql"]', '["database_design", "data_modeling"]', 5),
(5, 0.8, 1.2, 0.0, 'caching', '["redis", "memcached"]', '["performance", "caching"]', 4),

-- Problem Solving Questions (difficulty range: 0.0 to 2.0)
(6, 0.5, 1.5, 0.0, 'debugging', '["debugging", "performance"]', '["problem_solving", "debugging"]', 6),
(7, 1.8, 1.4, 0.0, 'scalability', '["distributed_systems", "load_balancing"]', '["scalability", "system_design"]', 8),
(8, 1.2, 1.3, 0.0, 'api_security', '["rate_limiting", "security"]', '["security", "api_design"]', 5),
(9, 1.6, 1.2, 0.0, 'architecture', '["microservices", "architecture"]', '["architecture", "migration"]', 7),
(10, 1.4, 1.3, 0.0, 'consistency', '["distributed_systems", "databases"]', '["data_consistency", "distributed_systems"]', 6),

-- Coding Questions (difficulty range: 0.3 to 1.8)
(11, 0.3, 1.6, 0.0, 'algorithms', '["arrays", "hash_tables"]', '["algorithms", "data_structures"]', 4),
(12, 1.2, 1.5, 0.0, 'data_structures', '["cache", "linked_lists"]', '["data_structures", "algorithms"]', 6),
(13, 0.8, 1.1, 0.0, 'parsing', '["json", "validation"]', '["string_processing", "validation"]', 4),
(14, 1.0, 1.4, 0.0, 'search_algorithms', '["binary_search", "complexity"]', '["algorithms", "complexity_analysis"]', 5),
(15, 1.1, 1.3, 0.0, 'linked_lists', '["linked_lists", "sorting"]', '["data_structures", "algorithms"]', 5),

-- System Design Questions (difficulty range: 1.0 to 2.5)
(16, 1.8, 1.2, 0.0, 'scalable_systems', '["distributed_systems", "caching"]', '["system_design", "scalability"]', 10),
(17, 2.2, 1.1, 0.0, 'real_time_systems', '["websockets", "message_queues"]', '["real_time", "system_design"]', 12),
(18, 2.0, 1.3, 0.0, 'notification_systems', '["queues", "distributed_systems"]', '["system_design", "messaging"]', 10),
(19, 2.4, 1.2, 0.0, 'storage_systems', '["distributed_storage", "consistency"]', '["storage", "system_design"]', 15),
(20, 2.1, 1.4, 0.0, 'recommendation_systems', '["machine_learning", "big_data"]', '["algorithms", "data_processing"]', 12),

-- Conceptual Questions (difficulty range: -0.5 to 1.2)
(21, 0.0, 1.3, 0.0, 'database_concepts', '["sql", "transactions"]', '["database_theory", "transactions"]', 4),
(22, 0.8, 1.2, 0.0, 'distributed_systems', '["cap_theorem", "consistency"]', '["distributed_systems", "theory"]', 5),
(23, -0.2, 1.4, 0.0, 'security', '["authentication", "authorization"]', '["security", "access_control"]', 3),
(24, 1.0, 1.1, 0.0, 'consistency', '["eventual_consistency", "databases"]', '["distributed_systems", "data_consistency"]', 4),
(25, 0.4, 1.3, 0.0, 'design_patterns', '["singleton", "factory", "patterns"]', '["software_engineering", "design_patterns"]', 4);

-- Create users table (for Spring Security integration)
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(50) DEFAULT 'CANDIDATE',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for users
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Create interview sessions table
CREATE TABLE interview_sessions (
    session_id VARCHAR(50) PRIMARY KEY,
    candidate_id INTEGER NOT NULL,
    recruiter_id INTEGER,
    interview_type interview_type DEFAULT 'technical',
    status session_status DEFAULT 'scheduled',
    current_theta DECIMAL(5,3) DEFAULT 0.000, -- Current ability estimate
    current_se DECIMAL(5,3) DEFAULT 1.000, -- Current standard error
    questions_asked INTEGER DEFAULT 0,
    total_duration_minutes INTEGER DEFAULT 0,
    final_score DECIMAL(4,2),
    confidence_level DECIMAL(4,3),
    bias_flags JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (candidate_id) REFERENCES candidates(candidate_id) ON DELETE CASCADE
);

-- Create indexes for interview sessions
CREATE INDEX idx_sessions_status ON interview_sessions(status);
CREATE INDEX idx_sessions_candidate_id ON interview_sessions(candidate_id);
CREATE INDEX idx_sessions_created_at ON interview_sessions(created_at);

-- Create candidate responses table
CREATE TABLE candidate_responses (
    response_id SERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    candidate_id INTEGER NOT NULL,
    question_id INTEGER NOT NULL,
    response_text TEXT,
    response_data JSONB, -- Contains score, time_taken, code_solution, etc.
    score DECIMAL(4,2), -- Question score (0.00 to 1.00)
    partial_credit DECIMAL(4,2),
    time_taken_seconds INTEGER,
    theta_before DECIMAL(5,3), -- Theta before this response
    theta_after DECIMAL(5,3), -- Theta after this response
    theta_change DECIMAL(5,3), -- Change in theta from this response
    information_gain DECIMAL(5,3), -- Information gained from this response
    bias_flags JSONB, -- Any bias detection flags for this response
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (candidate_id) REFERENCES candidates(candidate_id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE
);

-- Create indexes for candidate responses
CREATE INDEX idx_responses_session_id ON candidate_responses(session_id);
CREATE INDEX idx_responses_candidate_id ON candidate_responses(candidate_id);
CREATE INDEX idx_responses_question_id ON candidate_responses(question_id);
CREATE INDEX idx_responses_score ON candidate_responses(score);
CREATE INDEX idx_responses_created_at ON candidate_responses(created_at);

-- Create learning outcomes table
CREATE TABLE learning_interview_outcomes (
    outcome_id SERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    candidate_id INTEGER NOT NULL,
    final_theta DECIMAL(5,3) NOT NULL,
    confidence_level DECIMAL(4,3) NOT NULL,
    total_questions INTEGER NOT NULL,
    interview_duration INTEGER NOT NULL,
    candidate_feedback JSONB,
    recruiter_rating DECIMAL(3,2),
    hire_decision BOOLEAN,
    actual_performance DECIMAL(3,2), -- Post-hire performance if available
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for learning outcomes
CREATE INDEX idx_outcomes_session_id ON learning_interview_outcomes(session_id);
CREATE INDEX idx_outcomes_processed_at ON learning_interview_outcomes(processed_at);
CREATE INDEX idx_outcomes_hire_decision ON learning_interview_outcomes(hire_decision);

-- Create question effectiveness tracking table
CREATE TABLE learning_question_effectiveness (
    effectiveness_id SERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    question_id INTEGER NOT NULL,
    information_gain DECIMAL(5,3) NOT NULL,
    discrimination_accuracy DECIMAL(4,3) NOT NULL,
    bias_score DECIMAL(4,3) NOT NULL,
    candidate_satisfaction DECIMAL(3,2) NOT NULL,
    time_efficiency DECIMAL(4,3) NOT NULL,
    skill_coverage DECIMAL(4,3) NOT NULL,
    overall_score DECIMAL(4,3) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for question effectiveness
CREATE INDEX idx_effectiveness_question_id ON learning_question_effectiveness(question_id);
CREATE INDEX idx_effectiveness_overall_score ON learning_question_effectiveness(overall_score);
CREATE INDEX idx_effectiveness_processed_at ON learning_question_effectiveness(processed_at);

-- Insert a default admin user for testing
INSERT INTO users (username, password, email, first_name, last_name, role) VALUES
('admin', '$2a$10$GRLdNijSQMUvl/au9ofL.eDDmxTlIs8w4UyR9jOhXeWBSC8QcTvJu', 'admin@aria.com', 'Admin', 'User', 'ADMIN');

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_candidates_updated_at BEFORE UPDATE ON candidates FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_questions_updated_at BEFORE UPDATE ON questions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_question_irt_parameters_updated_at BEFORE UPDATE ON question_irt_parameters FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_interview_sessions_updated_at BEFORE UPDATE ON interview_sessions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
