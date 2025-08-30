package com.company.user.service;

import com.company.user.dto.InterviewRoundDto;
import com.company.user.exception.ResourceNotFoundException;
import com.company.user.model.Candidate;
import com.company.user.model.InterviewRound;
import com.company.user.model.InterviewRoundStatus;
import com.company.user.model.InterviewRoundType;
import com.company.user.repository.CandidateRepository;
import com.company.user.repository.InterviewRoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for managing interview rounds.
 * Handles CRUD operations, status transitions, and overall candidate status computation.
 */
@Service
@Transactional
public class InterviewRoundService {

    private static final Logger logger = LoggerFactory.getLogger(InterviewRoundService.class);

    private final InterviewRoundRepository interviewRoundRepository;
    private final CandidateRepository candidateRepository;

    @Autowired
    public InterviewRoundService(InterviewRoundRepository interviewRoundRepository,
                               CandidateRepository candidateRepository) {
        this.interviewRoundRepository = interviewRoundRepository;
        this.candidateRepository = candidateRepository;
    }

    /**
     * Create a new interview round for a candidate
     */
    public InterviewRoundDto createInterviewRound(InterviewRoundDto roundDto) {
        logger.info("Creating interview round for candidate {} with type {}", 
                   roundDto.getCandidateId(), roundDto.getRoundType());

        // Validate candidate exists
        Candidate candidate = candidateRepository.findById(roundDto.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Candidate not found with ID: " + roundDto.getCandidateId()));

        // Check if round already exists for this candidate and type
        if (interviewRoundRepository.existsByCandidateIdAndRoundType(
                roundDto.getCandidateId(), roundDto.getRoundType())) {
            throw new IllegalArgumentException(
                "Interview round already exists for candidate " + roundDto.getCandidateId() + 
                " and round type " + roundDto.getRoundType());
        }

        // Create and save the interview round
        InterviewRound round = convertToEntity(roundDto);
        round.setCandidate(candidate);
        
        // Set defaults if not provided
        if (round.getRoundName() == null) {
            round.setRoundName(round.getRoundType().getDisplayName());
        }
        if (round.getRoundOrder() == null) {
            round.setRoundOrder(round.getRoundType().getOrder());
        }
        
        InterviewRound savedRound = interviewRoundRepository.save(round);
        
        // Update candidate's overall status
        updateCandidateOverallStatus(candidate.getId());
        
        logger.info("Created interview round {} for candidate {}", savedRound.getId(), candidate.getId());
        return convertToDto(savedRound);
    }

    /**
     * Get all interview rounds for a candidate
     */
    @Transactional(readOnly = true)
    public List<InterviewRoundDto> getCandidateInterviewRounds(Long candidateId) {
        logger.debug("Fetching interview rounds for candidate {}", candidateId);
        
        // Validate candidate exists
        if (!candidateRepository.existsById(candidateId)) {
            throw new ResourceNotFoundException("Candidate not found with ID: " + candidateId);
        }
        
        List<InterviewRound> rounds = interviewRoundRepository.findByCandidateIdOrderByRoundOrder(candidateId);
        return rounds.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get interview round by ID
     */
    @Transactional(readOnly = true)
    public InterviewRoundDto getInterviewRoundById(Long roundId) {
        logger.debug("Fetching interview round with ID {}", roundId);
        
        InterviewRound round = interviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview round not found with ID: " + roundId));
        
        return convertToDto(round);
    }

    /**
     * Update an existing interview round
     */
    public InterviewRoundDto updateInterviewRound(Long roundId, InterviewRoundDto roundDto) {
        logger.info("Updating interview round {}", roundId);
        
        InterviewRound existingRound = interviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview round not found with ID: " + roundId));

        // Update fields
        updateRoundFields(existingRound, roundDto);
        
        InterviewRound updatedRound = interviewRoundRepository.save(existingRound);
        
        // Update candidate's overall status if round status changed
        if (!Objects.equals(existingRound.getStatus(), roundDto.getStatus())) {
            updateCandidateOverallStatus(existingRound.getCandidate().getId());
        }
        
        logger.info("Updated interview round {} for candidate {}", 
                   updatedRound.getId(), updatedRound.getCandidate().getId());
        return convertToDto(updatedRound);
    }

    /**
     * Update only the status of an interview round
     */
    public InterviewRoundDto updateRoundStatus(Long roundId, InterviewRoundStatus newStatus) {
        logger.info("Updating status of interview round {} to {}", roundId, newStatus);
        
        InterviewRound round = interviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview round not found with ID: " + roundId));

        InterviewRoundStatus oldStatus = round.getStatus();
        
        // Validate status transition
        if (!isValidStatusTransition(oldStatus, newStatus)) {
            throw new IllegalArgumentException(
                "Invalid status transition from " + oldStatus + " to " + newStatus);
        }
        
        round.setStatus(newStatus);
        
        // Update timing fields based on status
        updateTimingFieldsForStatus(round, newStatus);
        
        InterviewRound updatedRound = interviewRoundRepository.save(round);
        
        // Update candidate's overall status
        updateCandidateOverallStatus(round.getCandidate().getId());
        
        logger.info("Updated interview round {} status from {} to {}", 
                   roundId, oldStatus, newStatus);
        return convertToDto(updatedRound);
    }

    /**
     * Delete an interview round
     */
    public void deleteInterviewRound(Long roundId) {
        logger.info("Deleting interview round {}", roundId);
        
        InterviewRound round = interviewRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview round not found with ID: " + roundId));
        
        Long candidateId = round.getCandidate().getId();
        interviewRoundRepository.delete(round);
        
        // Update candidate's overall status
        updateCandidateOverallStatus(candidateId);
        
        logger.info("Deleted interview round {} for candidate {}", roundId, candidateId);
    }

    /**
     * Initialize all standard interview rounds for a candidate
     */
    public List<InterviewRoundDto> initializeStandardRounds(Long candidateId) {
        logger.info("Initializing standard interview rounds for candidate {}", candidateId);
        
        // Validate candidate exists
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with ID: " + candidateId));

        List<InterviewRoundDto> createdRounds = new ArrayList<>();
        
        // Create rounds for all standard types
        for (InterviewRoundType roundType : InterviewRoundType.values()) {
            // Check if round already exists
            if (!interviewRoundRepository.existsByCandidateIdAndRoundType(candidateId, roundType)) {
                InterviewRoundDto roundDto = new InterviewRoundDto(candidateId, roundType);
                createdRounds.add(createInterviewRound(roundDto));
            }
        }
        
        logger.info("Initialized {} standard interview rounds for candidate {}", 
                   createdRounds.size(), candidateId);
        return createdRounds;
    }

    /**
     * Get upcoming scheduled interviews
     */
    @Transactional(readOnly = true)
    public List<InterviewRoundDto> getUpcomingScheduledInterviews() {
        List<InterviewRound> upcomingRounds = interviewRoundRepository
                .findUpcomingScheduledInterviews(InterviewRoundStatus.INTERVIEW_SCHEDULED, LocalDateTime.now());
        
        return upcomingRounds.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get overdue interviews
     */
    @Transactional(readOnly = true)
    public List<InterviewRoundDto> getOverdueInterviews() {
        List<InterviewRound> overdueRounds = interviewRoundRepository
                .findOverdueInterviews(InterviewRoundStatus.INTERVIEW_SCHEDULED, LocalDateTime.now());
        
        return overdueRounds.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get interview rounds by interviewer
     */
    @Transactional(readOnly = true)
    public List<InterviewRoundDto> getInterviewsByInterviewer(String interviewerEmail) {
        List<InterviewRound> rounds = interviewRoundRepository.findByInterviewerEmail(interviewerEmail);
        
        return rounds.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get interview rounds that need review
     */
    @Transactional(readOnly = true)
    public List<InterviewRoundDto> getRoundsNeedingReview() {
        List<InterviewRound> rounds = interviewRoundRepository
                .findByStatusOrderByUpdatedAtAsc(InterviewRoundStatus.UNDER_REVIEW);
        
        return rounds.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Compute overall candidate status from all interview rounds
     */
    public String computeOverallCandidateStatus(Long candidateId) {
        List<InterviewRound> rounds = interviewRoundRepository.findByCandidateIdOrderByRoundOrder(candidateId);
        
        if (rounds.isEmpty()) {
            return "Not Started";
        }
        
        // Count rounds by status
        Map<InterviewRoundStatus, Long> statusCounts = rounds.stream()
                .collect(Collectors.groupingBy(InterviewRound::getStatus, Collectors.counting()));
        
        // Check for terminal statuses first (highest priority)
        if (statusCounts.containsKey(InterviewRoundStatus.REJECTED)) {
            return "Rejected";
        }
        if (statusCounts.containsKey(InterviewRoundStatus.WITHDRAWN)) {
            return "Withdrawn";
        }
        
        // Check if all rounds are completed successfully
        long totalRounds = rounds.size();
        long completedRounds = statusCounts.getOrDefault(InterviewRoundStatus.COMPLETED, 0L);
        
        if (completedRounds == totalRounds) {
            return "Process Complete";
        }
        
        // Check for active statuses
        if (statusCounts.containsKey(InterviewRoundStatus.IN_PROGRESS)) {
            return "Interview In Progress";
        }
        if (statusCounts.containsKey(InterviewRoundStatus.INTERVIEW_SCHEDULED)) {
            return "Interview Scheduled";
        }
        if (statusCounts.containsKey(InterviewRoundStatus.UNDER_REVIEW)) {
            return "Under Review";
        }
        if (statusCounts.containsKey(InterviewRoundStatus.ON_HOLD)) {
            return "On Hold";
        }
        
        // If we have completed rounds but not all, show progress
        if (completedRounds > 0) {
            return "In Progress";
        }
        
        return "Not Started";
    }

    /**
     * Update candidate's overall status based on interview rounds
     */
    private void updateCandidateOverallStatus(Long candidateId) {
        String overallStatus = computeOverallCandidateStatus(candidateId);
        
        // Update candidate entity through repository
        candidateRepository.findById(candidateId).ifPresent(candidate -> {
            candidate.setOverallStatus(overallStatus);
            candidateRepository.save(candidate);
            logger.debug("Updated candidate {} overall status to {}", candidateId, overallStatus);
        });
    }

    /**
     * Validate status transition rules
     */
    private boolean isValidStatusTransition(InterviewRoundStatus from, InterviewRoundStatus to) {
        if (from == to) return true;
        
        return switch (from) {
            case NOT_STARTED -> to == InterviewRoundStatus.INTERVIEW_SCHEDULED || 
                              to == InterviewRoundStatus.ON_HOLD ||
                              to == InterviewRoundStatus.WITHDRAWN;
            
            case INTERVIEW_SCHEDULED -> to != InterviewRoundStatus.NOT_STARTED;
            
            case IN_PROGRESS -> to == InterviewRoundStatus.UNDER_REVIEW || 
                              to == InterviewRoundStatus.COMPLETED ||
                              to == InterviewRoundStatus.ON_HOLD ||
                              to == InterviewRoundStatus.WITHDRAWN;
            
            case UNDER_REVIEW -> to == InterviewRoundStatus.COMPLETED || 
                               to == InterviewRoundStatus.REJECTED ||
                               to == InterviewRoundStatus.ON_HOLD;
            
            case ON_HOLD -> to != InterviewRoundStatus.NOT_STARTED;
            
            case COMPLETED, REJECTED, WITHDRAWN -> false; // Terminal states
        };
    }

    /**
     * Update timing fields based on status change
     */
    private void updateTimingFieldsForStatus(InterviewRound round, InterviewRoundStatus status) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (status) {
            case IN_PROGRESS:
                if (round.getStartTime() == null) {
                    round.setStartTime(now);
                }
                break;
            case COMPLETED:
            case REJECTED:
                if (round.getEndTime() == null) {
                    round.setEndTime(now);
                }
                if (round.getStartTime() != null && round.getDurationMinutes() == null) {
                    long duration = java.time.Duration.between(round.getStartTime(), round.getEndTime()).toMinutes();
                    round.setDurationMinutes((int) duration);
                }
                break;
        }
    }

    /**
     * Update round fields from DTO
     */
    private void updateRoundFields(InterviewRound round, InterviewRoundDto dto) {
        if (dto.getRoundName() != null) {
            round.setRoundName(dto.getRoundName());
        }
        if (dto.getStatus() != null) {
            round.setStatus(dto.getStatus());
        }
        if (dto.getScheduledDate() != null) {
            round.setScheduledDate(dto.getScheduledDate());
        }
        if (dto.getStartTime() != null) {
            round.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            round.setEndTime(dto.getEndTime());
        }
        if (dto.getDurationMinutes() != null) {
            round.setDurationMinutes(dto.getDurationMinutes());
        }
        if (dto.getInterviewerName() != null) {
            round.setInterviewerName(dto.getInterviewerName());
        }
        if (dto.getInterviewerEmail() != null) {
            round.setInterviewerEmail(dto.getInterviewerEmail());
        }
        if (dto.getMeetingLink() != null) {
            round.setMeetingLink(dto.getMeetingLink());
        }
        if (dto.getNotes() != null) {
            round.setNotes(dto.getNotes());
        }
        if (dto.getFeedback() != null) {
            round.setFeedback(dto.getFeedback());
        }
        if (dto.getScore() != null) {
            round.setScore(dto.getScore());
        }
        if (dto.getMaxScore() != null) {
            round.setMaxScore(dto.getMaxScore());
        }
    }

    /**
     * Convert entity to DTO
     */
    private InterviewRoundDto convertToDto(InterviewRound round) {
        InterviewRoundDto dto = new InterviewRoundDto();
        dto.setId(round.getId());
        dto.setCandidateId(round.getCandidate().getId());
        dto.setRoundType(round.getRoundType());
        dto.setRoundName(round.getRoundName());
        dto.setRoundOrder(round.getRoundOrder());
        dto.setStatus(round.getStatus());
        dto.setScheduledDate(round.getScheduledDate());
        dto.setStartTime(round.getStartTime());
        dto.setEndTime(round.getEndTime());
        dto.setDurationMinutes(round.getDurationMinutes());
        dto.setInterviewerName(round.getInterviewerName());
        dto.setInterviewerEmail(round.getInterviewerEmail());
        dto.setMeetingLink(round.getMeetingLink());
        dto.setNotes(round.getNotes());
        dto.setFeedback(round.getFeedback());
        dto.setScore(round.getScore());
        dto.setMaxScore(round.getMaxScore());
        dto.setCreatedAt(round.getCreatedAt());
        dto.setUpdatedAt(round.getUpdatedAt());
        return dto;
    }

    /**
     * Convert DTO to entity
     */
    private InterviewRound convertToEntity(InterviewRoundDto dto) {
        InterviewRound round = new InterviewRound();
        round.setRoundType(dto.getRoundType());
        round.setRoundName(dto.getRoundName());
        round.setRoundOrder(dto.getRoundOrder());
        round.setStatus(dto.getStatus());
        round.setScheduledDate(dto.getScheduledDate());
        round.setStartTime(dto.getStartTime());
        round.setEndTime(dto.getEndTime());
        round.setDurationMinutes(dto.getDurationMinutes());
        round.setInterviewerName(dto.getInterviewerName());
        round.setInterviewerEmail(dto.getInterviewerEmail());
        round.setMeetingLink(dto.getMeetingLink());
        round.setNotes(dto.getNotes());
        round.setFeedback(dto.getFeedback());
        round.setScore(dto.getScore());
        round.setMaxScore(dto.getMaxScore());
        return round;
    }
}
