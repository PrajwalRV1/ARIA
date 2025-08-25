package com.company.interview.service;

import com.company.interview.client.UserManagementClient;
import com.company.interview.client.AdaptiveEngineClient;
import com.company.interview.client.JobAnalysisClient;
import com.company.interview.dto.InterviewScheduleRequest;
import com.company.interview.dto.InterviewSessionResponse;
import com.company.interview.model.InterviewSession;
import com.company.interview.websocket.OrchestratorWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service for orchestrating the complete interview flow
 * Handles: session initialization → WebRTC setup → adaptive questioning → 
 * response processing → analytics generation → session completion
 */
@Service
public class InterviewOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(InterviewOrchestrationService.class);

    private final InterviewSessionStateService sessionStateService;
    private final UserManagementClient userManagementClient;
    private final AdaptiveEngineClient adaptiveEngineClient;
    private final JobAnalysisClient jobAnalysisClient;
    private final AdaptiveInterviewService adaptiveInterviewService;
    private final RestTemplate adaptiveEngineRestTemplate;
    private final RestTemplate speechServiceRestTemplate;
    private final RestTemplate aiAnalyticsRestTemplate;
    private final OrchestratorWebSocketHandler orchestratorWebSocketHandler;
    
    // Active interview sessions tracking
    private final Map<String, InterviewExecutionContext> activeInterviews = new ConcurrentHashMap<>();
    
    // Job analysis caching
    private final Map<String, JobAnalysisClient.JobAnalysisResponse> jobAnalysisCache = new ConcurrentHashMap<>();

    public InterviewOrchestrationService(
            InterviewSessionStateService sessionStateService,
            UserManagementClient userManagementClient,
            AdaptiveEngineClient adaptiveEngineClient,
            JobAnalysisClient jobAnalysisClient,
            AdaptiveInterviewService adaptiveInterviewService,
            @Qualifier("questionEngineRestTemplate") RestTemplate adaptiveEngineRestTemplate,
            @Qualifier("transcriptServiceRestTemplate") RestTemplate speechServiceRestTemplate,
            @Qualifier("aiAnalyticsRestTemplate") RestTemplate aiAnalyticsRestTemplate,
            OrchestratorWebSocketHandler orchestratorWebSocketHandler) {
        this.sessionStateService = sessionStateService;
        this.userManagementClient = userManagementClient;
        this.adaptiveEngineClient = adaptiveEngineClient;
        this.jobAnalysisClient = jobAnalysisClient;
        this.adaptiveInterviewService = adaptiveInterviewService;
        this.adaptiveEngineRestTemplate = adaptiveEngineRestTemplate;
        this.speechServiceRestTemplate = speechServiceRestTemplate;
        this.aiAnalyticsRestTemplate = aiAnalyticsRestTemplate;
        this.orchestratorWebSocketHandler = orchestratorWebSocketHandler;
    }

    /**
     * Initialize a new interview session
     */
    public InterviewSessionResult initializeInterview(InitializeInterviewRequest request) {
        try {
            logger.info("Initializing interview session for candidate: {}, interviewer: {}", 
                request.getCandidateId(), request.getInterviewerId());

            // Step 1: Validate users
            if (!validateUsers(request.getCandidateId(), request.getInterviewerId())) {
                return InterviewSessionResult.failure("Invalid candidate or interviewer");
            }

            // Step 2: Generate session ID and create session state
            String sessionId = generateSessionId();
            InterviewSessionStateService.InterviewSessionState sessionState = createSessionState(sessionId, request);

            // Step 3: Initialize session state in Redis
            if (!sessionStateService.initializeInterviewSession(sessionId, sessionState)) {
                return InterviewSessionResult.failure("Failed to initialize session state");
            }

            // Step 4: Create execution context
            InterviewExecutionContext context = new InterviewExecutionContext(sessionId, sessionState);
            context.setInitializedAt(Instant.now());
            activeInterviews.put(sessionId, context);

            // Step 5: Initialize AI services asynchronously
            CompletableFuture.runAsync(() -> initializeAIServices(sessionId, request));

            logger.info("Successfully initialized interview session: {}", sessionId);
            return InterviewSessionResult.success(sessionId, sessionState);

        } catch (Exception e) {
            logger.error("Failed to initialize interview session: {}", e.getMessage());
            return InterviewSessionResult.failure("Failed to initialize interview session");
        }
    }

    /**
     * Start the interview session (internal method)
     */
    public InterviewSessionResult startInterviewInternal(String sessionId) {
        try {
            logger.info("Starting interview session: {}", sessionId);

            InterviewExecutionContext context = activeInterviews.get(sessionId);
            if (context == null) {
                return InterviewSessionResult.failure("Session not found or not active");
            }

            // Step 1: Update session status
            if (!sessionStateService.updateSessionStatus(sessionId, InterviewSessionStateService.SessionStatus.IN_PROGRESS)) {
                return InterviewSessionResult.failure("Failed to update session status");
            }

            // Step 2: Generate first question
            QuestionGenerationResult firstQuestion = generateNextQuestion(sessionId, context);
            if (!firstQuestion.isSuccess()) {
                return InterviewSessionResult.failure("Failed to generate first question");
            }

            // Step 3: Store question and update context
            sessionStateService.storeCurrentQuestion(sessionId, firstQuestion.getQuestionData());
            context.setCurrentQuestion(firstQuestion.getQuestionData());
            context.setStartedAt(Instant.now());

            // Step 4: Initialize WebRTC and speech services
            CompletableFuture.runAsync(() -> setupRealtimeServices(sessionId));

            logger.info("Successfully started interview session: {}", sessionId);
            return InterviewSessionResult.success(sessionId, context.getSessionState());

        } catch (Exception e) {
            logger.error("Failed to start interview session {}: {}", sessionId, e.getMessage());
            return InterviewSessionResult.failure("Failed to start interview session");
        }
    }

    /**
     * Process candidate response and generate next question (OPTIMIZED for 2-3 second transitions)
     */
    public InterviewSessionResult processResponse(String sessionId, ProcessResponseRequest request) {
        try {
            long startTime = System.currentTimeMillis();
            logger.debug("🚀 Processing response for session: {} (targeting <2s response)", sessionId);

            InterviewExecutionContext context = activeInterviews.get(sessionId);
            if (context == null) {
                return InterviewSessionResult.failure("Session not found or not active");
            }

            // Step 1: Store the response (NON-BLOCKING)
            InterviewSessionStateService.ResponseData responseData = createResponseData(request);
            CompletableFuture.runAsync(() -> sessionStateService.storeResponse(sessionId, responseData));

            // Step 2: Analyze response asynchronously (NON-BLOCKING)
            CompletableFuture.supplyAsync(() -> analyzeResponse(sessionId, responseData, context))
                .thenAccept(analysisResult -> {
                    // Process analysis results asynchronously
                    if (analysisResult.isSuccess()) {
                        logger.debug("✅ Analysis completed for session: {}", sessionId);
                        // Update scoring in background
                        updateCandidatePerformanceAsync(sessionId, analysisResult.getAnalysisData());
                    }
                })
                .exceptionally(throwable -> {
                    logger.warn("⚠️ Analysis failed for session {}: {}", sessionId, throwable.getMessage());
                    return null;
                });

            // Step 3: Update IRT parameters (LIGHTWEIGHT)
            updateIRTParametersOptimized(context, responseData);

            // Step 4: Check if interview should continue (IMMEDIATE)
            if (shouldContinueInterview(context)) {
                // 🚀 IMMEDIATE: Generate next question (< 1 second)
                QuestionGenerationResult nextQuestion = generateNextQuestionOptimized(sessionId, context);
                if (nextQuestion.isSuccess()) {
                    // Store question asynchronously
                    CompletableFuture.runAsync(() -> 
                        sessionStateService.storeCurrentQuestion(sessionId, nextQuestion.getQuestionData()));
                    
                    context.setCurrentQuestion(nextQuestion.getQuestionData());
                    context.incrementQuestionIndex();
                    
                    long processingTime = System.currentTimeMillis() - startTime;
                    logger.info("✅ Response processed in {}ms for session: {} (Target: <2000ms)", 
                               processingTime, sessionId);
                    
                    // 🚀 IMMEDIATE: Broadcast next question to AI Avatar (< 500ms)
                    broadcastNextQuestionImmediate(sessionId, nextQuestion.getQuestionData());
                    
                    return InterviewSessionResult.success(sessionId, context.getSessionState());
                }
            } else {
                // Complete the interview
                return completeInterview(sessionId, context);
            }

            return InterviewSessionResult.failure("Failed to process response");

        } catch (Exception e) {
            logger.error("❌ Failed to process response for session {}: {}", sessionId, e.getMessage());
            return InterviewSessionResult.failure("Failed to process response");
        }
    }

    /**
     * Complete the interview session
     */
    public InterviewSessionResult completeInterview(String sessionId, InterviewExecutionContext context) {
        try {
            logger.info("Completing interview session: {}", sessionId);

            // Step 1: Update session status
            sessionStateService.updateSessionStatus(sessionId, InterviewSessionStateService.SessionStatus.COMPLETED);

            // Step 2: Generate final analytics and report
            CompletableFuture<FinalAnalysisResult> finalAnalysis = CompletableFuture.supplyAsync(() -> 
                generateFinalAnalysis(sessionId, context));

            // Step 3: Clean up real-time services
            CompletableFuture.runAsync(() -> cleanupRealtimeServices(sessionId));

            // Step 4: Update user statistics
            CompletableFuture.runAsync(() -> updateUserStatistics(context.getSessionState()));

            // Step 5: Remove from active sessions
            activeInterviews.remove(sessionId);
            context.setCompletedAt(Instant.now());

            logger.info("Successfully completed interview session: {}", sessionId);
            return InterviewSessionResult.success(sessionId, context.getSessionState());

        } catch (Exception e) {
            logger.error("Failed to complete interview session {}: {}", sessionId, e.getMessage());
            return InterviewSessionResult.failure("Failed to complete interview session");
        }
    }

    /**
     * Terminate interview session (emergency stop)
     */
    public InterviewSessionResult terminateInterview(String sessionId, String reason) {
        try {
            logger.warn("Terminating interview session {}: {}", sessionId, reason);

            InterviewExecutionContext context = activeInterviews.get(sessionId);
            if (context != null) {
                // Update session status
                sessionStateService.updateSessionStatus(sessionId, InterviewSessionStateService.SessionStatus.TERMINATED);
                
                // Clean up services
                CompletableFuture.runAsync(() -> cleanupRealtimeServices(sessionId));
                
                // Remove from active sessions
                activeInterviews.remove(sessionId);
                context.setTerminatedAt(Instant.now());
                context.setTerminationReason(reason);
            }

            return InterviewSessionResult.success(sessionId, context != null ? context.getSessionState() : null);

        } catch (Exception e) {
            logger.error("Failed to terminate interview session {}: {}", sessionId, e.getMessage());
            return InterviewSessionResult.failure("Failed to terminate interview session");
        }
    }

    // Helper methods

    private boolean validateUsers(Long candidateId, Long interviewerId) {
        try {
            // Validate candidate
            Optional<UserManagementClient.UserDetails> candidate = userManagementClient.getUserById(candidateId);
            if (candidate.isEmpty() || !candidate.get().isActive()) {
                logger.warn("Invalid or inactive candidate: {}", candidateId);
                return false;
            }

            // Validate interviewer
            Optional<UserManagementClient.UserDetails> interviewer = userManagementClient.getUserById(interviewerId);
            if (interviewer.isEmpty() || !interviewer.get().isActive()) {
                logger.warn("Invalid or inactive interviewer: {}", interviewerId);
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("Error validating users: {}", e.getMessage());
            return false;
        }
    }

    private InterviewSessionStateService.InterviewSessionState createSessionState(String sessionId, InitializeInterviewRequest request) {
        InterviewSessionStateService.InterviewSessionState sessionState = new InterviewSessionStateService.InterviewSessionState();
        sessionState.setSessionId(sessionId);
        sessionState.setCandidateId(request.getCandidateId());
        sessionState.setInterviewerId(request.getInterviewerId());
        sessionState.setJobRole(request.getJobRole());
        sessionState.setRequiredSkills(request.getRequiredSkills());
        sessionState.setConfiguration(request.getConfiguration());
        sessionState.setCurrentQuestionIndex(0);
        sessionState.setCurrentTheta(0.0); // Initial IRT theta
        sessionState.setCurrentStandardError(1.0); // Initial IRT standard error
        return sessionState;
    }

    private void initializeAIServices(String sessionId, InitializeInterviewRequest request) {
        try {
            // Initialize adaptive engine with user profile
            Map<String, Object> initRequest = Map.of(
                "sessionId", sessionId,
                "candidateId", request.getCandidateId(),
                "jobRole", request.getJobRole(),
                "requiredSkills", request.getRequiredSkills()
            );
            
            adaptiveEngineRestTemplate.postForEntity("/initialize", initRequest, Map.class);
            logger.debug("Initialized adaptive engine for session: {}", sessionId);

            // Initialize speech service
            speechServiceRestTemplate.postForEntity("/session/initialize", 
                Map.of("sessionId", sessionId), Map.class);
            logger.debug("Initialized speech service for session: {}", sessionId);

        } catch (Exception e) {
            logger.error("Failed to initialize AI services for session {}: {}", sessionId, e.getMessage());
        }
    }

    private void setupRealtimeServices(String sessionId) {
        try {
            // Setup WebRTC room
            // This would integrate with Daily.co or similar service
            logger.debug("Setting up WebRTC for session: {}", sessionId);

            // Initialize transcript streaming
            Map<String, Object> transcriptSetup = Map.of(
                "sessionId", sessionId,
                "enableRealtime", true
            );
            speechServiceRestTemplate.postForEntity("/transcript/start", transcriptSetup, Map.class);
            logger.debug("Started transcript service for session: {}", sessionId);

        } catch (Exception e) {
            logger.error("Failed to setup real-time services for session {}: {}", sessionId, e.getMessage());
        }
    }

    private QuestionGenerationResult generateNextQuestion(String sessionId, InterviewExecutionContext context) {
        try {
            Map<String, Object> questionRequest = Map.of(
                "sessionId", sessionId,
                "currentTheta", context.getSessionState().getCurrentTheta(),
                "standardError", context.getSessionState().getCurrentStandardError(),
                "questionIndex", context.getSessionState().getCurrentQuestionIndex(),
                "jobRole", context.getSessionState().getJobRole(),
                "requiredSkills", context.getSessionState().getRequiredSkills()
            );

            var response = adaptiveEngineRestTemplate.postForEntity("/next-question", questionRequest, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> questionData = response.getBody();
                
                InterviewSessionStateService.QuestionData question = new InterviewSessionStateService.QuestionData();
                question.setQuestionId((String) questionData.get("questionId"));
                question.setQuestionText((String) questionData.get("questionText"));
                question.setQuestionType((String) questionData.get("questionType"));
                question.setDifficulty((Double) questionData.get("difficulty"));
                question.setCategory((String) questionData.get("category"));
                question.setPresentedAt(Instant.now());
                
                // Broadcast to frontend via WS
                try {
                    Map<String, Object> payload = Map.of(
                        "type", "new_question",
                        "data", Map.of(
                            "id", question.getQuestionId(),
                            "text", question.getQuestionText(),
                            "difficulty", question.getDifficulty(),
                            "category", question.getCategory()
                        )
                    );
                    orchestratorWebSocketHandler.broadcast(sessionId, payload);
                } catch (Exception ignored) {}
                return QuestionGenerationResult.success(question);
            }
            
            return QuestionGenerationResult.failure("Failed to generate question");

        } catch (Exception e) {
            logger.error("Failed to generate next question for session {}: {}", sessionId, e.getMessage());
            return QuestionGenerationResult.failure("Error generating question");
        }
    }

    private InterviewSessionStateService.ResponseData createResponseData(ProcessResponseRequest request) {
        InterviewSessionStateService.ResponseData responseData = new InterviewSessionStateService.ResponseData();
        responseData.setQuestionId(request.getQuestionId());
        responseData.setResponse(request.getResponse());
        responseData.setResponseType(request.getResponseType());
        responseData.setSubmittedAt(Instant.now());
        responseData.setResponseTime(request.getResponseTime());
        responseData.setMetadata(request.getMetadata());
        return responseData;
    }

    private AnalysisResult analyzeResponse(String sessionId, InterviewSessionStateService.ResponseData responseData, InterviewExecutionContext context) {
        try {
            Map<String, Object> analysisRequest = Map.of(
                "sessionId", sessionId,
                "questionId", responseData.getQuestionId(),
                "response", responseData.getResponse(),
                "responseType", responseData.getResponseType(),
                "responseTime", responseData.getResponseTime().toMillis()
            );

            var response = aiAnalyticsRestTemplate.postForEntity("/analyze/response", analysisRequest, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return AnalysisResult.success(response.getBody());
            }
            
            return AnalysisResult.failure("Analysis failed");

        } catch (Exception e) {
            logger.error("Failed to analyze response for session {}: {}", sessionId, e.getMessage());
            return AnalysisResult.failure("Analysis error");
        }
    }

    private void updateIRTParameters(InterviewExecutionContext context, InterviewSessionStateService.ResponseData responseData) {
        // Simple IRT parameter update logic
        // In a real implementation, this would be more sophisticated
        double currentTheta = context.getSessionState().getCurrentTheta();
        double currentSE = context.getSessionState().getCurrentStandardError();
        
        // Update based on response (simplified)
        double newTheta = currentTheta + (Math.random() - 0.5) * 0.1;
        double newSE = Math.max(0.1, currentSE * 0.9);
        
        context.getSessionState().setCurrentTheta(newTheta);
        context.getSessionState().setCurrentStandardError(newSE);
        
        sessionStateService.updateInterviewSessionState(context.getSessionId(), context.getSessionState());
    }

    // 🚀 NEW: Optimized lightweight IRT parameter updates
    private void updateIRTParametersOptimized(InterviewExecutionContext context, InterviewSessionStateService.ResponseData responseData) {
        // 🚀 LIGHTWEIGHT: Quick parameter estimation without complex calculations
        double currentTheta = context.getSessionState().getCurrentTheta();
        double currentSE = context.getSessionState().getCurrentStandardError();
        
        // Quick response quality estimation based on timing and length
        long responseTimeMs = responseData.getResponseTime() != null ? responseData.getResponseTime().toMillis() : 30000;
        int responseLength = responseData.getResponse() != null ? responseData.getResponse().length() : 0;
        
        // Fast theta adjustment
        double adjustment = 0.0;
        if (responseTimeMs < 10000 && responseLength > 50) {
            adjustment = 0.05; // Quick, substantial response
        } else if (responseTimeMs > 60000 || responseLength < 20) {
            adjustment = -0.05; // Slow or minimal response
        }
        
        double newTheta = Math.max(-3.0, Math.min(3.0, currentTheta + adjustment));
        double newSE = Math.max(0.1, currentSE * 0.95);
        
        context.getSessionState().setCurrentTheta(newTheta);
        context.getSessionState().setCurrentStandardError(newSE);
        
        // 🚀 ASYNC: Update session state in background
        CompletableFuture.runAsync(() -> 
            sessionStateService.updateInterviewSessionState(context.getSessionId(), context.getSessionState()));
    }

    // 🚀 NEW: Optimized question generation
    private QuestionGenerationResult generateNextQuestionOptimized(String sessionId, InterviewExecutionContext context) {
        try {
            // 🚀 FAST: Use cached question bank instead of API call when possible
            if (useQuestionCache(context)) {
                return generateFromCachedQuestions(sessionId, context);
            }
            
            // 🚀 TIMEOUT: Set 1-second timeout for question generation
            Map<String, Object> questionRequest = Map.of(
                "sessionId", sessionId,
                "currentTheta", context.getSessionState().getCurrentTheta(),
                "standardError", context.getSessionState().getCurrentStandardError(),
                "questionIndex", context.getSessionState().getCurrentQuestionIndex(),
                "jobRole", context.getSessionState().getJobRole(),
                "requiredSkills", context.getSessionState().getRequiredSkills(),
                "fastGeneration", true, // Flag for quick generation
                "timeoutMs", 800 // 800ms timeout
            );

            var response = adaptiveEngineRestTemplate.postForEntity("/next-question-fast", questionRequest, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> questionData = response.getBody();
                
                InterviewSessionStateService.QuestionData question = new InterviewSessionStateService.QuestionData();
                question.setQuestionId((String) questionData.get("questionId"));
                question.setQuestionText((String) questionData.get("questionText"));
                question.setQuestionType((String) questionData.get("questionType"));
                question.setDifficulty((Double) questionData.get("difficulty"));
                question.setCategory((String) questionData.get("category"));
                question.setPresentedAt(Instant.now());
                
                return QuestionGenerationResult.success(question);
            }
            
            // Fallback to cached questions
            return generateFromCachedQuestions(sessionId, context);
            
        } catch (Exception e) {
            logger.warn("⚠️ Fast question generation failed, using fallback: {}", e.getMessage());
            return generateFromCachedQuestions(sessionId, context);
        }
    }

    // 🚀 NEW: Immediate question broadcast to AI Avatar
    private void broadcastNextQuestionImmediate(String sessionId, InterviewSessionStateService.QuestionData question) {
        try {
            Map<String, Object> payload = Map.of(
                "type", "next_question_immediate",
                "sessionId", sessionId,
                "question", Map.of(
                    "id", question.getQuestionId(),
                    "text", question.getQuestionText(),
                    "difficulty", question.getDifficulty(),
                    "category", question.getCategory(),
                    "timestamp", Instant.now().toEpochMilli()
                ),
                "aiInstruction", "CONTINUE_IMMEDIATELY" // Instruct AI to continue without delay
            );
            
            // 🚀 IMMEDIATE: Non-blocking broadcast
            CompletableFuture.runAsync(() -> {
                try {
                    orchestratorWebSocketHandler.broadcast(sessionId, payload);
                    logger.debug("✅ Next question broadcasted immediately to AI Avatar for session: {}", sessionId);
                } catch (Exception e) {
                    logger.warn("⚠️ Failed to broadcast next question: {}", e.getMessage());
                }
            });
            
        } catch (Exception e) {
            logger.warn("⚠️ Error in immediate question broadcast: {}", e.getMessage());
        }
    }

    // 🚀 NEW: Check if we can use cached questions for speed
    private boolean useQuestionCache(InterviewExecutionContext context) {
        // Use cache for stages that need quick transitions
        int questionIndex = context.getSessionState().getCurrentQuestionIndex();
        return questionIndex > 5; // After first 5 questions, use caching for speed
    }

    // 🚀 NEW: Generate from pre-cached questions
    private QuestionGenerationResult generateFromCachedQuestions(String sessionId, InterviewExecutionContext context) {
        try {
            // Simplified question generation from predefined set
            String[] quickQuestions = {
                "Can you explain how you would optimize the performance of this solution?",
                "What potential issues do you see with this approach?",
                "How would you handle edge cases in this scenario?",
                "What testing strategy would you implement for this code?",
                "How would you scale this solution for high traffic?"
            };
            
            int questionIndex = context.getSessionState().getCurrentQuestionIndex();
            String questionText = quickQuestions[questionIndex % quickQuestions.length];
            
            InterviewSessionStateService.QuestionData question = new InterviewSessionStateService.QuestionData();
            question.setQuestionId("cached_" + questionIndex + "_" + System.currentTimeMillis());
            question.setQuestionText(questionText);
            question.setQuestionType("follow_up");
            question.setDifficulty(2.0);
            question.setCategory("technical");
            question.setPresentedAt(Instant.now());
            
            logger.debug("✅ Generated cached question for session: {}", sessionId);
            return QuestionGenerationResult.success(question);
            
        } catch (Exception e) {
            logger.error("❌ Failed to generate cached question: {}", e.getMessage());
            return QuestionGenerationResult.failure("Failed to generate cached question");
        }
    }

    // 🚀 NEW: Async performance update
    private void updateCandidatePerformanceAsync(String sessionId, Map<String, Object> analysisData) {
        CompletableFuture.runAsync(() -> {
            try {
                // Update candidate performance metrics in background
                logger.debug("✅ Updating candidate performance for session: {}", sessionId);
                // Implementation for performance tracking
            } catch (Exception e) {
                logger.warn("⚠️ Failed to update candidate performance: {}", e.getMessage());
            }
        });
    }

    private boolean shouldContinueInterview(InterviewExecutionContext context) {
        int currentQuestions = context.getSessionState().getCurrentQuestionIndex();
        int maxQuestions = (Integer) context.getSessionState().getConfiguration().getOrDefault("maxQuestions", 20);
        int minQuestions = (Integer) context.getSessionState().getConfiguration().getOrDefault("minQuestions", 5);
        
        // Continue if under max questions and either under min questions or confidence not reached
        return currentQuestions < maxQuestions && 
               (currentQuestions < minQuestions || context.getSessionState().getCurrentStandardError() > 0.3);
    }

    private FinalAnalysisResult generateFinalAnalysis(String sessionId, InterviewExecutionContext context) {
        try {
            Map<String, Object> finalAnalysisRequest = Map.of(
                "sessionId", sessionId,
                "candidateId", context.getSessionState().getCandidateId(),
                "totalQuestions", context.getSessionState().getCurrentQuestionIndex(),
                "finalTheta", context.getSessionState().getCurrentTheta(),
                "jobRole", context.getSessionState().getJobRole()
            );

            var response = aiAnalyticsRestTemplate.postForEntity("/analyze/final", finalAnalysisRequest, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return FinalAnalysisResult.success(response.getBody());
            }
            
            return FinalAnalysisResult.failure("Final analysis failed");

        } catch (Exception e) {
            logger.error("Failed to generate final analysis for session {}: {}", sessionId, e.getMessage());
            return FinalAnalysisResult.failure("Final analysis error");
        }
    }

    private void cleanupRealtimeServices(String sessionId) {
        try {
            // Stop transcript service
            speechServiceRestTemplate.postForEntity("/transcript/stop", 
                Map.of("sessionId", sessionId), Map.class);
            
            // Clean up session state
            sessionStateService.cleanupSession(sessionId);
            
            logger.debug("Cleaned up real-time services for session: {}", sessionId);

        } catch (Exception e) {
            logger.error("Failed to cleanup real-time services for session {}: {}", sessionId, e.getMessage());
        }
    }

    private void updateUserStatistics(InterviewSessionStateService.InterviewSessionState sessionState) {
        try {
            UserManagementClient.InterviewStatsUpdate statsUpdate = new UserManagementClient.InterviewStatsUpdate();
            statsUpdate.setTotalInterviews(1); // Increment by 1
            statsUpdate.setCompletedInterviews(1); // This interview was completed
            statsUpdate.setLastInterviewDate(Instant.now().toString());
            
            userManagementClient.updateUserInterviewStats(sessionState.getCandidateId(), statsUpdate);
            logger.debug("Updated user statistics for candidate: {}", sessionState.getCandidateId());

        } catch (Exception e) {
            logger.error("Failed to update user statistics: {}", e.getMessage());
        }
    }

    private String generateSessionId() {
        // Generate a simple UUID for session ID that's compatible with UUID parsing
        return UUID.randomUUID().toString();
    }

    // Data classes and supporting types

    public static class InterviewExecutionContext {
        private final String sessionId;
        private final InterviewSessionStateService.InterviewSessionState sessionState;
        private InterviewSessionStateService.QuestionData currentQuestion;
        private Instant initializedAt;
        private Instant startedAt;
        private Instant completedAt;
        private Instant terminatedAt;
        private String terminationReason;

        public InterviewExecutionContext(String sessionId, InterviewSessionStateService.InterviewSessionState sessionState) {
            this.sessionId = sessionId;
            this.sessionState = sessionState;
        }

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public InterviewSessionStateService.InterviewSessionState getSessionState() { return sessionState; }
        public InterviewSessionStateService.QuestionData getCurrentQuestion() { return currentQuestion; }
        public void setCurrentQuestion(InterviewSessionStateService.QuestionData currentQuestion) { this.currentQuestion = currentQuestion; }
        public Instant getInitializedAt() { return initializedAt; }
        public void setInitializedAt(Instant initializedAt) { this.initializedAt = initializedAt; }
        public Instant getStartedAt() { return startedAt; }
        public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
        public Instant getTerminatedAt() { return terminatedAt; }
        public void setTerminatedAt(Instant terminatedAt) { this.terminatedAt = terminatedAt; }
        public String getTerminationReason() { return terminationReason; }
        public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }
        
        public void incrementQuestionIndex() {
            this.sessionState.setCurrentQuestionIndex(this.sessionState.getCurrentQuestionIndex() + 1);
        }
    }

    // Request/Response classes
    public static class InitializeInterviewRequest {
        private Long candidateId;
        private Long interviewerId;
        private String jobRole;
        private List<String> requiredSkills;
        private Map<String, Object> configuration;

        // Getters and setters
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public Long getInterviewerId() { return interviewerId; }
        public void setInterviewerId(Long interviewerId) { this.interviewerId = interviewerId; }
        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }
        public List<String> getRequiredSkills() { return requiredSkills; }
        public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }
        public Map<String, Object> getConfiguration() { return configuration; }
        public void setConfiguration(Map<String, Object> configuration) { this.configuration = configuration; }
    }

    public static class ProcessResponseRequest {
        private String questionId;
        private String response;
        private String responseType;
        private Duration responseTime;
        private Map<String, Object> metadata;

        // Getters and setters
        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public String getResponseType() { return responseType; }
        public void setResponseType(String responseType) { this.responseType = responseType; }
        public Duration getResponseTime() { return responseTime; }
        public void setResponseTime(Duration responseTime) { this.responseTime = responseTime; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    // Result classes
    public static class InterviewSessionResult {
        private boolean success;
        private String sessionId;
        private String message;
        private InterviewSessionStateService.InterviewSessionState sessionState;

        public static InterviewSessionResult success(String sessionId, InterviewSessionStateService.InterviewSessionState sessionState) {
            InterviewSessionResult result = new InterviewSessionResult();
            result.success = true;
            result.sessionId = sessionId;
            result.sessionState = sessionState;
            return result;
        }

        public static InterviewSessionResult failure(String message) {
            InterviewSessionResult result = new InterviewSessionResult();
            result.success = false;
            result.message = message;
            return result;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getSessionId() { return sessionId; }
        public String getMessage() { return message; }
        public InterviewSessionStateService.InterviewSessionState getSessionState() { return sessionState; }
    }

    public static class QuestionGenerationResult {
        private boolean success;
        private String message;
        private InterviewSessionStateService.QuestionData questionData;

        public static QuestionGenerationResult success(InterviewSessionStateService.QuestionData questionData) {
            QuestionGenerationResult result = new QuestionGenerationResult();
            result.success = true;
            result.questionData = questionData;
            return result;
        }

        public static QuestionGenerationResult failure(String message) {
            QuestionGenerationResult result = new QuestionGenerationResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() { return success; }
        public InterviewSessionStateService.QuestionData getQuestionData() { return questionData; }
    }

    public static class AnalysisResult {
        private boolean success;
        private String message;
        private Map<String, Object> analysisData;

        public static AnalysisResult success(Map<String, Object> analysisData) {
            AnalysisResult result = new AnalysisResult();
            result.success = true;
            result.analysisData = analysisData;
            return result;
        }

        public static AnalysisResult failure(String message) {
            AnalysisResult result = new AnalysisResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() { return success; }
        public Map<String, Object> getAnalysisData() { return analysisData; }
    }

    public static class FinalAnalysisResult {
        private boolean success;
        private String message;
        private Map<String, Object> finalAnalysis;

        public static FinalAnalysisResult success(Map<String, Object> finalAnalysis) {
            FinalAnalysisResult result = new FinalAnalysisResult();
            result.success = true;
            result.finalAnalysis = finalAnalysis;
            return result;
        }

        public static FinalAnalysisResult failure(String message) {
            FinalAnalysisResult result = new FinalAnalysisResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() { return success; }
    }

    // --- Aggregated report (final analysis + transcript) ---
    public Map<String, Object> getAggregatedReport(String sessionId) {
        try {
            Map<String, Object> finalAnalysis = Map.of("status", "pending");
            try {
                var response = aiAnalyticsRestTemplate.getForEntity("/analyze/final/" + sessionId, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    finalAnalysis = response.getBody();
                }
            } catch (Exception ignored) {}

            Map<String, Object> transcript = Map.of();
            try {
                var response = speechServiceRestTemplate.getForEntity("/transcript/" + sessionId, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    transcript = response.getBody();
                }
            } catch (Exception ignored) {}

            return Map.of(
                "sessionId", sessionId,
                "finalAnalysis", finalAnalysis,
                "transcript", transcript
            );
        } catch (Exception e) {
            return Map.of("sessionId", sessionId, "error", "Failed to assemble report");
        }
    }

    // New scheduling methods
    @Autowired
    private MeetingRoomService meetingRoomService;
    
    // In-memory storage for demo purposes (in production, use a database)
    private final Map<String, InterviewSessionResponse> sessionStorage = new ConcurrentHashMap<>();

    /**
     * Schedule a new interview session with meeting room creation and job analysis
     */
    public InterviewSessionResponse scheduleInterview(InterviewScheduleRequest request) {
        try {
            logger.info("Scheduling interview for candidate {} with recruiter {} for role {}", 
                request.getCandidateId(), request.getRecruiterId(), request.getJobRole());

            // Generate session ID
            String sessionId = generateSessionId();
            
            // Step 1: Perform job analysis if enabled and job description is provided
            JobAnalysisClient.JobAnalysisResponse jobAnalysis = null;
            Map<String, Object> interviewPreview = null;
            
            if (Boolean.TRUE.equals(request.getEnableJobAnalysis()) && 
                request.getJobDescription() != null && !request.getJobDescription().trim().isEmpty()) {
                
                logger.info("Performing job analysis for session: {}", sessionId);
                jobAnalysis = performJobAnalysis(request, sessionId);
                
                if (jobAnalysis != null) {
                    // Get interview preview based on job analysis
                    interviewPreview = getJobAwareInterviewPreview(request, sessionId);
                    logger.info("Job analysis completed for session: {} with {} key competencies", 
                            sessionId, jobAnalysis.getKeyCompetencies() != null ? jobAnalysis.getKeyCompetencies().size() : 0);
                }
            }
            
            // Step 2: Create meeting room via Jitsi Meet (Free)
            String candidateName = getCandidateName(request.getCandidateId());
            Map<String, String> roomInfo = meetingRoomService.createRoom(sessionId, candidateName);
            
            // Step 3: Build response with meeting details and job analysis data
            InterviewSessionResponse.InterviewSessionResponseBuilder responseBuilder = InterviewSessionResponse.builder()
                .sessionId(UUID.fromString(sessionId))
                .candidateId(request.getCandidateId())
                .recruiterId(request.getRecruiterId())
                .status("SCHEDULED")
                .scheduledStartTime(request.getScheduledStartTime())
                .meetingLink(roomInfo.get("roomUrl"))
                .webrtcRoomId(roomInfo.get("roomId"))
                .jobRole(request.getJobRole())
                .experienceLevel(request.getExperienceLevel())
                .requiredTechnologies(request.getRequiredTechnologies())
                .interviewType(request.getInterviewType())
                .languagePreference(request.getLanguagePreference())
                .minQuestions(request.getMinQuestions())
                .maxQuestions(request.getMaxQuestions())
                .canStart(true)
                .canTerminateEarly(false)
                .isMaxQuestionsReached(false)
                .nextAction("START_INTERVIEW")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now());
            
            // Add job analysis data if available
            if (jobAnalysis != null) {
                responseBuilder
                    .jobAnalysisCompleted(true)
                    .keyCompetencies(jobAnalysis.getKeyCompetencies())
                    .priorityTechnicalSkills(jobAnalysis.getPriorityTechnicalSkills())
                    .suggestedDuration(jobAnalysis.getSuggestedInterviewDuration())
                    .jobAnalysisConfidence(jobAnalysis.getConfidenceScore());
                    
                // Adjust initial theta based on job analysis
                Double recommendedTheta = (Double) jobAnalysis.getDifficultyAdjustment().get("initial_theta");
                if (recommendedTheta != null) {
                    responseBuilder.recommendedInitialTheta(recommendedTheta);
                }
            } else {
                responseBuilder.jobAnalysisCompleted(false);
            }
            
            InterviewSessionResponse response = responseBuilder.build();
            
            // Step 4: Initialize adaptive engine with job context if available
            final JobAnalysisClient.JobAnalysisResponse finalJobAnalysis = jobAnalysis;
            if (finalJobAnalysis != null && Boolean.TRUE.equals(request.getUseJobAwareQuestions())) {
                CompletableFuture.runAsync(() -> initializeJobAwareAdaptiveEngine(sessionId, request, finalJobAnalysis));
            }
            
            logger.info("Successfully scheduled interview session: {} with meeting link: {}, job analysis: {}", 
                sessionId, roomInfo.get("roomUrl"), jobAnalysis != null ? "completed" : "skipped");
            
            // Store in memory for retrieval
            sessionStorage.put(sessionId, response);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to schedule interview: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to schedule interview: " + e.getMessage());
        }
    }

    /**
     * Get interview session details
     */
    public InterviewSessionResponse getInterviewSession(String sessionId) {
        try {
            // Check if we have the session stored in memory
            InterviewSessionResponse storedSession = sessionStorage.get(sessionId);
            if (storedSession != null) {
                logger.info("Retrieved stored session: {} with meeting link: {}", sessionId, storedSession.getMeetingLink());
                return storedSession;
            }
            
            // If not found in memory, return null (in production, check database)
            logger.warn("Session not found: {}", sessionId);
            return null;
                
        } catch (Exception e) {
            logger.error("Failed to fetch interview session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * Start an interview session (overloaded method for API)
     */
    public InterviewSessionResponse startInterview(String sessionId) {
        try {
            // Update session status to IN_PROGRESS
            InterviewSessionResponse session = getInterviewSession(sessionId);
            if (session != null) {
                // In real implementation, update database
                session = InterviewSessionResponse.builder()
                    .sessionId(session.getSessionId())
                    .status("IN_PROGRESS")
                    .actualStartTime(LocalDateTime.now())
                    .meetingLink(session.getMeetingLink())
                    .webrtcRoomId(session.getWebrtcRoomId())
                    .canStart(false)
                    .canTerminateEarly(true)
                    .nextAction("CONTINUE_QUESTIONS")
                    .build();
            }
            return session;
            
        } catch (Exception e) {
            logger.error("Failed to start interview session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * End an interview session (overloaded method for API)
     */
    public InterviewSessionResponse endInterview(String sessionId) {
        try {
            // Update session status to COMPLETED
            InterviewSessionResponse session = getInterviewSession(sessionId);
            if (session != null) {
                // In real implementation, update database and cleanup
                session = InterviewSessionResponse.builder()
                    .sessionId(session.getSessionId())
                    .status("COMPLETED")
                    .endTime(LocalDateTime.now())
                    .meetingLink(session.getMeetingLink())
                    .webrtcRoomId(session.getWebrtcRoomId())
                    .canStart(false)
                    .canTerminateEarly(false)
                    .nextAction("VIEW_RESULTS")
                    .build();
            }
            return session;
            
        } catch (Exception e) {
            logger.error("Failed to end interview session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * Get current question for session using proper stage-wise adaptive flow
     */
    public Map<String, Object> getCurrentQuestion(String sessionId) {
        try {
            logger.info("Getting current question for session: {}", sessionId);
            
            // Get session information
            InterviewSessionResponse session = getInterviewSession(sessionId);
            if (session == null) {
                logger.warn("Session not found for getCurrentQuestion: {}", sessionId);
                return getDefaultQuestion();
            }
            
            // Check if adaptive interview is already initialized for this session
            Optional<AdaptiveInterviewService.AdaptiveInterviewState> adaptiveState = 
                    adaptiveInterviewService.getInterviewState(sessionId);
            
            if (adaptiveState.isEmpty()) {
                logger.info("Initializing adaptive interview for session: {}", sessionId);
                
                // Initialize adaptive interview with session data
                AdaptiveInterviewService.AdaptiveInterviewState newState = 
                        adaptiveInterviewService.initializeInterview(
                            sessionId,
                            session.getCandidateId().intValue(),
                            session.getJobRole(),
                            session.getExperienceLevel(),
                            session.getRequiredTechnologies()
                        );
                
                logger.info("Adaptive interview initialized for session: {} in {} stage", 
                        sessionId, newState.getCurrentStage().getDescription());
            }
            
            // Get next question using adaptive interview service (follows stage-wise flow)
            Optional<AdaptiveEngineClient.QuestionResponse> adaptiveQuestion = 
                    adaptiveInterviewService.getNextQuestion(sessionId);
            
            if (adaptiveQuestion.isPresent()) {
                AdaptiveEngineClient.QuestionResponse question = adaptiveQuestion.get();
                
                // Convert to the expected format
                Map<String, Object> questionMap = Map.of(
                    "id", question.getQuestionId(),
                    "text", question.getQuestionText(),
                    "type", question.getQuestionType(),
                    "difficulty", question.getDifficulty(),
                    "category", question.getCategory(),
                    "estimatedSeconds", 300,
                    "coding_required", "coding".equals(question.getQuestionType()),
                    "programming_language", determineProgrammingLanguage(question.getQuestionType(), session.getRequiredTechnologies())
                );
                
                // Log the stage-wise progression
                AdaptiveInterviewService.AdaptiveInterviewState currentState = 
                        adaptiveInterviewService.getInterviewState(sessionId).orElse(null);
                if (currentState != null) {
                    logger.info("Generated question for session: {} in {} stage (Question #{}) - {}", 
                            sessionId, 
                            currentState.getCurrentStage().getDescription(),
                            currentState.getQuestionsAsked(),
                            question.getQuestionText().substring(0, Math.min(50, question.getQuestionText().length())) + "...");
                }
                
                return questionMap;
            } else {
                logger.warn("Adaptive interview service returned no question for session: {}, falling back to job-specific question", sessionId);
                
                // Fallback to job-specific question generation
                return generateJobSpecificQuestion(session.getJobRole(), session.getRequiredTechnologies());
            }
            
        } catch (Exception e) {
            logger.error("Failed to get current question for session {}: {}", sessionId, e.getMessage());
            return getDefaultQuestion();
        }
    }

    /**
     * Process response (API method)
     */
    public Map<String, Object> processResponse(String sessionId, Map<String, Object> response) {
        try {
            // Get session information to determine job role
            InterviewSessionResponse session = getInterviewSession(sessionId);
            if (session == null) {
                logger.warn("Session not found for processResponse: {}", sessionId);
                return Map.of("shouldEnd", true, "message", "Session not found");
            }
            
            String jobRole = session.getJobRole();
            List<String> technologies = session.getRequiredTechnologies();
            
            logger.info("Processing response for session: {} with job role: {}", sessionId, jobRole);
            
            // Mock processing with job-role awareness
            boolean shouldEnd = Math.random() > 0.7; // 30% chance to end
            
            if (shouldEnd) {
                return Map.of(
                    "shouldEnd", true,
                    "message", "Interview completed successfully"
                );
            } else {
                // Generate next question based on job role
                Map<String, Object> nextQuestion = generateJobSpecificQuestion(jobRole, technologies);
                nextQuestion.put("id", 2); // Update question ID
                
                return Map.of(
                    "shouldEnd", false,
                    "nextQuestion", nextQuestion
                );
            }
            
        } catch (Exception e) {
            logger.error("Failed to process response for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * Get interview results (API method)
     */
    public Map<String, Object> getInterviewResults(String sessionId) {
        try {
            // Mock results
            return Map.of(
                "sessionId", sessionId,
                "overallScore", 85.5,
                "technicalScore", 88.0,
                "communicationScore", 82.0,
                "biasScore", 15.0,
                "questionsAnswered", 8,
                "totalDuration", "45 minutes",
                "recommendation", "HIRE",
                "strengths", List.of("Strong technical knowledge", "Clear communication", "Problem-solving skills"),
                "improvements", List.of("Could improve on system design", "More practice with algorithms")
            );
            
        } catch (Exception e) {
            logger.error("Failed to get results for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to get candidate name
     */
    private String getCandidateName(Long candidateId) {
        try {
            Optional<UserManagementClient.UserDetails> candidate = userManagementClient.getUserById(candidateId);
            return candidate.map(UserManagementClient.UserDetails::getName).orElse("Candidate_" + candidateId);
        } catch (Exception e) {
            logger.warn("Failed to get candidate name for ID {}: {}", candidateId, e.getMessage());
            return "Candidate_" + candidateId;
        }
    }
    
    /**
     * Perform job analysis for interview customization
     */
    private JobAnalysisClient.JobAnalysisResponse performJobAnalysis(InterviewScheduleRequest request, String sessionId) {
        try {
            // Create cache key based on job description content
            String cacheKey = String.valueOf((request.getJobDescription() + request.getKeyResponsibilities()).hashCode());
            
            // Check cache first
            JobAnalysisClient.JobAnalysisResponse cachedAnalysis = jobAnalysisCache.get(cacheKey);
            if (cachedAnalysis != null) {
                logger.debug("Using cached job analysis for session: {}", sessionId);
                return cachedAnalysis;
            }
            
            // Create job analysis request
            JobAnalysisClient.JobAnalysisRequest analysisRequest = new JobAnalysisClient.JobAnalysisRequest();
            analysisRequest.setJobDescription(request.getJobDescription());
            analysisRequest.setKeyResponsibilities(request.getKeyResponsibilities());
            analysisRequest.setJobRole(request.getJobRole());
            analysisRequest.setCompanyContext(request.getCompanyContext());
            
            // Perform analysis via job analyzer service
            Optional<JobAnalysisClient.JobAnalysisResponse> analysisResult = 
                    jobAnalysisClient.analyzeJob(analysisRequest);
            
            if (analysisResult.isPresent()) {
                JobAnalysisClient.JobAnalysisResponse analysis = analysisResult.get();
                
                // Cache the result
                jobAnalysisCache.put(cacheKey, analysis);
                
                logger.info("Job analysis completed for session: {} - {} competencies, {} technical skills", 
                        sessionId, 
                        analysis.getKeyCompetencies() != null ? analysis.getKeyCompetencies().size() : 0,
                        analysis.getTechnicalSkills() != null ? analysis.getTechnicalSkills().size() : 0);
                
                return analysis;
            } else {
                logger.warn("Job analysis service returned empty result for session: {}", sessionId);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Failed to perform job analysis for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get job-aware interview preview
     */
    private Map<String, Object> getJobAwareInterviewPreview(InterviewScheduleRequest request, String sessionId) {
        try {
            // Create preview request via adaptive engine
            AdaptiveEngineClient.JobAnalysisRequest previewRequest = new AdaptiveEngineClient.JobAnalysisRequest();
            previewRequest.setJobDescription(request.getJobDescription());
            previewRequest.setKeyResponsibilities(request.getKeyResponsibilities());
            previewRequest.setJobRole(request.getJobRole());
            previewRequest.setCompanyContext(request.getCompanyContext());
            
            Optional<Map<String, Object>> previewResult = adaptiveEngineClient.analyzeJobForInterview(previewRequest);
            
            if (previewResult.isPresent()) {
                logger.debug("Interview preview generated for session: {}", sessionId);
                return previewResult.get();
            } else {
                logger.warn("Failed to generate interview preview for session: {}", sessionId);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error generating interview preview for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Initialize adaptive engine with job context
     */
    private void initializeJobAwareAdaptiveEngine(String sessionId, InterviewScheduleRequest request, 
                                                 JobAnalysisClient.JobAnalysisResponse jobAnalysis) {
        try {
            logger.info("Initializing job-aware adaptive engine for session: {}", sessionId);
            
            // Create job-aware initialization request
            Map<String, Object> initRequest = new HashMap<>();
            initRequest.put("sessionId", sessionId);
            initRequest.put("candidateId", request.getCandidateId());
            initRequest.put("jobRole", request.getJobRole());
            initRequest.put("experienceLevel", request.getExperienceLevel());
            initRequest.put("requiredSkills", request.getRequiredTechnologies());
            
            // Add job analysis context
            initRequest.put("jobDescription", request.getJobDescription());
            initRequest.put("keyResponsibilities", request.getKeyResponsibilities());
            initRequest.put("keyCompetencies", jobAnalysis.getKeyCompetencies());
            initRequest.put("priorityTechnicalSkills", jobAnalysis.getPriorityTechnicalSkills());
            initRequest.put("questionCategoryWeights", jobAnalysis.getQuestionCategoryWeights());
            initRequest.put("difficultyAdjustment", jobAnalysis.getDifficultyAdjustment());
            
            // Send initialization to adaptive engine
            adaptiveEngineRestTemplate.postForEntity("/configure-interview", initRequest, Map.class);
            
            logger.info("Job-aware adaptive engine initialized for session: {}", sessionId);
            
        } catch (Exception e) {
            logger.error("Failed to initialize job-aware adaptive engine for session {}: {}", 
                    sessionId, e.getMessage());
        }
    }
    
    /**
     * Generate next question with job context awareness
     */
    private QuestionGenerationResult generateNextJobAwareQuestion(String sessionId, InterviewExecutionContext context, 
                                                                 JobAnalysisClient.JobAnalysisResponse jobAnalysis) {
        try {
            // Create job-aware question request
            AdaptiveEngineClient.JobAwareQuestionRequest request = new AdaptiveEngineClient.JobAwareQuestionRequest();
            request.setSessionId(sessionId);
            request.setCandidateId(context.getSessionState().getCandidateId().intValue());
            request.setCurrentTheta(context.getSessionState().getCurrentTheta());
            request.setStandardError(context.getSessionState().getCurrentStandardError());
            request.setJobRole(context.getSessionState().getJobRole());
            request.setExperienceLevel("INTERMEDIATE"); // Get from context
            request.setTechnologies(context.getSessionState().getRequiredSkills());
            
            // Add job context from schedule request (would need to be stored in context)
            // For now, we'll retrieve from cached job analysis
            if (jobAnalysis != null) {
                // This would typically come from stored session data
                request.setJobDescription("[Job description from session data]");
                request.setKeyResponsibilities("[Key responsibilities from session data]");
            }
            
            Optional<AdaptiveEngineClient.QuestionResponse> response = 
                    adaptiveEngineClient.getNextJobAwareQuestion(request);
            
            if (response.isPresent()) {
                AdaptiveEngineClient.QuestionResponse questionResponse = response.get();
                
                InterviewSessionStateService.QuestionData question = new InterviewSessionStateService.QuestionData();
                question.setQuestionId(String.valueOf(questionResponse.getQuestionId()));
                question.setQuestionText(questionResponse.getQuestionText());
                question.setQuestionType(questionResponse.getQuestionType());
                question.setDifficulty(questionResponse.getDifficulty());
                question.setCategory(questionResponse.getCategory());
                question.setPresentedAt(Instant.now());
                
                return QuestionGenerationResult.success(question);
            }
            
            return QuestionGenerationResult.failure("Failed to generate job-aware question");
            
        } catch (Exception e) {
            logger.error("Failed to generate job-aware question for session {}: {}", sessionId, e.getMessage());
            return QuestionGenerationResult.failure("Error generating job-aware question");
        }
    }
    
    /**
     * Generate job-specific question based on role and technologies
     */
    private Map<String, Object> generateJobSpecificQuestion(String jobRole, List<String> technologies) {
        try {
            logger.debug("Generating job-specific question for role: {} with technologies: {}", jobRole, technologies);
            
            // Get primary technology for code examples
            String primaryTech = (technologies != null && !technologies.isEmpty()) ? 
                    technologies.get(0).toLowerCase() : "java";
            
            // Generate role-appropriate questions
            switch (jobRole.toLowerCase()) {
                case "frontend developer":
                case "react developer":
                case "frontend engineer":
                    return generateFrontendQuestion(primaryTech);
                    
                case "backend developer":
                case "java developer":
                case "backend engineer":
                    return generateBackendQuestion(primaryTech);
                    
                case "full stack developer":
                case "fullstack developer":
                case "full-stack engineer":
                    return generateFullStackQuestion(primaryTech);
                    
                case "devops engineer":
                case "sre":
                case "platform engineer":
                    return generateDevOpsQuestion(primaryTech);
                    
                case "data scientist":
                case "machine learning engineer":
                case "ml engineer":
                    return generateDataScienceQuestion(primaryTech);
                    
                case "mobile developer":
                case "ios developer":
                case "android developer":
                    return generateMobileQuestion(primaryTech);
                    
                default:
                    logger.warn("Unknown job role: {}, using default question", jobRole);
                    return getDefaultQuestion();
            }
            
        } catch (Exception e) {
            logger.error("Error generating job-specific question for role {}: {}", jobRole, e.getMessage());
            return getDefaultQuestion();
        }
    }
    
    /**
     * Generate frontend-specific questions
     */
    private Map<String, Object> generateFrontendQuestion(String primaryTech) {
        String[] questions = {
            "How would you optimize the performance of a React application that's experiencing slow re-renders?",
            "Explain the difference between client-side and server-side rendering. When would you use each?",
            "How would you implement responsive design for a complex dashboard component?",
            "Describe how you would handle state management in a large-scale frontend application.",
            "How would you implement accessibility features for a dynamic form component?"
        };
        
        String language = primaryTech.contains("react") || primaryTech.contains("javascript") || primaryTech.contains("typescript") 
                ? "javascript" : "javascript";
        
        return createQuestionMap(questions[(int)(Math.random() * questions.length)], "technical", "Medium", 480, true, language);
    }
    
    /**
     * Generate backend-specific questions
     */
    private Map<String, Object> generateBackendQuestion(String primaryTech) {
        String[] questions = {
            "Design a REST API for a e-commerce order management system. Include endpoints and data models.",
            "How would you implement caching in a high-traffic web application? Discuss different strategies.",
            "Explain how you would design a microservices architecture for a social media platform.",
            "How would you handle database migrations in a production environment with zero downtime?",
            "Implement a rate limiting mechanism for an API that serves millions of requests per day."
        };
        
        String language = primaryTech.contains("java") ? "java" : 
                         primaryTech.contains("python") ? "python" : 
                         primaryTech.contains("node") || primaryTech.contains("javascript") ? "javascript" : "java";
        
        return createQuestionMap(questions[(int)(Math.random() * questions.length)], "technical", "Hard", 600, true, language);
    }
    
    /**
     * Generate full-stack questions
     */
    private Map<String, Object> generateFullStackQuestion(String primaryTech) {
        String[] questions = {
            "Design and implement a real-time chat application. Discuss both frontend and backend considerations.",
            "How would you architect a scalable web application that handles both web and mobile clients?",
            "Implement user authentication and authorization for a multi-tenant SaaS application.",
            "Design a CI/CD pipeline for a full-stack application with automated testing and deployment.",
            "How would you implement real-time notifications across web and mobile platforms?"
        };
        
        return createQuestionMap(questions[(int)(Math.random() * questions.length)], "architectural", "Hard", 720, true, primaryTech);
    }
    
    /**
     * Generate DevOps-specific questions
     */
    private Map<String, Object> generateDevOpsQuestion(String primaryTech) {
        String[] questions = {
            "Design a container orchestration strategy for a microservices application using Kubernetes.",
            "How would you implement monitoring and alerting for a distributed system?",
            "Explain your approach to infrastructure as code using Terraform or similar tools.",
            "Design a disaster recovery plan for a cloud-native application.",
            "How would you implement blue-green deployment for a critical production service?"
        };
        
        String language = primaryTech.contains("python") ? "python" : 
                         primaryTech.contains("bash") || primaryTech.contains("shell") ? "bash" : "yaml";
        
        return createQuestionMap(questions[(int)(Math.random() * questions.length)], "system-design", "Hard", 900, false, language);
    }
    
    /**
     * Generate data science questions
     */
    private Map<String, Object> generateDataScienceQuestion(String primaryTech) {
        String[] questions = {
            "Explain how you would approach building a recommendation system for an e-commerce platform.",
            "Design an A/B testing framework for evaluating machine learning model performance.",
            "How would you handle class imbalance in a binary classification problem?",
            "Implement a data pipeline for processing streaming data from IoT devices.",
            "Explain your approach to feature engineering for a time series forecasting model."
        };
        
        String language = primaryTech.contains("python") ? "python" : 
                         primaryTech.contains("r") ? "r" : 
                         primaryTech.contains("sql") ? "sql" : "python";
        
        return createQuestionMap(questions[(int)(Math.random() * questions.length)], "technical", "Medium", 600, true, language);
    }
    
    /**
     * Generate mobile development questions
     */
    private Map<String, Object> generateMobileQuestion(String primaryTech) {
        String[] questions = {
            "How would you implement offline synchronization for a mobile application?",
            "Design a mobile app architecture that works efficiently on both iOS and Android.",
            "Explain your approach to handling different screen sizes and orientations.",
            "How would you implement push notifications with deep linking functionality?",
            "Design a caching strategy for a mobile app that works with intermittent connectivity."
        };
        
        String language = primaryTech.contains("swift") || primaryTech.contains("ios") ? "swift" : 
                         primaryTech.contains("kotlin") || primaryTech.contains("android") ? "kotlin" : 
                         primaryTech.contains("flutter") || primaryTech.contains("dart") ? "dart" : 
                         primaryTech.contains("react") ? "javascript" : "java";
        
        return createQuestionMap(questions[(int)(Math.random() * questions.length)], "technical", "Medium", 540, true, language);
    }
    
    /**
     * Create a standardized question map
     */
    private Map<String, Object> createQuestionMap(String text, String type, String difficulty, 
                                                   int estimatedSeconds, boolean codingRequired, String language) {
        return Map.of(
            "text", text,
            "type", type,
            "difficulty", difficulty,
            "estimatedSeconds", estimatedSeconds,
            "coding_required", codingRequired,
            "programming_language", language,
            "category", type,
            "id", UUID.randomUUID().toString()
        );
    }
    
    /**
     * Get default fallback question
     */
    private Map<String, Object> getDefaultQuestion() {
        return Map.of(
            "id", 1,
            "text", "Tell me about a challenging technical problem you've solved recently and how you approached it.",
            "type", "behavioral",
            "difficulty", "Medium",
            "estimatedSeconds", 300,
            "coding_required", false,
            "programming_language", "none",
            "category", "behavioral"
        );
    }
    
    /**
     * Determine appropriate programming language based on question type and session technologies
     */
    private String determineProgrammingLanguage(String questionType, List<String> technologies) {
        // If it's not a coding question, return none
        if (!"coding".equalsIgnoreCase(questionType) && !"technical".equalsIgnoreCase(questionType)) {
            return "none";
        }
        
        // If no technologies specified, default to java
        if (technologies == null || technologies.isEmpty()) {
            return "java";
        }
        
        // Get the first technology and map it to appropriate language
        String primaryTech = technologies.get(0).toLowerCase();
        
        if (primaryTech.contains("javascript") || primaryTech.contains("node") || primaryTech.contains("react")) {
            return "javascript";
        } else if (primaryTech.contains("typescript") || primaryTech.contains("angular")) {
            return "typescript";
        } else if (primaryTech.contains("python") || primaryTech.contains("django") || primaryTech.contains("flask")) {
            return "python";
        } else if (primaryTech.contains("java") || primaryTech.contains("spring")) {
            return "java";
        } else if (primaryTech.contains("c#") || primaryTech.contains("csharp") || primaryTech.contains(".net")) {
            return "csharp";
        } else if (primaryTech.contains("php")) {
            return "php";
        } else if (primaryTech.contains("go") || primaryTech.contains("golang")) {
            return "go";
        } else if (primaryTech.contains("rust")) {
            return "rust";
        } else if (primaryTech.contains("swift") || primaryTech.contains("ios")) {
            return "swift";
        } else if (primaryTech.contains("kotlin") || primaryTech.contains("android")) {
            return "kotlin";
        } else if (primaryTech.contains("c++") || primaryTech.contains("cpp")) {
            return "cpp";
        } else {
            // Default fallback
            return "java";
        }
    }
}
