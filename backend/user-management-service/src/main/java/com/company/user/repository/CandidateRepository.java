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
    
    // === TENANT-AWARE QUERIES (CRITICAL SECURITY) ===
    
    /**
     * ✅ SECURE: Find candidate by ID with tenant and ownership validation
     * Prevents BOLA (Broken Object Level Authorization) attacks
     */
    @Query("SELECT c FROM Candidate c LEFT JOIN FETCH c.skills WHERE c.id = :id AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId")
    Optional<Candidate> findByIdAndTenantAndRecruiter(@Param("id") Long id, 
                                                     @Param("tenantId") String tenantId, 
                                                     @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Find candidate by ID with tenant validation (admin access)
     * Allows admins to access all candidates within their tenant
     */
    @Query("SELECT c FROM Candidate c LEFT JOIN FETCH c.skills WHERE c.id = :id AND c.tenantId = :tenantId")
    Optional<Candidate> findByIdAndTenant(@Param("id") Long id, @Param("tenantId") String tenantId);
    
    /**
     * ✅ SECURE: Find all candidates with tenant isolation
     */
    @Query("SELECT c FROM Candidate c WHERE c.tenantId = :tenantId AND c.recruiterId = :recruiterId ORDER BY c.createdAt DESC")
    List<Candidate> findAllByTenantAndRecruiter(@Param("tenantId") String tenantId, 
                                               @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Find all candidates with tenant isolation (admin access)
     */
    @Query("SELECT c FROM Candidate c WHERE c.tenantId = :tenantId ORDER BY c.createdAt DESC")
    List<Candidate> findAllByTenant(@Param("tenantId") String tenantId);
    
    /**
     * ✅ SECURE: Check existence with tenant and recruiter validation
     */
    boolean existsByEmailAndRequisitionIdAndTenantIdAndRecruiterId(String email, String requisitionId, String tenantId, String recruiterId);
    
    /**
     * ✅ SECURE: Check existence with tenant validation (admin access)
     */
    boolean existsByEmailAndRequisitionIdAndTenantId(String email, String requisitionId, String tenantId);
    
    /**
     * ✅ SECURE: Find candidates by tenant and recruiter (service layer method)
     */
    @Query("SELECT c FROM Candidate c WHERE c.tenantId = :tenantId AND c.recruiterId = :recruiterId ORDER BY c.createdAt DESC")
    List<Candidate> findByTenantIdAndRecruiterIdOrderByCreatedAtDesc(@Param("tenantId") String tenantId, @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Find candidates by tenant only (admin access)
     */
    @Query("SELECT c FROM Candidate c WHERE c.tenantId = :tenantId ORDER BY c.createdAt DESC")
    List<Candidate> findByTenantIdOrderByCreatedAtDesc(@Param("tenantId") String tenantId);
    
    /**
     * ✅ SECURE: Find candidate by ID and tenant with recruiter validation
     */
    @Query("SELECT c FROM Candidate c LEFT JOIN FETCH c.skills WHERE c.id = :id AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId")
    Optional<Candidate> findByIdAndTenantIdAndRecruiterId(@Param("id") Long id, @Param("tenantId") String tenantId, @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Find candidate by ID and tenant (admin access)
     */
    @Query("SELECT c FROM Candidate c LEFT JOIN FETCH c.skills WHERE c.id = :id AND c.tenantId = :tenantId")
    Optional<Candidate> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") String tenantId);

    // === LIST QUERIES ===
    
    /**
     * Find candidates by name (case-insensitive partial match)
     * @deprecated Use findByNameContainingIgnoreCaseAndTenantAndRecruiter or findByNameContainingIgnoreCaseAndTenant instead
     */
    @Deprecated
    @Query("SELECT c FROM Candidate c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY c.createdAt DESC")
    List<Candidate> findByNameContainingIgnoreCase(@Param("name") String name);
    
    /**
     * Find candidates by status
     * @deprecated Use findByStatusAndTenantAndRecruiter or findByStatusAndTenant instead
     */
    @Deprecated
    List<Candidate> findByStatusOrderByCreatedAtDesc(CandidateStatus status);
    
    /**
     * ✅ SECURE: Find candidates by status with tenant isolation
     */
    @Query("SELECT c FROM Candidate c WHERE c.status = :status AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId ORDER BY c.createdAt DESC")
    List<Candidate> findByStatusAndTenantAndRecruiter(@Param("status") CandidateStatus status, 
                                                     @Param("tenantId") String tenantId, 
                                                     @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Find candidates by status with tenant isolation (admin access)
     */
    @Query("SELECT c FROM Candidate c WHERE c.status = :status AND c.tenantId = :tenantId ORDER BY c.createdAt DESC")
    List<Candidate> findByStatusAndTenant(@Param("status") CandidateStatus status, @Param("tenantId") String tenantId);
    
    /**
     * Find candidates by multiple statuses
     */
    @Query("SELECT c FROM Candidate c WHERE c.status IN :statuses ORDER BY c.createdAt DESC")
    List<Candidate> findByStatusIn(@Param("statuses") Set<CandidateStatus> statuses);
    
    /**
     * Find candidates by requisition ID
     * @deprecated Use findByRequisitionIdAndTenantAndRecruiter or findByRequisitionIdAndTenant instead
     */
    @Deprecated
    List<Candidate> findByRequisitionIdOrderByCreatedAtDesc(String requisitionId);
    
    /**
     * ✅ SECURE: Find candidates by requisition with tenant isolation
     */
    @Query("SELECT c FROM Candidate c WHERE c.requisitionId = :requisitionId AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId ORDER BY c.createdAt DESC")
    List<Candidate> findByRequisitionIdAndTenantAndRecruiter(@Param("requisitionId") String requisitionId, 
                                                            @Param("tenantId") String tenantId, 
                                                            @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Find candidates by requisition with tenant isolation (admin access)
     */
    @Query("SELECT c FROM Candidate c WHERE c.requisitionId = :requisitionId AND c.tenantId = :tenantId ORDER BY c.createdAt DESC")
    List<Candidate> findByRequisitionIdAndTenant(@Param("requisitionId") String requisitionId, @Param("tenantId") String tenantId);
    
    /**
     * Find candidates by recruiter ID
     * @deprecated Use findByTenantIdAndRecruiterIdOrderByCreatedAtDesc instead
     */
    @Deprecated
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
     * @deprecated Use globalSearchWithTenantAndRecruiter or globalSearchWithTenant instead
     */
    @Deprecated
    @Query("SELECT c FROM Candidate c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.appliedRole) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.requisitionId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY c.createdAt DESC")
    List<Candidate> globalSearch(@Param("searchTerm") String searchTerm);
    
    /**
     * ✅ SECURE: Search by name with tenant isolation
     */
    @Query("SELECT c FROM Candidate c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')) AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId ORDER BY c.createdAt DESC")
    List<Candidate> findByNameContainingIgnoreCaseAndTenantAndRecruiter(@Param("name") String name, 
                                                                       @Param("tenantId") String tenantId, 
                                                                       @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Search by name with tenant isolation (admin access)
     */
    @Query("SELECT c FROM Candidate c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')) AND c.tenantId = :tenantId ORDER BY c.createdAt DESC")
    List<Candidate> findByNameContainingIgnoreCaseAndTenant(@Param("name") String name, @Param("tenantId") String tenantId);
    
    /**
     * ✅ SECURE: Global search with tenant isolation
     */
    @Query("SELECT c FROM Candidate c WHERE (" +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.appliedRole) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.requisitionId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%'))" +
           ") AND c.tenantId = :tenantId AND c.recruiterId = :recruiterId " +
           "ORDER BY c.createdAt DESC")
    List<Candidate> globalSearchWithTenantAndRecruiter(@Param("searchTerm") String searchTerm, 
                                                       @Param("tenantId") String tenantId, 
                                                       @Param("recruiterId") String recruiterId);
    
    /**
     * ✅ SECURE: Global search with tenant isolation (admin access)
     */
    @Query("SELECT c FROM Candidate c WHERE (" +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.appliedRole) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.requisitionId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%'))" +
           ") AND c.tenantId = :tenantId " +
           "ORDER BY c.createdAt DESC")
    List<Candidate> globalSearchWithTenant(@Param("searchTerm") String searchTerm, @Param("tenantId") String tenantId);
    
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
