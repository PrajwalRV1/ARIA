package com.company.user.repository;

import com.company.user.model.Candidate;
import com.company.user.dto.CandidateUpdateRequest;

/**
 * Custom repository interface for handling PostgreSQL-specific operations
 * that require explicit enum casting.
 */
public interface CandidateRepositoryCustom {
    
    /**
     * Save candidate with proper PostgreSQL enum handling
     */
    Candidate saveWithEnumCasting(Candidate candidate);
    
    /**
     * Update candidate with explicit PostgreSQL enum casting
     */
    Candidate updateWithEnumCasting(Candidate candidate);
    
    /**
     * Update candidate fields with explicit PostgreSQL enum casting
     * This avoids Hibernate auto-flush issues by not modifying attached entities
     */
    Candidate updateCandidateFields(Long candidateId, CandidateUpdateRequest request, 
                                   String resumeUrl, String resumeFileName, Long resumeSize,
                                   String profilePicUrl, String profilePicFileName, Long profilePicSize);
    
    /**
     * Find candidates by status with explicit PostgreSQL enum casting
     */
    java.util.List<Candidate> findByStatusWithEnumCasting(String status, String tenantId, String recruiterId);
    
    /**
     * Find candidates by status with explicit PostgreSQL enum casting (admin access)
     */
    java.util.List<Candidate> findByStatusWithEnumCasting(String status, String tenantId);
}
