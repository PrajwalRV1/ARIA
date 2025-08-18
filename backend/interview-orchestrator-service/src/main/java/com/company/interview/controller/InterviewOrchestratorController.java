package com.company.interview.controller;

import com.company.interview.dto.*;
import com.company.interview.service.InterviewOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Interview Orchestration
 * Handles AI-driven adaptive interview sessions with real-time WebRTC integration
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@Validated
@Slf4j
public class InterviewOrchestratorController {
    
    private final InterviewOrchestratorService interviewService;
    
    /**
     * Schedule a new interview session
     * POST /api/interview/schedule
     */
    @PostMapping("/schedule")
    public ResponseEntity<InterviewSessionResponse> scheduleInterview(
            @Valid @RequestBody InterviewScheduleRequest request) {
        
        log.info("Scheduling interview for candidate {} with recruiter {}", 
                request.getCandidateId(), request.getRecruiterId());
        
        try {
            InterviewSessionResponse response = interviewService.scheduleInterview(request);
            log.info("Interview scheduled successfully with session ID: {}", response.getSessionId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Failed to schedule interview: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Start an interview session
     * POST /api/interview/{sessionId}/start
     */
    @PostMapping("/{sessionId}/start")
    public ResponseEntity<InterviewStartResponse> startInterview(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) Map<String, Object> startOptions) {
        
        log.info("Starting interview session: {}", sessionId);
        
        try {
            InterviewStartResponse response = interviewService.startInterview(sessionId, startOptions);
            log.info("Interview started successfully for session: {}", sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to start interview {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process candidate response and get next question
     * POST /api/interview/{sessionId}/response
     */
    @PostMapping("/{sessionId}/response")
    public ResponseEntity<InterviewResponseProcessResult> processResponse(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CandidateResponseRequest request) {
        
        log.info("Processing response for session: {}, question: {}", 
                sessionId, request.getQuestionId());
        
        try {
            InterviewResponseProcessResult result = interviewService.processResponse(sessionId, request);
            log.info("Response processed for session: {}, next action: {}", 
                    sessionId, result.getNextAction());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to process response for session {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * End an interview session
     * POST /api/interview/{sessionId}/end
     */
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<InterviewEndResponse> endInterview(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) Map<String, Object> endOptions) {
        
        log.info("Ending interview session: {}", sessionId);
        
        try {
            InterviewEndResponse response = interviewService.endInterview(sessionId, endOptions);
            log.info("Interview ended successfully for session: {}", sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to end interview {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get interview session details
     * GET /api/interview/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<InterviewSessionResponse> getInterviewSession(
            @PathVariable UUID sessionId) {
        
        log.info("Retrieving interview session: {}", sessionId);
        
        try {
            InterviewSessionResponse response = interviewService.getInterviewSession(sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to retrieve interview session {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Pause an ongoing interview
     * POST /api/interview/{sessionId}/pause
     */
    @PostMapping("/{sessionId}/pause")
    public ResponseEntity<Map<String, Object>> pauseInterview(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) Map<String, String> pauseReason) {
        
        log.info("Pausing interview session: {}", sessionId);
        
        try {
            interviewService.pauseInterview(sessionId, pauseReason);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Interview paused successfully",
                "sessionId", sessionId.toString()
            ));
        } catch (Exception e) {
            log.error("Failed to pause interview {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Resume a paused interview
     * POST /api/interview/{sessionId}/resume
     */
    @PostMapping("/{sessionId}/resume")
    public ResponseEntity<Map<String, Object>> resumeInterview(
            @PathVariable UUID sessionId) {
        
        log.info("Resuming interview session: {}", sessionId);
        
        try {
            interviewService.resumeInterview(sessionId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Interview resumed successfully",
                "sessionId", sessionId.toString()
            ));
        } catch (Exception e) {
            log.error("Failed to resume interview {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get current question for a session
     * GET /api/interview/{sessionId}/current-question
     */
    @GetMapping("/{sessionId}/current-question")
    public ResponseEntity<CurrentQuestionResponse> getCurrentQuestion(
            @PathVariable UUID sessionId) {
        
        log.info("Getting current question for session: {}", sessionId);
        
        try {
            CurrentQuestionResponse response = interviewService.getCurrentQuestion(sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get current question for session {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get interview analytics and results
     * GET /api/interview/{sessionId}/analytics
     */
    @GetMapping("/{sessionId}/analytics")
    public ResponseEntity<InterviewAnalyticsResponse> getInterviewAnalytics(
            @PathVariable UUID sessionId) {
        
        log.info("Getting analytics for interview session: {}", sessionId);
        
        try {
            InterviewAnalyticsResponse response = interviewService.getInterviewAnalytics(sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get analytics for session {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Update session configuration during interview
     * PUT /api/interview/{sessionId}/config
     */
    @PutMapping("/{sessionId}/config")
    public ResponseEntity<Map<String, Object>> updateSessionConfig(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, Object> configUpdates) {
        
        log.info("Updating configuration for session: {}", sessionId);
        
        try {
            interviewService.updateSessionConfig(sessionId, configUpdates);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Session configuration updated",
                "sessionId", sessionId.toString()
            ));
        } catch (Exception e) {
            log.error("Failed to update config for session {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Cancel a scheduled interview
     * DELETE /api/interview/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> cancelInterview(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) Map<String, String> cancellationReason) {
        
        log.info("Cancelling interview session: {}", sessionId);
        
        try {
            interviewService.cancelInterview(sessionId, cancellationReason);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Interview cancelled successfully",
                "sessionId", sessionId.toString()
            ));
        } catch (Exception e) {
            log.error("Failed to cancel interview {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
}
