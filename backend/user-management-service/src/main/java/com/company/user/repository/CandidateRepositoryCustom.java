package com.company.user.repository;

import com.company.user.model.Candidate;

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
}
