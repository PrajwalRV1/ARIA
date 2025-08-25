package com.company.interview.controller;

import com.company.interview.client.AdaptiveEngineClient;
import com.company.interview.client.JobAnalysisClient;
import com.company.interview.dto.InterviewScheduleRequest;
import com.company.interview.dto.InterviewSessionResponse;
import com.company.interview.service.InterviewOrchestrationService;
import com.company.interview.service.MeetingRoomService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Interview Orchestration
 * Handles AI-driven adaptive interview sessions with real-time WebRTC integration
 */
@RestController
@RequestMapping("/api/interview")
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://localhost:4200", "http://localhost:3000", "https://localhost:3000"}, allowCredentials = "true")
public class InterviewOrchestratorController {

    @Autowired
    private InterviewOrchestrationService orchestrationService;

    @Autowired
    private MeetingRoomService meetingRoomService;

    @Autowired
    private JobAnalysisClient jobAnalysisClient;

    @Autowired
    private AdaptiveEngineClient adaptiveEngineClient;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "interview-orchestrator",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Schedule a new interview session with session token generation
     */
    @PostMapping("/schedule")
    public ResponseEntity<Map<String, Object>> scheduleInterview(
            @Valid @RequestBody InterviewScheduleRequest request) {
        try {
            // Get current server time with proper timezone handling
            ZonedDateTime currentZonedTime = ZonedDateTime.now(ZoneId.systemDefault());
            LocalDateTime currentTime = currentZonedTime.toLocalDateTime();
            LocalDateTime scheduledTime = request.getScheduledStartTime();
            
            log.info("📅 ===== INTERVIEW SCHEDULING REQUEST =====");
            log.info("👤 Candidate: {} (ID: {})", request.getCandidateName(), request.getCandidateId());
            log.info("👥 Recruiter: {} (ID: {})", request.getRecruiterName(), request.getRecruiterId());
            log.info("🕐 Current server time: {}", currentTime);
            log.info("🕐 Current zoned time: {}", currentZonedTime);
            log.info("🕐 Requested start time: {}", scheduledTime);
            log.info("⏱️  Time difference: {} minutes", java.time.Duration.between(currentTime, scheduledTime).toMinutes());
            log.info("🌍 Server timezone: {}", ZoneId.systemDefault());
            log.info("🌍 JVM timezone: {}", java.util.TimeZone.getDefault().getID());
            log.info("✅ Is future time: {}", scheduledTime.isAfter(currentTime));
            log.info("============================================");
            
            // Custom timezone-aware validation - require at least 2 minutes in the future
            if (!scheduledTime.isAfter(currentTime.plusMinutes(2))) {
                String message = String.format(
                    "Interview must be scheduled at least 2 minutes in the future. Current time: %s, Requested time: %s", 
                    currentTime, scheduledTime
                );
                log.error("⚠️ Validation failed: {}", message);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "error", "Validation failed",
                                "message", message,
                                "timestamp", System.currentTimeMillis()
                        ));
            }

            // Create interview session
            InterviewSessionResponse session = orchestrationService.scheduleInterview(request);

            // Create response with session tokens for all participants
            Map<String, Object> response = Map.of(
                    "sessionId", session.getSessionId(),
                    "status", session.getStatus(),
                    "meetingLink", session.getMeetingLink(),
                    "webrtcRoomId", session.getWebrtcRoomId(),
                    "scheduledDateTime", session.getScheduledStartTime(),
                    "candidateInfo", Map.of(
                            "candidateId", request.getCandidateId(),
                            "candidateName", request.getCandidateName(),
                            "candidateEmail", request.getCandidateEmail()
                    ),
                    "recruiterInfo", Map.of(
                            "recruiterId", request.getRecruiterId(),
                            "recruiterName", request.getRecruiterName(),
                            "recruiterEmail", request.getRecruiterEmail()
                    ),
                    "position", request.getPosition(),
                    "technologies", request.getTechnologies(),
                    "sessionTokens", Map.of(
                            "note", "Session tokens should be generated via /api/sessions/login endpoint",
                            "loginEndpoint", "/api/sessions/login",
                            "participants", List.of(
                                    Map.of("userType", "recruiter", "userId", request.getRecruiterId()),
                                    Map.of("userType", "candidate", "userId", request.getCandidateId()),
                                    Map.of("userType", "ai_avatar", "userId", "aria_ai")
                            )
                    )
            );

