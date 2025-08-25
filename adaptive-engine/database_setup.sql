-- Database Setup for ARIA Adaptive Interview Engine
-- Creates all necessary tables for questions, IRT parameters, and candidate responses

-- Use the ARIA interviews database
USE aria_interviews;

-- Drop tables if they exist (for clean setup)
DROP TABLE IF EXISTS candidate_responses;
DROP TABLE IF EXISTS interview_sessions;
DROP TABLE IF EXISTS learning_question_effectiveness;
DROP TABLE IF EXISTS learning_interview_outcomes;
DROP TABLE IF EXISTS question_irt_parameters;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS candidates;

-- Create candidates table
CREATE TABLE candidates (
    candidate_id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    job_role VARCHAR(100) NOT NULL,
    experience_level ENUM('entry', 'junior', 'mid', 'senior', 'staff', 'principal') NOT NULL,
    experience_years INT DEFAULT 0,
    technologies JSON,
    demographics JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_job_role (job_role),
    INDEX idx_experience_level (experience_level)
);

-- Create questions table
CREATE TABLE questions (
    question_id INT AUTO_INCREMENT PRIMARY KEY,
    question_text TEXT NOT NULL,
    question_type ENUM('technical', 'problem_solving', 'coding', 'system_design', 'behavioral', 'conceptual', 'scenario') NOT NULL,
    created_by INT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_question_type (question_type),
    INDEX idx_active (active)
);

-- Create question IRT parameters table
CREATE TABLE question_irt_parameters (
    question_id INT PRIMARY KEY,
    difficulty DECIMAL(5,3) NOT NULL COMMENT 'IRT difficulty parameter (b)',
    discrimination DECIMAL(5,3) NOT NULL DEFAULT 1.000 COMMENT 'IRT discrimination parameter (a)',
    guessing_parameter DECIMAL(4,3) NOT NULL DEFAULT 0.000 COMMENT 'IRT guessing parameter (c)',
    category VARCHAR(100) NOT NULL DEFAULT 'general',
    technologies JSON COMMENT 'Required technologies for this question',
    skill_areas JSON COMMENT 'Skills tested by this question',
    expected_duration_minutes INT DEFAULT 5,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    INDEX idx_difficulty (difficulty),
    INDEX idx_discrimination (discrimination),
    INDEX idx_category (category)
);

-- Create interview sessions table
CREATE TABLE interview_sessions (
    session_id VARCHAR(50) PRIMARY KEY,
    candidate_id INT NOT NULL,
    recruiter_id INT,
    interview_type ENUM('technical', 'behavioral', 'mixed') DEFAULT 'technical',
    status ENUM('scheduled', 'in_progress', 'completed', 'cancelled') DEFAULT 'scheduled',
    current_theta DECIMAL(5,3) DEFAULT 0.000 COMMENT 'Current ability estimate',
    current_se DECIMAL(5,3) DEFAULT 1.000 COMMENT 'Current standard error',
    questions_asked INT DEFAULT 0,
    total_duration_minutes INT DEFAULT 0,
    final_score DECIMAL(4,2),
    confidence_level DECIMAL(4,3),
    bias_flags JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (candidate_id) REFERENCES candidates(candidate_id) ON DELETE CASCADE,
    INDEX idx_status (status),
    INDEX idx_candidate_id (candidate_id),
    INDEX idx_created_at (created_at)
);

