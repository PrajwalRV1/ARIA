package com.company.user.repository;

import com.company.user.model.Candidate;
import com.company.user.model.CandidateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for Candidate entity with optimized queries and comprehensive CRUD operations.
 * Includes custom queries for better performance and specific business requirements.
 */
@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long>, CandidateRepositoryCustom {

    // === EXISTENCE CHECKS ===
    
    /**
     * Check if a candidate exists with the given email and requisition ID
     */
    boolean existsByEmailAndRequisitionId(String email, String requisitionId);
    
    /**
     * Check if a candidate exists with the given email (across all requisitions)
     */
    boolean existsByEmail(String email);

    // === SINGLE RECORD FINDERS ===
    
    /**
     * Find candidate by email and requisition ID
     */
    Optional<Candidate> findByEmailAndRequisitionId(String email, String requisitionId);
    
    /**
     * Find candidate by ID with optimized query
     */
    @Query("SELECT c FROM Candidate c LEFT JOIN FETCH c.skills WHERE c.id = :id")
    Optional<Candidate> findByIdWithSkills(@Param("id") Long id);

    // === LIST QUERIES ===
    
    /**
     * Find candidates by name (case-insensitive partial match)
     */
    @Query("SELECT c FROM Candidate c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY c.createdAt DESC")
    List<Candidate> findByNameContainingIgnoreCase(@Param("name") String name);
    
    /**
     * Find candidates by status
     */
    List<Candidate> findByStatusOrderByCreatedAtDesc(CandidateStatus status);
    
    /**
     * Find candidates by multiple statuses
     */
    @Query("SELECT c FROM Candidate c WHERE c.status IN :statuses ORDER BY c.createdAt DESC")
    List<Candidate> findByStatusIn(@Param("statuses") Set<CandidateStatus> statuses);
    
    /**
     * Find candidates by requisition ID
     */
    List<Candidate> findByRequisitionIdOrderByCreatedAtDesc(String requisitionId);
    
    /**
     * Find candidates by recruiter ID
     */
    List<Candidate> findByRecruiterIdOrderByCreatedAtDesc(String recruiterId);
    
    /**
     * Find candidates by applied role (case-insensitive)
     */
    @Query("SELECT c FROM Candidate c WHERE LOWER(c.appliedRole) LIKE LOWER(CONCAT('%', :role, '%')) ORDER BY c.createdAt DESC")
    List<Candidate> findByAppliedRoleContainingIgnoreCase(@Param("role") String role);

    // === PAGINATED QUERIES ===
    
    /**
     * Find all candidates with pagination and sorting
     */
    @Query("SELECT c FROM Candidate c ORDER BY c.createdAt DESC")
    Page<Candidate> findAllWithPagination(Pageable pageable);
    
    /**
     * Find candidates by status with pagination
     */
    Page<Candidate> findByStatusOrderByCreatedAtDesc(CandidateStatus status, Pageable pageable);
    
    /**
     * Find candidates by requisition ID with pagination
     */
    Page<Candidate> findByRequisitionIdOrderByCreatedAtDesc(String requisitionId, Pageable pageable);

    // === SEARCH QUERIES ===
    
    /**
     * Global search across multiple fields
     */
    @Query("SELECT c FROM Candidate c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.appliedRole) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.requisitionId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY c.createdAt DESC")
    List<Candidate> globalSearch(@Param("searchTerm") String searchTerm);
    
    /**
     * Search candidates with pagination
     */
    @Query("SELECT c FROM Candidate c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.appliedRole) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.requisitionId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY c.createdAt DESC")
    Page<Candidate> globalSearchWithPagination(@Param("searchTerm") String searchTerm, Pageable pageable);

    // === FILTER QUERIES ===
    
    /**
     * Find candidates by experience range
     */
    @Query("SELECT c FROM Candidate c WHERE c.totalExperience BETWEEN :minExp AND :maxExp ORDER BY c.totalExperience DESC")
    List<Candidate> findByExperienceRange(@Param("minExp") Double minExperience, 
                                         @Param("maxExp") Double maxExperience);
    
    /**
     * Find candidates created within date range
     */
    @Query("SELECT c FROM Candidate c WHERE c.createdAt BETWEEN :startDate AND :endDate ORDER BY c.createdAt DESC")
    List<Candidate> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find candidates with application date in range
     */
    @Query("SELECT c FROM Candidate c WHERE c.applicationDate BETWEEN :startDate AND :endDate ORDER BY c.applicationDate DESC")
    List<Candidate> findByApplicationDateBetween(@Param("startDate") LocalDate startDate, 
                                                @Param("endDate") LocalDate endDate);

    // === SKILL-BASED QUERIES ===
    
    /**
     * Find candidates with specific skill
     */
    @Query("SELECT DISTINCT c FROM Candidate c JOIN c.skills s WHERE LOWER(s) LIKE LOWER(CONCAT('%', :skill, '%'))")
    List<Candidate> findBySkillContaining(@Param("skill") String skill);
    
    /**
     * Find candidates with any of the specified skills
     */
    @Query("SELECT DISTINCT c FROM Candidate c JOIN c.skills s WHERE LOWER(s) IN :skills")
    List<Candidate> findBySkillsIn(@Param("skills") Set<String> skills);

    // === STATISTICAL QUERIES ===
    
    /**
     * Count candidates by status
     */
    @Query("SELECT c.status, COUNT(c) FROM Candidate c GROUP BY c.status")
    List<Object[]> countByStatus();
    
    /**
     * Count candidates by requisition
     */
    @Query("SELECT c.requisitionId, COUNT(c) FROM Candidate c GROUP BY c.requisitionId")
    List<Object[]> countByRequisition();
    
    /**
     * Get candidates with files (resume/profile/audio)
     */
    @Query("SELECT c FROM Candidate c WHERE c.resumeUrl IS NOT NULL OR c.profilePicUrl IS NOT NULL OR c.audioUrl IS NOT NULL")
    List<Candidate> findCandidatesWithFiles();
    
    /**
     * Find candidates without resume
     */
    @Query("SELECT c FROM Candidate c WHERE c.resumeUrl IS NULL OR c.resumeUrl = ''")
    List<Candidate> findCandidatesWithoutResume();

    // === UPDATE OPERATIONS ===
    
    /**
     * Bulk update status for multiple candidates using explicit PostgreSQL enum casting
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE candidates SET status = CAST(:newStatus AS candidate_status), updated_at = CURRENT_TIMESTAMP WHERE id = ANY(:ids)", nativeQuery = true)
    int bulkUpdateStatus(@Param("ids") Long[] candidateIds, @Param("newStatus") String newStatus);
    
    /**
     * Update candidate status by ID using explicit PostgreSQL enum casting
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE candidates SET status = CAST(:status AS candidate_status), updated_at = CURRENT_TIMESTAMP WHERE id = :id", nativeQuery = true)
    int updateStatusById(@Param("id") Long id, @Param("status") String status);

    // === DELETE OPERATIONS ===
    
    /**
     * Delete candidates by requisition ID
     */
    @Modifying
    @Transactional
    void deleteByRequisitionId(String requisitionId);
    
    /**
     * Delete candidates older than specified date
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Candidate c WHERE c.createdAt < :cutoffDate")
    int deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    // === ADVANCED QUERIES ===
    
    /**
     * Find candidates ready for next interview round
     */
    @Query("SELECT c FROM Candidate c WHERE c.status = 'INTERVIEW_SCHEDULED' AND c.createdAt < :cutoffTime")
    List<Candidate> findCandidatesReadyForInterview(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find stale candidates (no updates for specified period)
     */
    @Query("SELECT c FROM Candidate c WHERE c.updatedAt < :cutoffTime AND c.status NOT IN ('SELECTED', 'REJECTED', 'WITHDRAWN')")
    List<Candidate> findStaleCandidates(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Get candidates summary for dashboard
     */
    @Query("SELECT c.status, COUNT(c), AVG(c.totalExperience) FROM Candidate c WHERE c.recruiterId = :recruiterId GROUP BY c.status")
    List<Object[]> getCandidatesSummaryByRecruiter(@Param("recruiterId") String recruiterId);
}
