package com.company.user.repository;

import com.company.user.model.InterviewRound;
import com.company.user.model.InterviewRoundStatus;
import com.company.user.model.InterviewRoundType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for InterviewRound entity.
 * Provides CRUD operations and custom query methods for interview round management.
 */
@Repository
public interface InterviewRoundRepository extends JpaRepository<InterviewRound, Long> {

    /**
     * Find all interview rounds for a specific candidate
     */
    List<InterviewRound> findByCandidateIdOrderByRoundOrder(Long candidateId);

    /**
     * Find all interview rounds for a specific candidate with pagination
     */
    Page<InterviewRound> findByCandidateId(Long candidateId, Pageable pageable);

    /**
     * Find interview rounds by candidate and status
     */
    List<InterviewRound> findByCandidateIdAndStatus(Long candidateId, InterviewRoundStatus status);

    /**
     * Find interview rounds by candidate and round type
     */
    Optional<InterviewRound> findByCandidateIdAndRoundType(Long candidateId, InterviewRoundType roundType);

    /**
     * Find all interview rounds with specific status
     */
    List<InterviewRound> findByStatus(InterviewRoundStatus status);

    /**
     * Find interview rounds scheduled between given dates
     */
    @Query("SELECT ir FROM InterviewRound ir WHERE ir.scheduledDate BETWEEN :startDate AND :endDate")
    List<InterviewRound> findByScheduledDateBetween(@Param("startDate") LocalDateTime startDate, 
                                                   @Param("endDate") LocalDateTime endDate);

    /**
     * Find interview rounds by interviewer email
     */
    List<InterviewRound> findByInterviewerEmail(String interviewerEmail);

    /**
     * Find upcoming scheduled interviews (status = INTERVIEW_SCHEDULED and scheduled date in future)
     */
    @Query("SELECT ir FROM InterviewRound ir WHERE ir.status = :status AND ir.scheduledDate > :now")
    List<InterviewRound> findUpcomingScheduledInterviews(@Param("status") InterviewRoundStatus status, 
                                                        @Param("now") LocalDateTime now);

    /**
     * Find overdue interviews (status = INTERVIEW_SCHEDULED and scheduled date in past)
     */
    @Query("SELECT ir FROM InterviewRound ir WHERE ir.status = :status AND ir.scheduledDate < :now")
    List<InterviewRound> findOverdueInterviews(@Param("status") InterviewRoundStatus status, 
                                             @Param("now") LocalDateTime now);

    /**
     * Count interview rounds by candidate and status
     */
    long countByCandidateIdAndStatus(Long candidateId, InterviewRoundStatus status);

    /**
     * Count total interview rounds for a candidate
     */
    long countByCandidateId(Long candidateId);

    /**
     * Find the latest interview round for a candidate (by created date)
     */
    @Query("SELECT ir FROM InterviewRound ir WHERE ir.candidate.id = :candidateId ORDER BY ir.createdAt DESC")
    Optional<InterviewRound> findLatestRoundByCandidate(@Param("candidateId") Long candidateId);

    /**
     * Find the next scheduled interview round for a candidate
     */
    @Query("SELECT ir FROM InterviewRound ir WHERE ir.candidate.id = :candidateId " +
           "AND ir.status = :status AND ir.scheduledDate > :now " +
           "ORDER BY ir.scheduledDate ASC")
    Optional<InterviewRound> findNextScheduledRound(@Param("candidateId") Long candidateId,
                                                  @Param("status") InterviewRoundStatus status,
                                                  @Param("now") LocalDateTime now);

    /**
     * Find active interview rounds (IN_PROGRESS or INTERVIEW_SCHEDULED)
     */
    @Query("SELECT ir FROM InterviewRound ir WHERE ir.status IN ('IN_PROGRESS', 'INTERVIEW_SCHEDULED')")
    List<InterviewRound> findActiveRounds();

    /**
     * Find interview rounds that need review (UNDER_REVIEW status)
     */
    List<InterviewRound> findByStatusOrderByUpdatedAtAsc(InterviewRoundStatus status);

    /**
     * Check if a candidate has completed a specific round type
     */
    @Query("SELECT CASE WHEN COUNT(ir) > 0 THEN true ELSE false END " +
           "FROM InterviewRound ir WHERE ir.candidate.id = :candidateId " +
           "AND ir.roundType = :roundType AND ir.status = 'COMPLETED'")
    boolean hasCompletedRoundType(@Param("candidateId") Long candidateId, 
                                 @Param("roundType") InterviewRoundType roundType);

    /**
     * Find candidates with no interview rounds created yet
     */
    @Query("SELECT c.id FROM Candidate c WHERE NOT EXISTS " +
           "(SELECT 1 FROM InterviewRound ir WHERE ir.candidate.id = c.id)")
    List<Long> findCandidatesWithoutInterviewRounds();

    /**
     * Get interview statistics by status
     */
    @Query("SELECT ir.status, COUNT(ir) FROM InterviewRound ir GROUP BY ir.status")
    List<Object[]> getInterviewStatsByStatus();

    /**
     * Get interview statistics by round type
     */
    @Query("SELECT ir.roundType, COUNT(ir) FROM InterviewRound ir GROUP BY ir.roundType")
    List<Object[]> getInterviewStatsByRoundType();

    /**
     * Find interview rounds with scores above threshold
     */
    @Query("SELECT ir FROM InterviewRound ir WHERE ir.score IS NOT NULL AND ir.score >= :minScore")
    List<InterviewRound> findRoundsWithScoreAbove(@Param("minScore") Integer minScore);

    /**
     * Find interview rounds by candidate and creation date range
     */
    @Query("SELECT ir FROM InterviewRound ir WHERE ir.candidate.id = :candidateId " +
           "AND ir.createdAt BETWEEN :startDate AND :endDate ORDER BY ir.roundOrder")
    List<InterviewRound> findByCandidateAndDateRange(@Param("candidateId") Long candidateId,
                                                    @Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);

    /**
     * Delete all interview rounds for a candidate
     */
    void deleteByCandidateId(Long candidateId);

    /**
     * Check if an interview round exists for candidate and round type
     */
    boolean existsByCandidateIdAndRoundType(Long candidateId, InterviewRoundType roundType);
}
