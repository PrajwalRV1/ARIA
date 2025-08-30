package com.company.user.controller;

import com.company.user.dto.InterviewRoundDto;
import com.company.user.model.InterviewRoundStatus;
import com.company.user.model.InterviewRoundType;
import com.company.user.service.InterviewRoundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for managing interview rounds.
 * Provides endpoints for CRUD operations, status updates, and round progression.
 */
@RestController
@RequestMapping("/api/interview-rounds")
@Tag(name = "Interview Rounds", description = "Interview Round Management API")
@CrossOrigin(origins = "*")
public class InterviewRoundController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewRoundController.class);

    private final InterviewRoundService interviewRoundService;

    @Autowired
    public InterviewRoundController(InterviewRoundService interviewRoundService) {
        this.interviewRoundService = interviewRoundService;
    }

    /**
     * Create a new interview round
     */
    @PostMapping
    @Operation(summary = "Create interview round", description = "Create a new interview round for a candidate")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Interview round created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Candidate not found"),
        @ApiResponse(responseCode = "409", description = "Interview round already exists for this candidate and round type")
    })
    public ResponseEntity<InterviewRoundDto> createInterviewRound(
            @Valid @RequestBody InterviewRoundDto roundDto) {
        
        logger.info("Creating interview round for candidate {} with type {}", 
                   roundDto.getCandidateId(), roundDto.getRoundType());
        
        try {
            InterviewRoundDto createdRound = interviewRoundService.createInterviewRound(roundDto);
            return new ResponseEntity<>(createdRound, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to create interview round: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
    }

    /**
     * Get interview round by ID
     */
    @GetMapping("/{roundId}")
    @Operation(summary = "Get interview round", description = "Get interview round by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Interview round found"),
        @ApiResponse(responseCode = "404", description = "Interview round not found")
    })
    public ResponseEntity<InterviewRoundDto> getInterviewRound(
            @Parameter(description = "Interview round ID") @PathVariable Long roundId) {
        
        logger.debug("Fetching interview round with ID: {}", roundId);
        
        InterviewRoundDto round = interviewRoundService.getInterviewRoundById(roundId);
        return ResponseEntity.ok(round);
    }

    /**
     * Get all interview rounds for a candidate
     */
    @GetMapping("/candidate/{candidateId}")
    @Operation(summary = "Get candidate interview rounds", description = "Get all interview rounds for a specific candidate")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Interview rounds found"),
        @ApiResponse(responseCode = "404", description = "Candidate not found")
    })
    public ResponseEntity<List<InterviewRoundDto>> getCandidateInterviewRounds(
            @Parameter(description = "Candidate ID") @PathVariable Long candidateId) {
        
        logger.debug("Fetching interview rounds for candidate: {}", candidateId);
        
        List<InterviewRoundDto> rounds = interviewRoundService.getCandidateInterviewRounds(candidateId);
        return ResponseEntity.ok(rounds);
    }

    /**
     * Update an existing interview round
     */
    @PutMapping("/{roundId}")
    @Operation(summary = "Update interview round", description = "Update an existing interview round")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Interview round updated successfully"),
        @ApiResponse(responseCode = "404", description = "Interview round not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<InterviewRoundDto> updateInterviewRound(
            @Parameter(description = "Interview round ID") @PathVariable Long roundId,
            @Valid @RequestBody InterviewRoundDto roundDto) {
        
        logger.info("Updating interview round: {}", roundId);
        
        InterviewRoundDto updatedRound = interviewRoundService.updateInterviewRound(roundId, roundDto);
        return ResponseEntity.ok(updatedRound);
    }

    /**
     * Update interview round status
     */
    @PatchMapping("/{roundId}/status")
    @Operation(summary = "Update round status", description = "Update the status of an interview round")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "404", description = "Interview round not found"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition")
    })
    public ResponseEntity<InterviewRoundDto> updateRoundStatus(
            @Parameter(description = "Interview round ID") @PathVariable Long roundId,
            @Parameter(description = "New status") @RequestParam InterviewRoundStatus status) {
        
        logger.info("Updating status of interview round {} to {}", roundId, status);
        
        try {
            InterviewRoundDto updatedRound = interviewRoundService.updateRoundStatus(roundId, status);
            return ResponseEntity.ok(updatedRound);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid status transition: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Delete an interview round
     */
    @DeleteMapping("/{roundId}")
    @Operation(summary = "Delete interview round", description = "Delete an interview round")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Interview round deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Interview round not found")
    })
    public ResponseEntity<Void> deleteInterviewRound(
            @Parameter(description = "Interview round ID") @PathVariable Long roundId) {
        
        logger.info("Deleting interview round: {}", roundId);
        
        interviewRoundService.deleteInterviewRound(roundId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Initialize all standard interview rounds for a candidate
     */
    @PostMapping("/candidate/{candidateId}/initialize")
    @Operation(summary = "Initialize standard rounds", description = "Initialize all standard interview rounds for a candidate")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Standard rounds initialized successfully"),
        @ApiResponse(responseCode = "404", description = "Candidate not found")
    })
    public ResponseEntity<List<InterviewRoundDto>> initializeStandardRounds(
            @Parameter(description = "Candidate ID") @PathVariable Long candidateId) {
        
        logger.info("Initializing standard interview rounds for candidate: {}", candidateId);
        
        List<InterviewRoundDto> initializedRounds = interviewRoundService.initializeStandardRounds(candidateId);
        return new ResponseEntity<>(initializedRounds, HttpStatus.CREATED);
    }

    /**
     * Get upcoming scheduled interviews
     */
    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming interviews", description = "Get all upcoming scheduled interviews")
    @ApiResponse(responseCode = "200", description = "Upcoming interviews retrieved successfully")
    public ResponseEntity<List<InterviewRoundDto>> getUpcomingScheduledInterviews() {
        
        logger.debug("Fetching upcoming scheduled interviews");
        
        List<InterviewRoundDto> upcomingRounds = interviewRoundService.getUpcomingScheduledInterviews();
        return ResponseEntity.ok(upcomingRounds);
    }

    /**
     * Get overdue interviews
     */
    @GetMapping("/overdue")
    @Operation(summary = "Get overdue interviews", description = "Get all overdue interviews")
    @ApiResponse(responseCode = "200", description = "Overdue interviews retrieved successfully")
    public ResponseEntity<List<InterviewRoundDto>> getOverdueInterviews() {
        
        logger.debug("Fetching overdue interviews");
        
        List<InterviewRoundDto> overdueRounds = interviewRoundService.getOverdueInterviews();
        return ResponseEntity.ok(overdueRounds);
    }

    /**
     * Get interviews by interviewer
     */
    @GetMapping("/interviewer/{interviewerEmail}")
    @Operation(summary = "Get interviews by interviewer", description = "Get all interviews assigned to a specific interviewer")
    @ApiResponse(responseCode = "200", description = "Interviews retrieved successfully")
    public ResponseEntity<List<InterviewRoundDto>> getInterviewsByInterviewer(
            @Parameter(description = "Interviewer email") @PathVariable String interviewerEmail) {
        
        logger.debug("Fetching interviews for interviewer: {}", interviewerEmail);
        
        List<InterviewRoundDto> interviewRounds = interviewRoundService.getInterviewsByInterviewer(interviewerEmail);
        return ResponseEntity.ok(interviewRounds);
    }

    /**
     * Get rounds needing review
     */
    @GetMapping("/review")
    @Operation(summary = "Get rounds needing review", description = "Get all interview rounds that need review")
    @ApiResponse(responseCode = "200", description = "Rounds needing review retrieved successfully")
    public ResponseEntity<List<InterviewRoundDto>> getRoundsNeedingReview() {
        
        logger.debug("Fetching rounds needing review");
        
        List<InterviewRoundDto> reviewRounds = interviewRoundService.getRoundsNeedingReview();
        return ResponseEntity.ok(reviewRounds);
    }

    /**
     * Get overall candidate status based on interview rounds
     */
    @GetMapping("/candidate/{candidateId}/status")
    @Operation(summary = "Get candidate overall status", description = "Compute overall candidate status from interview rounds")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidate status computed successfully"),
        @ApiResponse(responseCode = "404", description = "Candidate not found")
    })
    public ResponseEntity<Map<String, String>> getCandidateOverallStatus(
            @Parameter(description = "Candidate ID") @PathVariable Long candidateId) {
        
        logger.debug("Computing overall status for candidate: {}", candidateId);
        
        String overallStatus = interviewRoundService.computeOverallCandidateStatus(candidateId);
        Map<String, String> response = new HashMap<>();
        response.put("candidateId", candidateId.toString());
        response.put("overallStatus", overallStatus);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get available round types
     */
    @GetMapping("/round-types")
    @Operation(summary = "Get round types", description = "Get all available interview round types")
    @ApiResponse(responseCode = "200", description = "Round types retrieved successfully")
    public ResponseEntity<InterviewRoundType[]> getRoundTypes() {
        
        logger.debug("Fetching available round types");
        
        return ResponseEntity.ok(InterviewRoundType.values());
    }

    /**
     * Get available round statuses
     */
    @GetMapping("/round-statuses")
    @Operation(summary = "Get round statuses", description = "Get all available interview round statuses")
    @ApiResponse(responseCode = "200", description = "Round statuses retrieved successfully")
    public ResponseEntity<InterviewRoundStatus[]> getRoundStatuses() {
        
        logger.debug("Fetching available round statuses");
        
        return ResponseEntity.ok(InterviewRoundStatus.values());
    }

    /**
     * Bulk update round statuses
     */
    @PatchMapping("/bulk-status")
    @Operation(summary = "Bulk update round statuses", description = "Update status of multiple interview rounds")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statuses updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "One or more rounds not found")
    })
    public ResponseEntity<List<InterviewRoundDto>> bulkUpdateRoundStatuses(
            @RequestBody Map<String, Object> request) {
        
        logger.info("Bulk updating round statuses");
        
        @SuppressWarnings("unchecked")
        List<Long> roundIds = (List<Long>) request.get("roundIds");
        InterviewRoundStatus status = InterviewRoundStatus.valueOf((String) request.get("status"));
        
        List<InterviewRoundDto> updatedRounds = roundIds.stream()
                .map(roundId -> interviewRoundService.updateRoundStatus(roundId, status))
                .toList();
        
        return ResponseEntity.ok(updatedRounds);
    }

    /**
     * Get interview round statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get interview statistics", description = "Get interview round statistics")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    public ResponseEntity<Map<String, Object>> getInterviewStatistics() {
        
        logger.debug("Fetching interview round statistics");
        
        // This would typically involve calling repository methods to get counts
        // For now, returning a placeholder response structure
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("message", "Statistics endpoint - implementation pending");
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * Schedule an interview round
     */
    @PatchMapping("/{roundId}/schedule")
    @Operation(summary = "Schedule interview round", description = "Schedule an interview round with date and interviewer details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Interview scheduled successfully"),
        @ApiResponse(responseCode = "404", description = "Interview round not found"),
        @ApiResponse(responseCode = "400", description = "Invalid scheduling data")
    })
    public ResponseEntity<InterviewRoundDto> scheduleInterview(
            @Parameter(description = "Interview round ID") @PathVariable Long roundId,
            @RequestBody Map<String, Object> scheduleData) {
        
        logger.info("Scheduling interview round: {}", roundId);
        
        // Extract scheduling data and update the round
        InterviewRoundDto roundDto = interviewRoundService.getInterviewRoundById(roundId);
        
        // Update scheduling fields from the request data
        // This would involve parsing the scheduleData map and setting appropriate fields
        
        InterviewRoundDto updatedRound = interviewRoundService.updateInterviewRound(roundId, roundDto);
        
        // Update status to INTERVIEW_SCHEDULED if not already
        if (updatedRound.getStatus() == InterviewRoundStatus.NOT_STARTED) {
            updatedRound = interviewRoundService.updateRoundStatus(roundId, InterviewRoundStatus.INTERVIEW_SCHEDULED);
        }
        
        return ResponseEntity.ok(updatedRound);
    }
}
