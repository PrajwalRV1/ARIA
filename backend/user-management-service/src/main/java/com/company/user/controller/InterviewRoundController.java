package com.company.user.controller;

import com.company.user.dto.InterviewRoundDto;
import com.company.user.model.InterviewRoundStatus;
import com.company.user.model.InterviewRoundType;
import com.company.user.service.InterviewRoundService;
// Swagger annotations removed for build compatibility
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
public ResponseEntity<InterviewRoundDto> getInterviewRound(
        @PathVariable Long roundId) {
        
        logger.debug("Fetching interview round with ID: {}", roundId);
        
        InterviewRoundDto round = interviewRoundService.getInterviewRoundById(roundId);
        return ResponseEntity.ok(round);
    }

    /**
     * Get all interview rounds for a candidate
     */
@GetMapping("/candidate/{candidateId}")
public ResponseEntity<List<InterviewRoundDto>> getCandidateInterviewRounds(
        @PathVariable Long candidateId) {
        
        logger.debug("Fetching interview rounds for candidate: {}", candidateId);
        
        List<InterviewRoundDto> rounds = interviewRoundService.getCandidateInterviewRounds(candidateId);
        return ResponseEntity.ok(rounds);
    }

    /**
     * Update an existing interview round
     */
@PutMapping("/{roundId}")
public ResponseEntity<InterviewRoundDto> updateInterviewRound(
        @PathVariable Long roundId,
        @Valid @RequestBody InterviewRoundDto roundDto) {
        
        logger.info("Updating interview round: {}", roundId);
        
        InterviewRoundDto updatedRound = interviewRoundService.updateInterviewRound(roundId, roundDto);
        return ResponseEntity.ok(updatedRound);
    }

    /**
     * Update interview round status
     */
@PatchMapping("/{roundId}/status")
public ResponseEntity<InterviewRoundDto> updateRoundStatus(
        @PathVariable Long roundId,
        @RequestParam InterviewRoundStatus status) {
        
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
public ResponseEntity<Void> deleteInterviewRound(
        @PathVariable Long roundId) {
        
        logger.info("Deleting interview round: {}", roundId);
        
        interviewRoundService.deleteInterviewRound(roundId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Initialize all standard interview rounds for a candidate
     */
@PostMapping("/candidate/{candidateId}/initialize")
public ResponseEntity<List<InterviewRoundDto>> initializeStandardRounds(
        @PathVariable Long candidateId) {
        
        logger.info("Initializing standard interview rounds for candidate: {}", candidateId);
        
        List<InterviewRoundDto> initializedRounds = interviewRoundService.initializeStandardRounds(candidateId);
        return new ResponseEntity<>(initializedRounds, HttpStatus.CREATED);
    }

    /**
     * Get upcoming scheduled interviews
     */
@GetMapping("/upcoming")
public ResponseEntity<List<InterviewRoundDto>> getUpcomingScheduledInterviews() {
        
        logger.debug("Fetching upcoming scheduled interviews");
        
        List<InterviewRoundDto> upcomingRounds = interviewRoundService.getUpcomingScheduledInterviews();
        return ResponseEntity.ok(upcomingRounds);
    }

    /**
     * Get overdue interviews
     */
@GetMapping("/overdue")
public ResponseEntity<List<InterviewRoundDto>> getOverdueInterviews() {
        
        logger.debug("Fetching overdue interviews");
        
        List<InterviewRoundDto> overdueRounds = interviewRoundService.getOverdueInterviews();
        return ResponseEntity.ok(overdueRounds);
    }

    /**
     * Get interviews by interviewer
     */
@GetMapping("/interviewer/{interviewerEmail}")
public ResponseEntity<List<InterviewRoundDto>> getInterviewsByInterviewer(
        @PathVariable String interviewerEmail) {
        
        logger.debug("Fetching interviews for interviewer: {}", interviewerEmail);
        
        List<InterviewRoundDto> interviewRounds = interviewRoundService.getInterviewsByInterviewer(interviewerEmail);
        return ResponseEntity.ok(interviewRounds);
    }

    /**
     * Get rounds needing review
     */
@GetMapping("/review")
public ResponseEntity<List<InterviewRoundDto>> getRoundsNeedingReview() {
        
        logger.debug("Fetching rounds needing review");
        
        List<InterviewRoundDto> reviewRounds = interviewRoundService.getRoundsNeedingReview();
        return ResponseEntity.ok(reviewRounds);
    }

    /**
     * Get overall candidate status based on interview rounds
     */
@GetMapping("/candidate/{candidateId}/status")
public ResponseEntity<Map<String, String>> getCandidateOverallStatus(
        @PathVariable Long candidateId) {
        
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
public ResponseEntity<InterviewRoundType[]> getRoundTypes() {
        
        logger.debug("Fetching available round types");
        
        return ResponseEntity.ok(InterviewRoundType.values());
    }

    /**
     * Get available round statuses
     */
@GetMapping("/round-statuses")
public ResponseEntity<InterviewRoundStatus[]> getRoundStatuses() {
        
        logger.debug("Fetching available round statuses");
        
        return ResponseEntity.ok(InterviewRoundStatus.values());
    }

    /**
     * Bulk update round statuses
     */
@PatchMapping("/bulk-status")
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
public ResponseEntity<InterviewRoundDto> scheduleInterview(
        @PathVariable Long roundId,
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