-- Create candidate responses table
CREATE TABLE candidate_responses (
    response_id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    candidate_id INT NOT NULL,
    question_id INT NOT NULL,
    response_text TEXT,
    response_data JSON COMMENT 'Contains score, time_taken, code_solution, etc.',
    score DECIMAL(4,2) COMMENT 'Question score (0.00 to 1.00)',
    partial_credit DECIMAL(4,2),
    time_taken_seconds INT,
    theta_before DECIMAL(5,3) COMMENT 'Theta before this response',
    theta_after DECIMAL(5,3) COMMENT 'Theta after this response', 
    theta_change DECIMAL(5,3) COMMENT 'Change in theta from this response',
    information_gain DECIMAL(5,3) COMMENT 'Information gained from this response',
    bias_flags JSON COMMENT 'Any bias detection flags for this response',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (candidate_id) REFERENCES candidates(candidate_id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    INDEX idx_session_id (session_id),
    INDEX idx_candidate_id (candidate_id),
    INDEX idx_question_id (question_id),
    INDEX idx_score (score),
    INDEX idx_created_at (created_at)
);

-- Create learning outcomes table for continuous learning module
CREATE TABLE learning_interview_outcomes (
    outcome_id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    candidate_id INT NOT NULL,
    final_theta DECIMAL(5,3) NOT NULL,
    confidence_level DECIMAL(4,3) NOT NULL,
    total_questions INT NOT NULL,
    interview_duration INT NOT NULL,
    candidate_feedback JSON,
    recruiter_rating DECIMAL(3,2),
    hire_decision BOOLEAN,
    actual_performance DECIMAL(3,2) COMMENT 'Post-hire performance if available',
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_processed_at (processed_at),
    INDEX idx_hire_decision (hire_decision)
);

-- Create question effectiveness tracking table
CREATE TABLE learning_question_effectiveness (
    effectiveness_id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL,
    question_id INT NOT NULL,
    information_gain DECIMAL(5,3) NOT NULL,
    discrimination_accuracy DECIMAL(4,3) NOT NULL,
    bias_score DECIMAL(4,3) NOT NULL,
    candidate_satisfaction DECIMAL(3,2) NOT NULL,
    time_efficiency DECIMAL(4,3) NOT NULL,
    skill_coverage DECIMAL(4,3) NOT NULL,
    overall_score DECIMAL(4,3) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_question_id (question_id),
    INDEX idx_overall_score (overall_score),
    INDEX idx_processed_at (processed_at)
);

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
(10, 1.4, 1.3, 0.0, 'distributed_systems', '["distributed_systems", "consistency"]', '["distributed_systems", "data_consistency"]', 6),

-- Coding Questions (difficulty range: -1.0 to 1.8)
(11, -0.5, 1.6, 0.1, 'algorithms', '["java", "python", "javascript"]', '["algorithms", "problem_solving"]', 8),
(12, 1.0, 1.5, 0.1, 'data_structures', '["java", "python", "javascript"]', '["data_structures", "caching"]', 10),
(13, 0.3, 1.2, 0.1, 'parsing', '["java", "python", "javascript"]', '["string_processing", "parsing"]', 6),
(14, 0.8, 1.4, 0.1, 'algorithms', '["java", "python", "javascript"]', '["algorithms", "search"]', 7),
(15, 0.6, 1.3, 0.1, 'data_structures', '["java", "python", "javascript"]', '["data_structures", "linked_lists"]', 9),

-- System Design Questions (difficulty range: 1.0 to 2.5)
(16, 1.5, 1.6, 0.0, 'system_design', '["distributed_systems", "databases", "caching"]', '["system_design", "scalability"]', 12),
(17, 2.0, 1.5, 0.0, 'real_time_systems', '["websockets", "distributed_systems", "databases"]', '["real_time", "messaging", "scalability"]', 15),
(18, 1.8, 1.4, 0.0, 'notification_systems', '["queues", "databases", "apis"]', '["system_design", "messaging"]', 10),
(19, 2.2, 1.3, 0.0, 'distributed_storage', '["distributed_systems", "file_systems", "databases"]', '["storage", "distributed_systems"]', 18),
(20, 2.1, 1.5, 0.0, 'recommendation_systems', '["machine_learning", "databases", "algorithms"]', '["algorithms", "data_analysis"]', 14),

-- Conceptual Questions (difficulty range: -0.5 to 1.2)
(21, 0.2, 1.1, 0.0, 'database_theory', '["databases", "sql"]', '["database_concepts", "theory"]', 4),
(22, 1.0, 1.2, 0.0, 'distributed_systems_theory', '["distributed_systems"]', '["distributed_systems", "theory"]', 5),
(23, -0.2, 1.0, 0.0, 'security', '["security", "authentication"]', '["security", "access_control"]', 3),
(24, 1.1, 1.1, 0.0, 'distributed_systems_theory', '["distributed_systems", "databases"]', '["consistency", "distributed_systems"]', 4),
(25, 0.4, 1.0, 0.0, 'software_engineering', '["design_patterns", "java", "python"]', '["software_design", "patterns"]', 6);

-- Add more questions for different difficulty levels and technologies
INSERT INTO questions (question_text, question_type) VALUES
-- Advanced Technical Questions
('Explain database sharding strategies and their trade-offs in terms of consistency and performance.', 'technical'),
('What is the difference between optimistic and pessimistic locking? When would you use each?', 'technical'),
('Describe the event sourcing pattern and its advantages over traditional CRUD operations.', 'technical'),
('How does garbage collection work in Java? Compare different GC algorithms.', 'technical'),
('Explain the principles of reactive programming and how it differs from imperative programming.', 'technical'),

-- Advanced Coding Questions
('Implement a thread-safe singleton pattern in Java with proper lazy initialization.', 'coding'),
('Write a function to detect cycles in a directed graph using DFS.', 'coding'),
('Implement a distributed hash table with consistent hashing.', 'coding'),
('Design and implement a basic compiler or interpreter for a simple language.', 'coding'),
('Write a function to find the longest palindromic substring in O(n) time.', 'coding'),

-- Entry Level Questions
('What is the difference between GET and POST HTTP methods?', 'technical'),
('What is a primary key in a database?', 'technical'),
('What is the difference between a stack and a queue?', 'technical'),
('Write a function to reverse a string.', 'coding'),
('What is object-oriented programming? Name its four main principles.', 'conceptual');

-- Add IRT parameters for the additional questions
INSERT INTO question_irt_parameters (question_id, difficulty, discrimination, guessing_parameter, category, technologies, skill_areas, expected_duration_minutes) VALUES
-- Advanced Technical Questions (difficulty range: 1.5 to 2.8)
(26, 2.1, 1.5, 0.0, 'database_advanced', '["databases", "sharding", "distributed_systems"]', '["database_design", "scalability", "sharding"]', 8),
(27, 1.8, 1.4, 0.0, 'concurrency', '["databases", "concurrency"]', '["concurrency", "locking", "performance"]', 6),
(28, 2.5, 1.3, 0.0, 'architecture_patterns', '["event_sourcing", "cqrs"]', '["architecture", "event_sourcing", "patterns"]', 10),
(29, 2.0, 1.6, 0.0, 'jvm', '["java", "jvm"]', '["jvm", "memory_management", "performance"]', 7),
(30, 2.2, 1.4, 0.0, 'reactive_programming', '["reactive", "java", "javascript"]', '["reactive_programming", "async_programming"]', 9),

-- Advanced Coding Questions (difficulty range: 2.0 to 3.0)
(31, 2.3, 1.7, 0.1, 'concurrency', '["java", "multithreading"]', '["concurrency", "design_patterns", "thread_safety"]', 12),
(32, 2.6, 1.6, 0.1, 'graph_algorithms', '["java", "python", "javascript"]', '["algorithms", "graphs", "dfs"]', 15),
(33, 2.8, 1.5, 0.1, 'distributed_systems', '["java", "python", "distributed_systems"]', '["distributed_systems", "hashing", "algorithms"]', 20),
(34, 3.0, 1.4, 0.1, 'compiler_design', '["java", "python", "compilers"]', '["compiler_design", "parsing", "algorithms"]', 25),
(35, 2.4, 1.8, 0.1, 'string_algorithms', '["java", "python", "javascript"]', '["algorithms", "string_processing", "dynamic_programming"]', 18),

-- Entry Level Questions (difficulty range: -2.0 to -0.5)
(36, -1.5, 0.9, 0.0, 'http_basics', '["http", "web"]', '["web_basics", "http"]', 2),
(37, -1.8, 0.8, 0.0, 'database_basics', '["sql", "databases"]', '["database_basics"]', 2),
(38, -1.2, 1.0, 0.0, 'data_structures_basics', '["data_structures"]', '["data_structures_basics"]', 3),
(39, -2.0, 0.7, 0.2, 'basic_programming', '["java", "python", "javascript"]', '["basic_programming"]', 5),
(40, -1.0, 0.9, 0.0, 'oop_basics', '["java", "python", "oop"]', '["oop", "software_engineering_basics"]', 4);

-- Create sample candidates for testing
INSERT INTO candidates (email, first_name, last_name, job_role, experience_level, experience_years, technologies, demographics) VALUES
('john.doe@email.com', 'John', 'Doe', 'Backend Engineer', 'mid', 5, '["java", "spring", "mysql", "redis"]', '{"region": "North America", "education_level": "bachelors", "experience_level": "mid", "primary_language": "english"}'),
('jane.smith@email.com', 'Jane', 'Smith', 'Full Stack Engineer', 'senior', 8, '["javascript", "react", "node.js", "mongodb"]', '{"region": "Europe", "education_level": "masters", "experience_level": "senior", "primary_language": "english"}'),
('bob.wilson@email.com', 'Bob', 'Wilson', 'Backend Engineer', 'junior', 2, '["python", "django", "postgresql"]', '{"region": "North America", "education_level": "bachelors", "experience_level": "junior", "primary_language": "english"}'),
('alice.brown@email.com', 'Alice', 'Brown', 'DevOps Engineer', 'senior', 10, '["aws", "kubernetes", "docker", "terraform"]', '{"region": "Asia", "education_level": "masters", "experience_level": "senior", "primary_language": "english"}'),
('charlie.davis@email.com', 'Charlie', 'Davis', 'Data Engineer', 'mid', 4, '["python", "spark", "kafka", "elasticsearch"]', '{"region": "Europe", "education_level": "bachelors", "experience_level": "mid", "primary_language": "english"}');

-- Create indexes for performance optimization
CREATE INDEX idx_questions_difficulty ON question_irt_parameters (difficulty, discrimination);
CREATE INDEX idx_candidates_role_exp ON candidates (job_role, experience_level);
CREATE INDEX idx_responses_theta ON candidate_responses (theta_after, information_gain);
CREATE INDEX idx_sessions_theta_progress ON interview_sessions (current_theta, questions_asked);

-- Create views for common queries
CREATE VIEW question_stats AS
SELECT 
    q.question_id,
    q.question_text,
    q.question_type,
    qp.difficulty,
    qp.discrimination,
    qp.category,
    COUNT(cr.response_id) as times_asked,
    AVG(cr.score) as avg_score,
    AVG(cr.information_gain) as avg_information_gain,
    STD(cr.score) as score_std
FROM questions q
LEFT JOIN question_irt_parameters qp ON q.question_id = qp.question_id
LEFT JOIN candidate_responses cr ON q.question_id = cr.question_id
WHERE q.active = 1
GROUP BY q.question_id;

CREATE VIEW candidate_performance AS
SELECT 
    c.candidate_id,
    c.email,
    c.job_role,
    c.experience_level,
    COUNT(DISTINCT is_session.session_id) as interviews_taken,
    AVG(is_session.final_score) as avg_final_score,
    AVG(is_session.current_theta) as avg_theta,
    AVG(is_session.confidence_level) as avg_confidence
FROM candidates c
LEFT JOIN interview_sessions is_session ON c.candidate_id = is_session.candidate_id
WHERE is_session.status = 'completed'
GROUP BY c.candidate_id;

-- Add comments to important columns
ALTER TABLE question_irt_parameters 
    MODIFY COLUMN difficulty DECIMAL(5,3) NOT NULL COMMENT 'IRT difficulty parameter (b): negative = easier, positive = harder',
    MODIFY COLUMN discrimination DECIMAL(5,3) NOT NULL COMMENT 'IRT discrimination parameter (a): higher values = better discrimination',
    MODIFY COLUMN guessing_parameter DECIMAL(4,3) NOT NULL COMMENT 'IRT guessing parameter (c): probability of correct guess';

-- Create stored procedures for common operations
DELIMITER //

CREATE PROCEDURE GetAdaptiveQuestions(
    IN p_difficulty_min DECIMAL(5,3),
    IN p_difficulty_max DECIMAL(5,3),
    IN p_technologies JSON,
    IN p_excluded_questions TEXT,
    IN p_limit INT
)
BEGIN
    SET @sql = CONCAT('
        SELECT q.question_id, q.question_text, q.question_type,
               qp.difficulty, qp.discrimination, qp.guessing_parameter,
               qp.category, qp.technologies, qp.expected_duration_minutes
        FROM questions q
        JOIN question_irt_parameters qp ON q.question_id = qp.question_id
        WHERE q.active = 1
          AND qp.difficulty BETWEEN ', p_difficulty_min, ' AND ', p_difficulty_max);
    
    IF p_excluded_questions IS NOT NULL AND LENGTH(p_excluded_questions) > 0 THEN
        SET @sql = CONCAT(@sql, ' AND q.question_id NOT IN (', p_excluded_questions, ')');
    END IF;
    
    SET @sql = CONCAT(@sql, ' ORDER BY qp.difficulty LIMIT ', p_limit);
    
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END //

DELIMITER ;

-- Insert some sample interview sessions for testing
INSERT INTO interview_sessions (session_id, candidate_id, status, current_theta, current_se, questions_asked) VALUES
('SESSION_TEST_001', 1, 'in_progress', 0.5, 0.8, 3),
('SESSION_TEST_002', 2, 'completed', 1.2, 0.4, 12),
('SESSION_TEST_003', 3, 'in_progress', -0.3, 0.9, 2);

-- Insert some sample responses for testing the learning system
INSERT INTO candidate_responses (session_id, candidate_id, question_id, response_data, score, time_taken_seconds, theta_before, theta_after, theta_change, information_gain) VALUES
('SESSION_TEST_001', 1, 1, '{"correct": true, "quality_score": 0.8}', 0.80, 120, 0.0, 0.3, 0.3, 0.45),
('SESSION_TEST_001', 1, 3, '{"correct": true, "quality_score": 0.9}', 0.90, 180, 0.3, 0.5, 0.2, 0.38),
('SESSION_TEST_001', 1, 5, '{"correct": false, "quality_score": 0.4}', 0.40, 240, 0.5, 0.4, -0.1, 0.22),
('SESSION_TEST_002', 2, 16, '{"correct": true, "quality_score": 0.95}', 0.95, 720, 0.8, 1.1, 0.3, 0.52),
('SESSION_TEST_002', 2, 18, '{"correct": true, "quality_score": 0.85}', 0.85, 600, 1.1, 1.2, 0.1, 0.28);

-- Show summary of the database setup
SELECT 'Database setup completed successfully!' as status;
SELECT COUNT(*) as total_questions FROM questions;
SELECT COUNT(*) as total_irt_parameters FROM question_irt_parameters;
SELECT COUNT(*) as total_candidates FROM candidates;
SELECT COUNT(*) as total_sessions FROM interview_sessions;
SELECT COUNT(*) as total_responses FROM candidate_responses;
