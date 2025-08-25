package com.company.interview.service;

import com.company.interview.client.AdaptiveEngineClient;
import com.company.interview.model.InterviewSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptive Interview Service that implements the complete 6-stage interview flow:
 * 
 * Stage 1: Initial Assessment & Calibration
 * Stage 2: Adaptive Questioning (Easy → Medium)
 * Stage 3: Deep Skill Exploration
 * Stage 4: Challenge Questions (Hard)
 * Stage 5: Behavioral Integration 
 * Stage 6: Final Assessment & Wrap-up
 * 
 * Uses Item Response Theory (IRT) for optimal question selection and ability estimation.
 */
@Service
public class AdaptiveInterviewService {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveInterviewService.class);

    @Autowired
    private AdaptiveEngineClient adaptiveEngineClient;

    @Value("${app.interview.irt.initial-theta:0.0}")
    private double initialTheta;

    @Value("${app.interview.irt.initial-standard-error:1.0}")
    private double initialStandardError;

    @Value("${app.interview.irt.confidence-threshold:0.3}")
    private double confidenceThreshold;

    @Value("${app.interview.irt.min-questions:10}")
    private int minQuestions;

    @Value("${app.interview.irt.max-questions:30}")
    private int maxQuestions;

    // Session state management
    private final Map<String, AdaptiveInterviewState> sessionStates = new ConcurrentHashMap<>();

    /**
     * Stage definitions for the 6-stage adaptive interview flow
     */
    public enum InterviewStage {
        CALIBRATION("Initial Assessment & Calibration", 1, 3, -1.0, 1.0),
        ADAPTIVE_EXPLORATION("Adaptive Questioning", 4, 8, -2.0, 2.0),
        SKILL_DEEP_DIVE("Deep Skill Exploration", 9, 12, -1.5, 2.5),
        CHALLENGE_QUESTIONS("Challenge Questions", 13, 16, 0.5, 3.0),
        BEHAVIORAL_INTEGRATION("Behavioral Integration", 17, 19, -1.0, 1.5),
        FINAL_ASSESSMENT("Final Assessment", 20, 25, -2.0, 3.0);

        private final String description;
        private final int minQuestionNumber;
        private final int maxQuestionNumber;
        private final double minDifficulty;
        private final double maxDifficulty;

        InterviewStage(String description, int minQuestionNumber, int maxQuestionNumber, 
                      double minDifficulty, double maxDifficulty) {
            this.description = description;
            this.minQuestionNumber = minQuestionNumber;
            this.maxQuestionNumber = maxQuestionNumber;
            this.minDifficulty = minDifficulty;
            this.maxDifficulty = maxDifficulty;
        }

        public String getDescription() { return description; }
        public int getMinQuestionNumber() { return minQuestionNumber; }
        public int getMaxQuestionNumber() { return maxQuestionNumber; }
        public double getMinDifficulty() { return minDifficulty; }
        public double getMaxDifficulty() { return maxDifficulty; }
    }

    /**
     * State object to track interview progress
     */
    public static class AdaptiveInterviewState {
        private String sessionId;
        private Integer candidateId;
        private String jobRole;
        private String experienceLevel;
        private List<String> technologies;
        
        private InterviewStage currentStage = InterviewStage.CALIBRATION;
        private int questionsAsked = 0;
        private double currentTheta = 0.0;
        private double currentStandardError = 1.0;
        private List<Integer> answeredQuestions = new ArrayList<>();
        
        private LocalDateTime interviewStartTime;
        private Duration totalDuration = Duration.ZERO;
        
        private Map<String, Object> stageProgress = new HashMap<>();
        private List<Map<String, Object>> questionHistory = new ArrayList<>();
        private Map<String, Double> skillAssessment = new HashMap<>();
        
        private boolean terminationRecommended = false;
        private String terminationReason;
        
        // Constructors
        public AdaptiveInterviewState(String sessionId, Integer candidateId, String jobRole, 
                                    String experienceLevel, List<String> technologies) {
            this.sessionId = sessionId;
            this.candidateId = candidateId;
            this.jobRole = jobRole;
            this.experienceLevel = experienceLevel;
            this.technologies = technologies != null ? new ArrayList<>(technologies) : new ArrayList<>();
            this.interviewStartTime = LocalDateTime.now();
            this.initializeStageProgress();
        }

        private void initializeStageProgress() {
            for (InterviewStage stage : InterviewStage.values()) {
                Map<String, Object> progress = new HashMap<>();
                progress.put("completed", false);
                progress.put("questionsAsked", 0);
                progress.put("startTime", null);
                progress.put("endTime", null);
                stageProgress.put(stage.name(), progress);
            }
        }

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public Integer getCandidateId() { return candidateId; }
        public String getJobRole() { return jobRole; }
        public String getExperienceLevel() { return experienceLevel; }
        public List<String> getTechnologies() { return technologies; }
        
        public InterviewStage getCurrentStage() { return currentStage; }
        public void setCurrentStage(InterviewStage currentStage) { this.currentStage = currentStage; }
        
        public int getQuestionsAsked() { return questionsAsked; }
        public void incrementQuestionsAsked() { this.questionsAsked++; }
        
        public double getCurrentTheta() { return currentTheta; }
        public void setCurrentTheta(double currentTheta) { this.currentTheta = currentTheta; }
        
        public double getCurrentStandardError() { return currentStandardError; }
        public void setCurrentStandardError(double currentStandardError) { 
            this.currentStandardError = currentStandardError; 
        }
        
        public List<Integer> getAnsweredQuestions() { return answeredQuestions; }
        public void addAnsweredQuestion(Integer questionId) { this.answeredQuestions.add(questionId); }
        
        public LocalDateTime getInterviewStartTime() { return interviewStartTime; }
        public Duration getTotalDuration() { return totalDuration; }
        public void updateTotalDuration() { 
            this.totalDuration = Duration.between(interviewStartTime, LocalDateTime.now()); 
        }
        
        public Map<String, Object> getStageProgress() { return stageProgress; }
        public List<Map<String, Object>> getQuestionHistory() { return questionHistory; }
        public Map<String, Double> getSkillAssessment() { return skillAssessment; }
        
        public boolean isTerminationRecommended() { return terminationRecommended; }
        public void setTerminationRecommended(boolean terminationRecommended) { 
            this.terminationRecommended = terminationRecommended; 
        }
        
        public String getTerminationReason() { return terminationReason; }
        public void setTerminationReason(String terminationReason) { 
            this.terminationReason = terminationReason; 
        }

        public void addQuestionToHistory(Map<String, Object> questionData) {
            questionHistory.add(questionData);
        }

        public void updateSkillAssessment(String skill, double score) {
            skillAssessment.put(skill, score);
        }

        @SuppressWarnings("unchecked")
        public void updateStageProgress(InterviewStage stage, String key, Object value) {
            Map<String, Object> progress = (Map<String, Object>) stageProgress.get(stage.name());
            if (progress != null) {
                progress.put(key, value);
            }
        }
    }

    /**
     * Initialize adaptive interview session
     */
    public AdaptiveInterviewState initializeInterview(String sessionId, Integer candidateId, 
                                                    String jobRole, String experienceLevel, 
                                                    List<String> technologies) {
        logger.info("Initializing adaptive interview for session: {}, candidate: {}, role: {}", 
                sessionId, candidateId, jobRole);

        AdaptiveInterviewState state = new AdaptiveInterviewState(
                sessionId, candidateId, jobRole, experienceLevel, technologies);
        
        state.setCurrentTheta(initialTheta);
        state.setCurrentStandardError(initialStandardError);
        
        // Mark calibration stage as started
        state.updateStageProgress(InterviewStage.CALIBRATION, "startTime", LocalDateTime.now());

        sessionStates.put(sessionId, state);

        logger.info("Adaptive interview initialized for session: {} in {} stage", 
                sessionId, state.getCurrentStage().getDescription());
        
        return state;
    }

    /**
     * Get the next question based on current interview state
     */
    public Optional<AdaptiveEngineClient.QuestionResponse> getNextQuestion(String sessionId) {
        AdaptiveInterviewState state = sessionStates.get(sessionId);
        if (state == null) {
            logger.error("No interview state found for session: {}", sessionId);
            return Optional.empty();
        }

        // Check if interview should terminate
        if (shouldTerminateInterview(state)) {
            logger.info("Interview termination recommended for session: {} - {}", 
                    sessionId, state.getTerminationReason());
            return Optional.empty();
        }

        // Update stage if needed
        updateInterviewStage(state);

        // Create question request based on current stage
        AdaptiveEngineClient.QuestionRequest request = createQuestionRequest(state);

        try {
            Optional<AdaptiveEngineClient.QuestionResponse> response = 
                    adaptiveEngineClient.getNextQuestion(request);

            if (response.isPresent()) {
                AdaptiveEngineClient.QuestionResponse question = response.get();
                
                // Track question in state
                Map<String, Object> questionData = new HashMap<>();
                questionData.put("questionId", question.getQuestionId());
                questionData.put("stage", state.getCurrentStage().name());
                questionData.put("difficulty", question.getDifficulty());
                questionData.put("questionType", question.getQuestionType());
                questionData.put("askedAt", LocalDateTime.now());
                questionData.put("theta", state.getCurrentTheta());
                questionData.put("standardError", state.getCurrentStandardError());
                
                state.addQuestionToHistory(questionData);
                state.addAnsweredQuestion(question.getQuestionId());
                state.incrementQuestionsAsked();

                logger.info("Selected question {} for session {} in stage {} (difficulty: {})", 
                        question.getQuestionId(), sessionId, 
                        state.getCurrentStage().getDescription(), question.getDifficulty());

                return response;
            } else {
                logger.warn("No suitable question found for session: {} in stage: {}", 
                        sessionId, state.getCurrentStage());
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.error("Error getting next question for session {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Process candidate response and update theta
     */
    public Optional<AdaptiveEngineClient.ThetaUpdateResponse> processResponse(
            String sessionId, Integer questionId, Map<String, Object> responseData, 
            Double partialCredit, Integer responseTimeSeconds) {
        
        AdaptiveInterviewState state = sessionStates.get(sessionId);
        if (state == null) {
            logger.error("No interview state found for session: {}", sessionId);
            return Optional.empty();
        }

        AdaptiveEngineClient.ThetaUpdateRequest request = new AdaptiveEngineClient.ThetaUpdateRequest(
                sessionId, state.getCandidateId(), questionId, responseData, 
                state.getCurrentTheta(), state.getCurrentStandardError());
        
        request.setPartialCredit(partialCredit);
        request.setResponseTimeSeconds(responseTimeSeconds);

        try {
            Optional<AdaptiveEngineClient.ThetaUpdateResponse> response = 
                    adaptiveEngineClient.updateTheta(request);

            if (response.isPresent()) {
                AdaptiveEngineClient.ThetaUpdateResponse thetaUpdate = response.get();
                
                // Update state with new theta values
                state.setCurrentTheta(thetaUpdate.getNewTheta());
                state.setCurrentStandardError(thetaUpdate.getNewStandardError());

                // Update question history with response
                if (!state.getQuestionHistory().isEmpty()) {
                    Map<String, Object> lastQuestion = state.getQuestionHistory()
                            .get(state.getQuestionHistory().size() - 1);
                    lastQuestion.put("responseScore", responseData.get("score"));
                    lastQuestion.put("responseTime", responseTimeSeconds);
                    lastQuestion.put("thetaChange", thetaUpdate.getThetaChange());
                    lastQuestion.put("newTheta", thetaUpdate.getNewTheta());
                    lastQuestion.put("newStandardError", thetaUpdate.getNewStandardError());
                }

                // Check for bias flags
                if (Boolean.TRUE.equals(thetaUpdate.getBiasFlag())) {
                    logger.warn("Bias detected for session {} on question {}: {}", 
                            sessionId, questionId, thetaUpdate.getBiasDetails());
                }

                // Update termination recommendation
                if (Boolean.TRUE.equals(thetaUpdate.getTerminationRecommended())) {
                    state.setTerminationRecommended(true);
                    state.setTerminationReason("AI recommended based on confidence level");
                }

                // Update skill assessments based on question category
                updateSkillAssessments(state, questionId, responseData);

                logger.info("Updated theta for session {} from {} to {} (change: {})", 
                        sessionId, request.getCurrentTheta(), thetaUpdate.getNewTheta(), 
                        thetaUpdate.getThetaChange());

                return response;
            }

        } catch (Exception e) {
            logger.error("Error processing response for session {}: {}", sessionId, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Get comprehensive interview analytics
     */
    public Map<String, Object> getInterviewAnalytics(String sessionId) {
        AdaptiveInterviewState state = sessionStates.get(sessionId);
        if (state == null) {
            return Collections.emptyMap();
        }

        state.updateTotalDuration();

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("sessionId", sessionId);
        analytics.put("candidateId", state.getCandidateId());
        analytics.put("currentStage", state.getCurrentStage().getDescription());
        analytics.put("questionsAsked", state.getQuestionsAsked());
        analytics.put("currentTheta", state.getCurrentTheta());
        analytics.put("currentStandardError", state.getCurrentStandardError());
        analytics.put("confidenceLevel", 1.0 - state.getCurrentStandardError());
        analytics.put("interviewDuration", state.getTotalDuration().toMinutes());
        analytics.put("stageProgress", state.getStageProgress());
        analytics.put("skillAssessment", state.getSkillAssessment());
        analytics.put("terminationRecommended", state.isTerminationRecommended());
        analytics.put("terminationReason", state.getTerminationReason());
        
        // Calculate stage-wise performance
        Map<String, Object> stagePerformance = calculateStagePerformance(state);
        analytics.put("stagePerformance", stagePerformance);
        
        // Get adaptive engine analytics
        Optional<Map<String, Object>> engineAnalytics = 
                adaptiveEngineClient.getSessionAnalytics(sessionId);
        if (engineAnalytics.isPresent()) {
            analytics.put("adaptiveEngineAnalytics", engineAnalytics.get());
        }

        return analytics;
    }

    /**
     * Complete interview and submit learning data
     */
    public Map<String, Object> completeInterview(String sessionId, 
                                                Map<String, Object> candidateFeedback,
                                                Double recruiterRating,
                                                Boolean hireDecision) {
        AdaptiveInterviewState state = sessionStates.get(sessionId);
        if (state == null) {
            logger.error("No interview state found for session: {}", sessionId);
            return Collections.emptyMap();
        }

        state.updateTotalDuration();

        // Generate final analytics
        Map<String, Object> finalAnalytics = getInterviewAnalytics(sessionId);

        // Prepare learning data for adaptive engine
        Map<String, Object> interviewOutcome = new HashMap<>();
        interviewOutcome.put("candidate_id", state.getCandidateId());
        interviewOutcome.put("final_theta", state.getCurrentTheta());
        interviewOutcome.put("confidence_level", 1.0 - state.getCurrentStandardError());
        interviewOutcome.put("total_questions", state.getQuestionsAsked());
        interviewOutcome.put("interview_duration", state.getTotalDuration().toMinutes());
        interviewOutcome.put("candidate_feedback", candidateFeedback);
        interviewOutcome.put("recruiter_rating", recruiterRating);
        interviewOutcome.put("hire_decision", hireDecision);

        // Calculate question effectiveness
        List<Map<String, Object>> questionEffectiveness = calculateQuestionEffectiveness(state);

        // Submit learning data to adaptive engine
        AdaptiveEngineClient.LearningUpdateRequest learningRequest = 
                new AdaptiveEngineClient.LearningUpdateRequest(sessionId, interviewOutcome);
        learningRequest.setQuestionEffectiveness(questionEffectiveness);
        
        adaptiveEngineClient.submitLearningData(learningRequest);

        // Clean up session state
        sessionStates.remove(sessionId);

        logger.info("Completed adaptive interview for session: {} with final theta: {}, " +
                "confidence: {}, questions: {}", 
                sessionId, state.getCurrentTheta(), 
                1.0 - state.getCurrentStandardError(), state.getQuestionsAsked());

        return finalAnalytics;
    }

    /**
     * Get current interview state
     */
    public Optional<AdaptiveInterviewState> getInterviewState(String sessionId) {
        return Optional.ofNullable(sessionStates.get(sessionId));
    }

    // Private helper methods

    private boolean shouldTerminateInterview(AdaptiveInterviewState state) {
        // Check AI recommendation
        if (state.isTerminationRecommended()) {
            return true;
        }

        // Check maximum questions limit
        if (state.getQuestionsAsked() >= maxQuestions) {
            state.setTerminationRecommended(true);
            state.setTerminationReason("Maximum questions reached");
            return true;
        }

        // Check minimum questions and confidence threshold
        if (state.getQuestionsAsked() >= minQuestions && 
            state.getCurrentStandardError() <= confidenceThreshold) {
            state.setTerminationRecommended(true);
            state.setTerminationReason("Sufficient confidence achieved");
            return true;
        }

        // Check time limit (45 minutes default)
        state.updateTotalDuration();
        if (state.getTotalDuration().toMinutes() >= 45) {
            state.setTerminationRecommended(true);
            state.setTerminationReason("Time limit reached");
            return true;
        }

        return false;
    }

    private void updateInterviewStage(AdaptiveInterviewState state) {
        int questionsAsked = state.getQuestionsAsked();
        InterviewStage currentStage = state.getCurrentStage();

        for (InterviewStage stage : InterviewStage.values()) {
            if (questionsAsked >= stage.getMinQuestionNumber() && 
                questionsAsked <= stage.getMaxQuestionNumber() &&
                !stage.equals(currentStage)) {
                
                // Complete previous stage
                state.updateStageProgress(currentStage, "completed", true);
                state.updateStageProgress(currentStage, "endTime", LocalDateTime.now());

                // Start new stage
                state.setCurrentStage(stage);
                state.updateStageProgress(stage, "startTime", LocalDateTime.now());

                logger.info("Advanced to {} stage for session: {}", 
                        stage.getDescription(), state.getSessionId());
                break;
            }
        }
    }

    private AdaptiveEngineClient.QuestionRequest createQuestionRequest(AdaptiveInterviewState state) {
        InterviewStage stage = state.getCurrentStage();
        
        AdaptiveEngineClient.QuestionRequest request = new AdaptiveEngineClient.QuestionRequest(
                state.getSessionId(), state.getCandidateId(), 
                state.getJobRole(), state.getExperienceLevel());
        
        request.setCurrentTheta(state.getCurrentTheta());
        request.setStandardError(state.getCurrentStandardError());
        request.setAnsweredQuestions(state.getAnsweredQuestions());
        request.setTechnologies(state.getTechnologies());
        request.setMinDifficulty(stage.getMinDifficulty());
        request.setMaxDifficulty(stage.getMaxDifficulty());

        // Set question type based on stage
        switch (stage) {
            case CALIBRATION:
                request.setQuestionType("technical");
                break;
            case ADAPTIVE_EXPLORATION:
                // No specific type - let adaptive engine choose
                break;
            case SKILL_DEEP_DIVE:
                request.setQuestionType("coding");
                break;
            case CHALLENGE_QUESTIONS:
                request.setQuestionType("system_design");
                break;
            case BEHAVIORAL_INTEGRATION:
                request.setQuestionType("behavioral");
                break;
            case FINAL_ASSESSMENT:
                request.setQuestionType("problem_solving");
                break;
        }

        return request;
    }

    private void updateSkillAssessments(AdaptiveInterviewState state, Integer questionId, 
                                      Map<String, Object> responseData) {
        // This would typically look up question metadata to determine skills tested
        // For now, we'll use a simplified approach based on response score
        Double score = (Double) responseData.get("score");
        if (score != null) {
            // Update general skill assessment based on current theta
            String skill = "general_ability";
            state.updateSkillAssessment(skill, state.getCurrentTheta());
            
            // Update technology-specific assessments
            for (String tech : state.getTechnologies()) {
                state.updateSkillAssessment(tech, state.getCurrentTheta() * 0.9 + score * 0.1);
            }
        }
    }

    private Map<String, Object> calculateStagePerformance(AdaptiveInterviewState state) {
        Map<String, Object> stagePerformance = new HashMap<>();
        
        for (InterviewStage stage : InterviewStage.values()) {
            Map<String, Object> performance = new HashMap<>();
            
            // Filter questions for this stage
            List<Map<String, Object>> stageQuestions = state.getQuestionHistory().stream()
                    .filter(q -> stage.name().equals(q.get("stage")))
                    .toList();
            
            if (!stageQuestions.isEmpty()) {
                double avgScore = stageQuestions.stream()
                        .filter(q -> q.containsKey("responseScore"))
                        .mapToDouble(q -> ((Number) q.get("responseScore")).doubleValue())
                        .average().orElse(0.0);
                
                double avgDifficulty = stageQuestions.stream()
                        .filter(q -> q.containsKey("difficulty"))
                        .mapToDouble(q -> ((Number) q.get("difficulty")).doubleValue())
                        .average().orElse(0.0);
                
                performance.put("questionsAsked", stageQuestions.size());
                performance.put("averageScore", avgScore);
                performance.put("averageDifficulty", avgDifficulty);
                performance.put("completed", state.getStageProgress().get(stage.name()));
            }
            
            stagePerformance.put(stage.name(), performance);
        }
        
        return stagePerformance;
    }

    private List<Map<String, Object>> calculateQuestionEffectiveness(AdaptiveInterviewState state) {
        List<Map<String, Object>> effectiveness = new ArrayList<>();
        
        for (Map<String, Object> question : state.getQuestionHistory()) {
            if (question.containsKey("responseScore") && question.containsKey("thetaChange")) {
                Map<String, Object> metric = new HashMap<>();
                metric.put("question_id", question.get("questionId"));
                
                Double thetaChange = (Double) question.get("thetaChange");
                metric.put("information_gain", Math.abs(thetaChange != null ? thetaChange : 0.0));
                
                Double responseScore = (Double) question.get("responseScore");
                metric.put("discrimination_accuracy", responseScore != null ? responseScore : 0.5);
                
                metric.put("bias_score", 0.1); // Default low bias
                metric.put("candidate_satisfaction", 0.7); // Default satisfaction
                metric.put("time_efficiency", 0.8); // Default time efficiency
                metric.put("skill_coverage", 0.6); // Default skill coverage
                
                effectiveness.add(metric);
            }
        }
        
        return effectiveness;
    }
}
