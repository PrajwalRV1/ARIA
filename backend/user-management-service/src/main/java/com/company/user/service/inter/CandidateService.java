package com.company.user.service.inter;

import com.company.user.dto.*;
import com.company.user.model.CandidateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Comprehensive CandidateService interface for managing candidate CRUD operations.
 * Includes advanced search, pagination, file handling, and status management.
 */
public interface CandidateService {
    
    // === CREATE OPERATIONS ===
    
    /**
     * Create a new candidate with optional file uploads
     */
    CandidateResponse createCandidate(CandidateCreateRequest request,
                                      MultipartFile resume,
                                      MultipartFile profilePic);

    // === UPDATE OPERATIONS ===
    
    /**
     * Update an existing candidate with optional file uploads
     */
    CandidateResponse updateCandidate(CandidateUpdateRequest request,
                                      MultipartFile resume,
                                      MultipartFile profilePic);
    
    /**
     * Bulk update status for multiple candidates
     */
    int bulkUpdateStatus(List<Long> candidateIds, CandidateStatus newStatus);

    // === READ OPERATIONS ===
    
    /**
     * Get all candidates
     */
    List<CandidateResponse> getAllCandidates();
    
    /**
     * Get all candidates with pagination
     */
    Page<CandidateResponse> getAllCandidates(Pageable pageable);

    /**
     * Get candidate by ID
     */
    Optional<CandidateResponse> getCandidateById(Long id);

    /**
     * Search candidates by name (partial match)
     */
    List<CandidateResponse> searchCandidatesByName(String name);
    
    /**
     * Global search across multiple candidate fields
     */
    List<CandidateResponse> globalSearch(String searchTerm);

    /**
     * Get candidates by status
     */
    List<CandidateResponse> getCandidatesByStatus(CandidateStatus status);

    /**
     * Get candidates by requisition ID
     */
    List<CandidateResponse> getCandidatesByRequisitionId(String reqId);
    
    /**
     * Get candidates by recruiter ID
     */
    List<CandidateResponse> getCandidatesByRecruiterId(String recruiterId);

    // === DELETE OPERATIONS ===
    
    /**
     * Delete a candidate by ID
     */
    void deleteCandidate(Long id);

    // === FILE OPERATIONS ===
    
    /**
     * Parse resume file and extract information
     */
    ParsedResumeResponse parseResume(MultipartFile resumeFile);

    /**
     * Upload audio file for a candidate
     */
    AudioUploadResponse uploadAudioFile(Long candidateId, MultipartFile audioFile) throws IOException;
}