            log.info("Successfully scheduled interview session: {}", session.getSessionId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to schedule interview: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to schedule interview",
                            "message", e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                    ));
        }
    }

    /**
     * Get interview session details
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<InterviewSessionResponse> getInterviewSession(
            @PathVariable String sessionId) {
        try {
            log.info("Fetching interview session: {}", sessionId);

            InterviewSessionResponse session = orchestrationService.getInterviewSession(sessionId);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(session);

        } catch (Exception e) {
            log.error("Failed to fetch interview session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Start an interview session
     */
    @PostMapping("/session/{sessionId}/start")
    public ResponseEntity<InterviewSessionResponse> startInterview(
            @PathVariable String sessionId) {
        try {
            log.info("Starting interview session: {}", sessionId);

            InterviewSessionResponse session = orchestrationService.startInterview(sessionId);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(session);

        } catch (Exception e) {
            log.error("Failed to start interview session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * End an interview session
     */
    @PostMapping("/session/{sessionId}/end")
    public ResponseEntity<InterviewSessionResponse> endInterview(
            @PathVariable String sessionId) {
        try {
            log.info("Ending interview session: {}", sessionId);

            InterviewSessionResponse session = orchestrationService.endInterview(sessionId);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(session);

        } catch (Exception e) {
            log.error("Failed to end interview session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get current question for interview session
     */
    @GetMapping("/session/{sessionId}/current-question")
    public ResponseEntity<Map<String, Object>> getCurrentQuestion(
            @PathVariable String sessionId) {
        try {
            log.debug("Fetching current question for session: {}", sessionId);

            Map<String, Object> question = orchestrationService.getCurrentQuestion(sessionId);
            if (question == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(question);

        } catch (Exception e) {
            log.error("Failed to fetch current question for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Submit response for current question
     */
    @PostMapping("/session/{sessionId}/response")
    public ResponseEntity<Map<String, Object>> submitResponse(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> response) {
        try {
            log.debug("Processing response for session: {}", sessionId);

            Map<String, Object> result = orchestrationService.processResponse(sessionId, response);
            if (result == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to process response for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create a Jitsi Meet meeting room
     */
    @PostMapping("/meeting/create")
    public ResponseEntity<Map<String, String>> createMeetingRoom(
            @RequestBody Map<String, Object> request) {
        try {
            String sessionId = (String) request.get("sessionId");
            String candidateName = (String) request.get("candidateName");

            log.info("Creating meeting room for session: {} with candidate: {}", sessionId, candidateName);

            Map<String, String> roomInfo = meetingRoomService.createRoom(sessionId, candidateName);

            return ResponseEntity.ok(roomInfo);

        } catch (Exception e) {
            log.error("Failed to create meeting room: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create meeting room"));
        }
    }

    /**
     * Share meeting link with participants
     */
    @PostMapping("/meeting/share")
    public ResponseEntity<Map<String, String>> shareMeetingLink(
            @RequestBody Map<String, Object> request) {
        try {
            String sessionId = (String) request.get("sessionId");
            String meetingLink = (String) request.get("meetingLink");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> participants = (List<Map<String, String>>) request.get("participants");

            log.info("Sharing meeting link for session: {} with {} participants", sessionId, participants.size());

            boolean success = meetingRoomService.shareMeetingLink(sessionId, meetingLink, participants);

            if (success) {
                return ResponseEntity.ok(Map.of("status", "success", "message", "Meeting link shared successfully"));
            } else {
                String errorMessage = "Failed to share meeting link. This is usually caused by the email service being unavailable. Please ensure the user-management service is running on port 8080.";
                log.error("Meeting link sharing failed for session {}: {}", sessionId, errorMessage);

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("status", "error", "message", errorMessage));
            }

        } catch (Exception e) {
            log.error("Failed to share meeting link for session {}: {}", request.get("sessionId"), e.getMessage(), e);

            String errorMessage = "Failed to share meeting link due to server error: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", errorMessage));
        }
    }

    /**
     * Get interview results and analytics
     */
    @GetMapping("/session/{sessionId}/results")
    public ResponseEntity<Map<String, Object>> getInterviewResults(
            @PathVariable String sessionId) {
        try {
            log.info("Fetching interview results for session: {}", sessionId);

            Map<String, Object> results = orchestrationService.getInterviewResults(sessionId);
            if (results == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("Failed to fetch interview results for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Analyze job description for interview planning
     */
    @PostMapping("/analyze-job")
    public ResponseEntity<Map<String, Object>> analyzeJob(
            @Valid @RequestBody Map<String, String> jobData) {
        try {
            log.info("Analyzing job description for role: {}", jobData.get("jobRole"));

            JobAnalysisClient.JobAnalysisRequest analysisRequest = new JobAnalysisClient.JobAnalysisRequest();
            analysisRequest.setJobDescription(jobData.get("jobDescription"));
            analysisRequest.setKeyResponsibilities(jobData.get("keyResponsibilities"));
            analysisRequest.setJobRole(jobData.get("jobRole"));
            analysisRequest.setCompanyContext(jobData.get("companyContext"));

            Optional<JobAnalysisClient.JobAnalysisResponse> analysisResult =
                    jobAnalysisClient.analyzeJob(analysisRequest);

            if (analysisResult.isPresent()) {
                JobAnalysisClient.JobAnalysisResponse analysis = analysisResult.get();

                Map<String, Object> response = Map.of(
                        "success", true,
                        "jobAnalysis", Map.of(
                                "keyCompetencies", analysis.getKeyCompetencies(),
                                "technicalSkills", analysis.getTechnicalSkills(),
                                "priorityTechnicalSkills", analysis.getPriorityTechnicalSkills(),
                                "softSkills", analysis.getSoftSkills(),
                                "suggestedDuration", analysis.getSuggestedInterviewDuration(),
                                "confidenceScore", analysis.getConfidenceScore(),
                                "difficultyAdjustment", analysis.getDifficultyAdjustment(),
                                "questionCategoryWeights", analysis.getQuestionCategoryWeights()
                        ),
                        "recommendations", Map.of(
                                "estimatedQuestions", "10-15 questions",
                                "focusAreas", analysis.getKeyCompetencies(),
                                "suggestedInterviewType", "ADAPTIVE_AI"
                        ),
                        "timestamp", System.currentTimeMillis()
                );

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "success", false,
                                "error", "Job analysis service unavailable",
                                "message", "Please try again later or proceed without job analysis",
                                "timestamp", System.currentTimeMillis()
                        ));
            }

        } catch (Exception e) {
            log.error("Failed to analyze job description: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Job analysis failed",
                            "message", e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                    ));
        }
    }

    /**
     * Get interview preview based on job analysis
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> getInterviewPreview(
            @Valid @RequestBody Map<String, String> jobData) {
        try {
            log.info("Generating interview preview for role: {}", jobData.get("jobRole"));

            JobAnalysisClient.JobAnalysisRequest analysisRequest = new JobAnalysisClient.JobAnalysisRequest();
            analysisRequest.setJobDescription(jobData.get("jobDescription"));
            analysisRequest.setKeyResponsibilities(jobData.get("keyResponsibilities"));
            analysisRequest.setJobRole(jobData.get("jobRole"));
            analysisRequest.setCompanyContext(jobData.get("companyContext"));

            Optional<JobAnalysisClient.InterviewPreviewResponse> previewResult =
                    jobAnalysisClient.getInterviewPreview(analysisRequest);

            if (previewResult.isPresent()) {
                JobAnalysisClient.InterviewPreviewResponse preview = previewResult.get();

                Map<String, Object> response = Map.of(
                        "success", true,
                        "preview", Map.of(
                                "estimatedQuestions", preview.getEstimatedQuestions(),
                                "questionDistribution", preview.getQuestionDistribution(),
                                "estimatedDuration", preview.getEstimatedDuration(),
                                "difficultyProgression", preview.getDifficultyProgression(),
                                "skillCoverage", preview.getSkillCoverage(),
                                "recommendedInitialTheta", preview.getRecommendedInitialTheta()
                        ),
                        "timestamp", System.currentTimeMillis()
                );

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "success", false,
                                "error", "Preview generation failed",
                                "message", "Unable to generate interview preview",
                                "timestamp", System.currentTimeMillis()
                        ));
            }

        } catch (Exception e) {
            log.error("Failed to generate interview preview: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Preview generation failed",
                            "message", e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                    ));
        }
    }

    /**
     * Handle validation errors for @Valid annotated request bodies
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        log.error("Validation failed for interview schedule request");

        StringBuilder errorMessages = new StringBuilder();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            log.error("Field '{}': {}", error.getField(), error.getDefaultMessage());
            errorMessages.append(error.getField()).append(": ").append(error.getDefaultMessage()).append("; ");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Validation failed",
                        "message", errorMessages.toString(),
                        "timestamp", System.currentTimeMillis()
                ));
    }

    /**
     * Handle JSON parsing errors
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonParsingException(
            HttpMessageNotReadableException ex) {

        log.error("JSON parsing failed: {}", ex.getMessage());

        String message = "Invalid JSON format or data type mismatch";
        if (ex.getCause() != null) {
            message += ": " + ex.getCause().getMessage();
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "JSON parsing failed",
                        "message", message,
                        "timestamp", System.currentTimeMillis()
                ));
    }
}
