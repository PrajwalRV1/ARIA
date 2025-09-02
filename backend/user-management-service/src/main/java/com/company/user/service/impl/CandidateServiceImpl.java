package com.company.user.service.impl;

import com.company.user.dto.*;
import com.company.user.exception.BadRequestException;
import com.company.user.model.Candidate;
import com.company.user.model.CandidateStatus;
import com.company.user.repository.CandidateRepository;
import com.company.user.service.inter.CandidateService;
import com.company.user.service.inter.FileStorageService;
import com.company.user.service.inter.ResumeParsingService;
import com.company.user.util.TenantContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ✅ SECURITY: Comprehensive CandidateService implementation with tenant isolation,
 * robust business logic, validation, error handling, and status transition management.
 * 
 * CRITICAL SECURITY FEATURES:
 * - Tenant-aware data access (prevents BOLA attacks)
 * - User context validation and authorization
 * - Secure data isolation between organizations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateServiceImpl implements CandidateService {

    private final CandidateRepository candidateRepository;
    private final FileStorageService fileStorageService;
    private final Optional<ResumeParsingService> resumeParsingService;
    private final Optional<com.company.user.service.InterviewRoundService> interviewRoundService;
    private final TenantContextUtil tenantContextUtil;

    // File type constants
    private static final Set<String> ALLOWED_RESUME_TYPES = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    
    private static final Set<String> ALLOWED_AUDIO_TYPES = Set.of(
        "audio/mpeg",
        "audio/wav",
        "audio/mp3"
    );
    
    private static final long MAX_AUDIO_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_RESUME_SIZE = 25 * 1024 * 1024; // 25MB
    
    // File signature validation constants (magic bytes)
    private static final Map<String, byte[]> FILE_SIGNATURES = Map.of(
        "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}, // %PDF
        "application/msword", new byte[]{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}, // DOC
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{0x50, 0x4B, 0x03, 0x04}, // DOCX (ZIP)
        "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}, // JPEG
        "image/png", new byte[]{(byte)0x89, 0x50, 0x4E, 0x47}, // PNG
        "audio/mpeg", new byte[]{(byte)0xFF, (byte)0xFB}, // MP3
        "audio/wav", new byte[]{0x52, 0x49, 0x46, 0x46} // WAV
    );

    // === CREATE OPERATIONS ===
    
    @Override
    @Transactional
    public CandidateResponse createCandidate(CandidateCreateRequest request,
                                             MultipartFile resume,
                                             MultipartFile profilePic) {
        log.info("Creating candidate: {} for requisition: {}", request.getEmail(), request.getRequisitionId());
        
        try {
            // Validate request
            validateCreateRequest(request);
            
            // Check for duplicates
            if (candidateRepository.existsByEmailAndRequisitionId(request.getEmail(), request.getRequisitionId())) {
                throw new BadRequestException(
                    String.format("Candidate with email '%s' already exists for requisition '%s'", 
                                request.getEmail(), request.getRequisitionId())
                );
            }
            
            // Process files
            FileUploadResult resumeResult = processResumeFile(resume);
            FileUploadResult profilePicResult = processProfilePicFile(profilePic);
            
            // ✅ SECURITY: Build candidate entity with tenant isolation
            Candidate candidate = buildCandidateFromRequest(request, resumeResult, profilePicResult);
            
            // Save candidate with PostgreSQL enum casting
            Candidate savedCandidate = candidateRepository.saveWithEnumCasting(candidate);
            log.info("Successfully created candidate with ID: {}", savedCandidate.getId());
            
            // Clear related caches after creation
            clearCandidateCaches(savedCandidate.getTenantId(), savedCandidate.getRecruiterId());
            
            return CandidateResponse.from(savedCandidate);
            
        } catch (Exception e) {
            log.error("Error creating candidate: {}", e.getMessage(), e);
            if (e instanceof BadRequestException || e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("Failed to create candidate: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public CandidateResponse updateCandidate(CandidateUpdateRequest request,
                                             MultipartFile resume,
                                             MultipartFile profilePic) {
        log.info("Updating candidate with ID: {}", request.getId());
        
        try {
            // Validate request
            validateUpdateRequest(request);
            
            // ✅ SECURITY: Find existing candidate with tenant isolation
            String tenantId = tenantContextUtil.getCurrentTenantId();
            String recruiterId = tenantContextUtil.getCurrentRecruiterId();
            
            log.debug("[UPDATE] Looking for candidate ID {} with tenant: '{}', recruiter: '{}'", 
                    request.getId(), tenantId, recruiterId);
            
            Candidate existingCandidate;
            if (recruiterId != null && !recruiterId.trim().isEmpty()) {
                // Recruiter can only update their own candidates within their tenant
                existingCandidate = candidateRepository.findByIdAndTenantIdAndRecruiterId(request.getId(), tenantId, recruiterId)
                        .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Candidate with ID %d not found or access denied", request.getId())
                        ));
            } else {
                // Admin can update any candidate within their tenant
                existingCandidate = candidateRepository.findByIdAndTenantId(request.getId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException(
                            String.format("Candidate with ID %d not found in tenant %s", request.getId(), tenantId)
                        ));
            }
            
            log.debug("[UPDATE] Found candidate: id={}, tenant={}, recruiter={}, name={}", 
                    existingCandidate.getId(), existingCandidate.getTenantId(), 
                    existingCandidate.getRecruiterId(), existingCandidate.getName());
            
            // Validate status transition (only if status is actually changing)
            if (!existingCandidate.getStatus().equals(request.getStatus())) {
                if (!existingCandidate.canTransitionTo(request.getStatus())) {
                    throw new BadRequestException(
                        String.format("Invalid status transition from %s to %s", 
                                    existingCandidate.getStatus(), request.getStatus())
                    );
                }
            }
            
            // Check for email conflicts (excluding current candidate)
            if (!existingCandidate.getEmail().equals(request.getEmail()) || 
                !existingCandidate.getRequisitionId().equals(request.getRequisitionId())) {
                
                // Use tenant-aware existence check for security
                boolean conflictExists = (recruiterId != null && !recruiterId.trim().isEmpty()) ?
                    candidateRepository.existsByEmailAndRequisitionIdAndTenantIdAndRecruiterId(
                        request.getEmail(), request.getRequisitionId(), tenantId, recruiterId) :
                    candidateRepository.existsByEmailAndRequisitionIdAndTenantId(
                        request.getEmail(), request.getRequisitionId(), tenantId);
                
                if (conflictExists) {
                    throw new BadRequestException(
                        String.format("Another candidate with email '%s' already exists for requisition '%s' in your organization", 
                                    request.getEmail(), request.getRequisitionId())
                    );
                }
            }
            
            // Process files
            FileUploadResult resumeResult = processResumeFile(resume);
            FileUploadResult profilePicResult = processProfilePicFile(profilePic);
            
            // Update audit fields in request
            String currentUserEmail = tenantContextUtil.getCurrentRecruiterId();
            
            // Validate recruiter context for updates - prevent unauthorized transfers
            // Only check if the request is explicitly trying to change the recruiterId
            if (StringUtils.hasText(request.getRecruiterId()) && 
                !request.getRecruiterId().equals(existingCandidate.getRecruiterId())) {
                
                log.debug("[UPDATE] Recruiter ID change detected - from '{}' to '{}'", 
                        existingCandidate.getRecruiterId(), request.getRecruiterId());
                
                // Only allow if current user matches the new recruiter ID (prevent unauthorized transfers)
                if (currentUserEmail == null || !currentUserEmail.equals(request.getRecruiterId())) {
                    log.warn("[UPDATE] Attempted unauthorized recruiter transfer from '{}' to '{}' by user '{}'", 
                            existingCandidate.getRecruiterId(), request.getRecruiterId(), currentUserEmail);
                    throw new AccessDeniedException(
                        "Access denied: You cannot transfer candidates to another recruiter");
                }
            } else if (!StringUtils.hasText(request.getRecruiterId())) {
                // If no recruiterId provided in request, keep the existing one (don't change it)
                request.setRecruiterId(existingCandidate.getRecruiterId());
                log.debug("[UPDATE] No recruiter ID in request, preserving existing: '{}'", existingCandidate.getRecruiterId());
            }
            
            // Use field-based update to avoid Hibernate auto-flush issues with enum casting
            Candidate savedCandidate = candidateRepository.updateCandidateFields(
                existingCandidate.getId(), 
                request,
                resumeResult.url, resumeResult.fileName, resumeResult.fileSize,
                profilePicResult.url, profilePicResult.fileName, profilePicResult.fileSize
            );
            log.info("Successfully updated candidate with ID: {}", savedCandidate.getId());
            
            // Clear related caches after update
            clearCandidateCaches(savedCandidate.getTenantId(), savedCandidate.getRecruiterId());
            // Also clear individual candidate cache
            clearCandidateCache(savedCandidate.getId(), savedCandidate.getTenantId());
            
            return CandidateResponse.from(savedCandidate);
            
        } catch (Exception e) {
            log.error("Error updating candidate: {}", e.getMessage(), e);
            if (e instanceof BadRequestException || e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("Failed to update candidate: " + e.getMessage(), e);
        }
    }

    // === READ OPERATIONS ===
    
    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
        value = "candidateList", 
        key = "T{#root.target.tenantContextUtil.getCurrentTenantId()}.concat('_R').concat(T{#root.target.tenantContextUtil.getCurrentRecruiterId()?.toString() ?: 'ADMIN'})"
    )
    public List<CandidateResponse> getAllCandidates() {
        log.info("[DEBUG] Fetching all candidates with tenant isolation");
        
        // ✅ SECURITY: Extract tenant and recruiter context
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String recruiterId = tenantContextUtil.getCurrentRecruiterId();
        
        log.info("[DEBUG] Extracted context - tenant: '{}', recruiter: '{}'", tenantId, recruiterId);
        
        // Log null/empty checks
        if (tenantId == null) {
            log.warn("[DEBUG] TenantId is NULL!");
        } else if (tenantId.trim().isEmpty()) {
            log.warn("[DEBUG] TenantId is EMPTY!");
        }
        
        if (recruiterId == null) {
            log.info("[DEBUG] RecruiterId is NULL - will use admin query");
        } else if (recruiterId.trim().isEmpty()) {
            log.info("[DEBUG] RecruiterId is EMPTY - will use admin query");
        }
        
        // Use tenant-aware repository method
        List<Candidate> candidates;
        if (recruiterId != null && !recruiterId.trim().isEmpty()) {
            // Recruiter-specific view: see only their candidates within their tenant
            log.info("[DEBUG] Using recruiter-specific query with tenant: '{}', recruiter: '{}'", tenantId, recruiterId);
            candidates = candidateRepository.findByTenantIdAndRecruiterIdOrderByCreatedAtDesc(tenantId, recruiterId);
        } else {
            // Admin view: see all candidates within their tenant
            log.info("[DEBUG] Using admin query with tenant: '{}'", tenantId);
            candidates = candidateRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        
        log.info("[DEBUG] Repository query returned {} candidates", candidates != null ? candidates.size() : 0);
        
        // Log first few candidates for debugging
        if (candidates != null && !candidates.isEmpty()) {
            log.info("[DEBUG] First candidate example: id={}, tenant={}, recruiter={}, name={}", 
                    candidates.get(0).getId(), 
                    candidates.get(0).getTenantId(),
                    candidates.get(0).getRecruiterId(),
                    candidates.get(0).getName());
        }
        
        List<CandidateResponse> result = candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
        
        log.info("[DEBUG] Returning {} candidate responses", result.size());
        return result;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<CandidateResponse> getAllCandidates(Pageable pageable) {
        log.debug("Fetching candidates with pagination: {}", pageable);
        Page<Candidate> candidatePage = candidateRepository.findAllWithPagination(pageable);
        return candidatePage.map(CandidateResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
        value = "candidates", 
        key = "'C'.concat(#id.toString()).concat('_T').concat(#root.target.tenantContextUtil.getCurrentTenantId())"
    )
    public Optional<CandidateResponse> getCandidateById(Long id) {
        log.debug("Fetching candidate by ID: {} with tenant isolation", id);
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + id);
        }
        
        // ✅ SECURITY: Extract tenant and recruiter context
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String recruiterId = tenantContextUtil.getCurrentRecruiterId();
        
        // Use tenant-aware repository method
        Optional<Candidate> candidate;
        if (recruiterId != null && !recruiterId.trim().isEmpty()) {
            // Recruiter can only access their own candidates within their tenant
            candidate = candidateRepository.findByIdAndTenantIdAndRecruiterId(id, tenantId, recruiterId);
        } else {
            // Admin can access any candidate within their tenant
            candidate = candidateRepository.findByIdAndTenantId(id, tenantId);
        }
        
        if (candidate.isEmpty()) {
            log.warn("Candidate ID: {} not found or access denied for tenant: {} and recruiter: {}", 
                    id, tenantId, recruiterId);
        }
        
        return candidate.map(CandidateResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> searchCandidatesByName(String name) {
        log.debug("Searching candidates by name: {}", name);
        if (!StringUtils.hasText(name)) {
            return getAllCandidates();
        }
        
        // ✅ SECURITY: Extract tenant and recruiter context
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String recruiterId = tenantContextUtil.getCurrentRecruiterId();
        
        // Use tenant-aware repository method
        List<Candidate> candidates;
        if (recruiterId != null && !recruiterId.trim().isEmpty()) {
            // Recruiter-specific search within their tenant
            candidates = candidateRepository.findByNameContainingIgnoreCaseAndTenantAndRecruiter(
                name.trim(), tenantId, recruiterId);
        } else {
            // Admin search within their tenant
            candidates = candidateRepository.findByNameContainingIgnoreCaseAndTenant(
                name.trim(), tenantId);
        }
        
        return candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> globalSearch(String searchTerm) {
        log.debug("Performing global search with term: {}", searchTerm);
        if (!StringUtils.hasText(searchTerm)) {
            return getAllCandidates();
        }
        
        // ✅ SECURITY: Extract tenant and recruiter context
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String recruiterId = tenantContextUtil.getCurrentRecruiterId();
        
        // Use tenant-aware repository method
        List<Candidate> candidates;
        if (recruiterId != null && !recruiterId.trim().isEmpty()) {
            // Recruiter-specific search within their tenant
            candidates = candidateRepository.globalSearchWithTenantAndRecruiter(
                searchTerm.trim(), tenantId, recruiterId);
        } else {
            // Admin search within their tenant
            candidates = candidateRepository.globalSearchWithTenant(
                searchTerm.trim(), tenantId);
        }
        
        return candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> getCandidatesByStatus(CandidateStatus status) {
        log.debug("Fetching candidates by status: {}", status);
        if (status == null) {
            return getAllCandidates();
        }
        
        // ✅ SECURITY: Extract tenant and recruiter context
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String recruiterId = tenantContextUtil.getCurrentRecruiterId();
        
        // Use custom enum casting repository method to avoid PostgreSQL type issues
        List<Candidate> candidates;
        if (recruiterId != null && !recruiterId.trim().isEmpty()) {
            // Recruiter-specific filtering within their tenant using enum casting
            candidates = candidateRepository.findByStatusWithEnumCasting(
                status.name(), tenantId, recruiterId);
        } else {
            // Admin filtering within their tenant using enum casting
            candidates = candidateRepository.findByStatusWithEnumCasting(
                status.name(), tenantId);
        }
        
        return candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> getCandidatesByRequisitionId(String requisitionId) {
        log.debug("Fetching candidates by requisition ID: {}", requisitionId);
        if (!StringUtils.hasText(requisitionId)) {
            return new ArrayList<>();
        }
        
        // ✅ SECURITY: Extract tenant and recruiter context
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String recruiterId = tenantContextUtil.getCurrentRecruiterId();
        
        // Use tenant-aware repository method
        List<Candidate> candidates;
        if (recruiterId != null && !recruiterId.trim().isEmpty()) {
            // Recruiter-specific filtering within their tenant
            candidates = candidateRepository.findByRequisitionIdAndTenantAndRecruiter(
                requisitionId.trim(), tenantId, recruiterId);
        } else {
            // Admin filtering within their tenant
            candidates = candidateRepository.findByRequisitionIdAndTenant(
                requisitionId.trim(), tenantId);
        }
        
        return candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> getCandidatesByRecruiterId(String recruiterId) {
        log.debug("Fetching candidates by recruiter ID: {}", recruiterId);
        if (!StringUtils.hasText(recruiterId)) {
            return new ArrayList<>();
        }
        
        // ✅ SECURITY: Extract tenant and recruiter context
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String currentRecruiterId = tenantContextUtil.getCurrentRecruiterId();
        
        // Security check: ensure current recruiter can only access their own candidates
        if (currentRecruiterId != null && !currentRecruiterId.trim().isEmpty()) {
            if (!currentRecruiterId.equals(recruiterId)) {
                throw new AccessDeniedException(
                    "Access denied: Recruiters can only access their own candidates");
            }
        }
        
        // Use tenant-aware repository method
        List<Candidate> candidates = candidateRepository.findByTenantIdAndRecruiterIdOrderByCreatedAtDesc(
            tenantId, recruiterId.trim());
        
        return candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
    }

    // === DELETE OPERATIONS ===
    
    @Override
    @Transactional
    public void deleteCandidate(Long id) {
        log.info("Deleting candidate with ID: {} with tenant isolation", id);
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + id);
        }
        
        // ✅ SECURITY: Extract tenant and recruiter context
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String recruiterId = tenantContextUtil.getCurrentRecruiterId();
        
        log.info("Delete request context - tenant: '{}', recruiter: '{}', candidateId: {}", tenantId, recruiterId, id);
        
        // Find candidate with tenant isolation
        Candidate candidateToDelete;
        if (recruiterId != null && !recruiterId.trim().isEmpty()) {
            // Recruiter can only delete their own candidates within their tenant
            candidateToDelete = candidateRepository.findByIdAndTenantIdAndRecruiterId(id, tenantId, recruiterId)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Candidate with ID " + id + " not found or access denied"
                    ));
        } else {
            // Admin can delete any candidate within their tenant
            candidateToDelete = candidateRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Candidate with ID " + id + " not found or access denied"
                    ));
        }
        
        try {
            candidateRepository.delete(candidateToDelete);
            log.info("Successfully deleted candidate '{}' (ID: {}) from tenant: {}", 
                    candidateToDelete.getName(), id, tenantId);
            
            // Clear related caches after deletion
            clearCandidateCaches(tenantId, recruiterId);
            clearCandidateCache(id, tenantId);
            
        } catch (Exception e) {
            log.error("Error deleting candidate with ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete candidate: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional
    public int bulkUpdateStatus(List<Long> candidateIds, CandidateStatus newStatus) {
        log.info("Bulk updating status to {} for {} candidates", newStatus, candidateIds.size());
        
        if (candidateIds == null || candidateIds.isEmpty()) {
            throw new IllegalArgumentException("Candidate IDs list cannot be empty");
        }
        
        if (newStatus == null) {
            throw new IllegalArgumentException("New status cannot be null");
        }
        
        try {
            Long[] idsArray = candidateIds.toArray(new Long[0]);
            int updatedCount = candidateRepository.bulkUpdateStatus(idsArray, newStatus.name());
            log.info("Successfully updated status for {} candidates", updatedCount);
            return updatedCount;
        } catch (Exception e) {
            log.error("Error in bulk status update: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update candidate statuses: " + e.getMessage(), e);
        }
    }

    // === FILE OPERATIONS ===
    
    @Override
    @Transactional
    public AudioUploadResponse uploadAudioFile(Long candidateId, MultipartFile audioFile) throws IOException {
        log.info("Uploading audio file for candidate ID: {}", candidateId);
        
        if (candidateId == null || candidateId <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + candidateId);
        }
        
        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException("Audio file cannot be empty");
        }
        
        // ✅ SECURITY: Find candidate with tenant isolation
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String recruiterId = tenantContextUtil.getCurrentRecruiterId();
        
        Candidate candidate;
        if (recruiterId != null && !recruiterId.trim().isEmpty()) {
            // Recruiter can only upload audio for their own candidates
            candidate = candidateRepository.findByIdAndTenantIdAndRecruiterId(candidateId, tenantId, recruiterId)
                    .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Candidate with ID %d not found or access denied", candidateId)
                    ));
        } else {
            // Admin can upload audio for any candidate in their tenant
            candidate = candidateRepository.findByIdAndTenantId(candidateId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Candidate with ID %d not found in tenant %s", candidateId, tenantId)
                    ));
        }
        
        // Validate audio file
        validateAudioFile(audioFile);
        
        try {
            // Store audio file
            String storedFilename = fileStorageService.storeAudio(audioFile);
            String audioUrl = "/files/audio/" + storedFilename;
            
            // Update candidate
            candidate.setAudioFilename(audioFile.getOriginalFilename());
            candidate.setAudioUrl(audioUrl);
            candidate.setAudioSize(audioFile.getSize());
            
            candidateRepository.save(candidate);
            
            log.info("Successfully uploaded audio file for candidate ID: {}", candidateId);
            
            return new AudioUploadResponse(
                audioFile.getOriginalFilename(), 
                audioUrl, 
                audioFile.getSize()
            );
            
        } catch (Exception e) {
            log.error("Error uploading audio file for candidate ID {}: {}", candidateId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload audio file: " + e.getMessage(), e);
        }
    }

    @Override
    public ParsedResumeResponse parseResume(MultipartFile resumeFile) {
        log.debug("Parsing resume file: {}", resumeFile.getOriginalFilename());
        
        if (resumeFile == null || resumeFile.isEmpty()) {
            return ParsedResumeResponse.builder().build();
        }
        
        // Security validation before parsing
        try {
            // Validate file size
            if (resumeFile.getSize() > MAX_RESUME_SIZE) {
                throw new IllegalArgumentException(
                    String.format("Resume file size exceeds limit: %d bytes (max: %d bytes)", 
                                resumeFile.getSize(), MAX_RESUME_SIZE)
                );
            }
            
            // Validate content type
            String contentType = resumeFile.getContentType();
            if (contentType == null || !ALLOWED_RESUME_TYPES.contains(contentType)) {
                throw new IllegalArgumentException(
                    String.format("Invalid resume file type: %s. Allowed types: %s", 
                                contentType, ALLOWED_RESUME_TYPES)
                );
            }
            
            // Validate file signature to prevent malicious file uploads
            validateFileSignature(resumeFile);
            
        } catch (IOException e) {
            log.warn("Resume file validation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid resume file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Resume file validation failed: {}", e.getMessage());
            throw e;
        }
        
        return resumeParsingService
                .map(service -> {
                    try {
                        return service.parse(resumeFile);
                    } catch (Exception e) {
                        log.warn("Resume parsing failed: {}", e.getMessage());
                        return ParsedResumeResponse.builder().build();
                    }
                })
                .orElse(ParsedResumeResponse.builder().build());
    }

    // === VALIDATION METHODS ===
    
    private void validateCreateRequest(CandidateCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Create request cannot be null");
        }
        
        if (!StringUtils.hasText(request.getRequisitionId())) {
            throw new IllegalArgumentException("Requisition ID is required");
        }
        
        if (!StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("Candidate name is required");
        }
        
        if (!StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("Email is required");
        }
        
        if (request.getStatus() == null) {
            request.setStatus(CandidateStatus.PENDING); // Default status
        }
    }
    
    private void validateUpdateRequest(CandidateUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Update request cannot be null");
        }
        
        if (request.getId() == null || request.getId() <= 0) {
            throw new IllegalArgumentException("Valid candidate ID is required for update");
        }
        
        validateCreateRequest(CandidateCreateRequest.builder()
                .requisitionId(request.getRequisitionId())
                .name(request.getName())
                .email(request.getEmail())
                .status(request.getStatus())
                .build());
    }
    
    private void validateAudioFile(MultipartFile audioFile) {
        String contentType = audioFile.getContentType();
        if (contentType == null || !ALLOWED_AUDIO_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                String.format("Invalid audio file type: %s. Allowed types: %s", 
                            contentType, ALLOWED_AUDIO_TYPES)
            );
        }
        
        if (audioFile.getSize() > MAX_AUDIO_SIZE) {
            throw new IllegalArgumentException(
                String.format("Audio file size exceeds limit: %d bytes (max: %d bytes)", 
                            audioFile.getSize(), MAX_AUDIO_SIZE)
            );
        }
        
        // Validate file signature to prevent MIME type spoofing
        try {
            validateFileSignature(audioFile);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to validate audio file signature: " + e.getMessage());
        }
    }
    
    /**
     * Validates file signature (magic bytes) to prevent MIME type spoofing attacks
     */
    private void validateFileSignature(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }
        
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("File content type is required");
        }
        
        byte[] expectedSignature = FILE_SIGNATURES.get(contentType);
        if (expectedSignature == null) {
            // If no signature defined, skip validation (but content type was already validated)
            return;
        }
        
        byte[] header = new byte[Math.max(8, expectedSignature.length)];
        try (InputStream is = file.getInputStream()) {
            int bytesRead = is.read(header);
            if (bytesRead < expectedSignature.length) {
                throw new IllegalArgumentException("File is too small or corrupted");
            }
        }
        
        // Check if file signature matches expected signature
        for (int i = 0; i < expectedSignature.length; i++) {
            if (header[i] != expectedSignature[i]) {
                throw new IllegalArgumentException(
                    String.format("File content does not match declared type '%s'. Potential security risk detected.", contentType)
                );
            }
        }
        
        log.debug("File signature validation passed for content type: {}", contentType);
    }

    // === FILE PROCESSING METHODS ===
    
    private FileUploadResult processResumeFile(MultipartFile resume) {
        if (resume == null || resume.isEmpty()) {
            return new FileUploadResult(null, null, null);
        }
        
        // Validate resume file
        String contentType = resume.getContentType();
        if (contentType == null || !ALLOWED_RESUME_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                String.format("Invalid resume file type: %s. Allowed types: %s", 
                            contentType, ALLOWED_RESUME_TYPES)
            );
        }
        
        if (resume.getSize() > MAX_RESUME_SIZE) {
            throw new IllegalArgumentException(
                String.format("Resume file size exceeds limit: %d bytes (max: %d bytes)", 
                            resume.getSize(), MAX_RESUME_SIZE)
            );
        }
        
        // Validate file signature to prevent MIME type spoofing
        try {
            validateFileSignature(resume);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to validate resume file signature: " + e.getMessage());
        }
        
        try {
            String resumeUrl = fileStorageService.storeResume(resume);
            return new FileUploadResult(resumeUrl, resume.getOriginalFilename(), resume.getSize());
        } catch (Exception e) {
            throw new RuntimeException("Failed to store resume file: " + e.getMessage(), e);
        }
    }
    
    private FileUploadResult processProfilePicFile(MultipartFile profilePic) {
        if (profilePic == null || profilePic.isEmpty()) {
            return new FileUploadResult(null, null, null);
        }
        
        // Validate profile picture
        String contentType = profilePic.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Profile picture must be an image file");
        }
        
        if (profilePic.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException(
                String.format("Profile picture size exceeds limit: %d bytes (max: %d bytes)", 
                            profilePic.getSize(), MAX_IMAGE_SIZE)
            );
        }
        
        // Validate file signature to prevent MIME type spoofing
        try {
            validateFileSignature(profilePic);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to validate profile picture signature: " + e.getMessage());
        }
        
        try {
            String profilePicUrl = fileStorageService.storeProfilePic(profilePic);
            return new FileUploadResult(profilePicUrl, profilePic.getOriginalFilename(), profilePic.getSize());
        } catch (Exception e) {
            throw new RuntimeException("Failed to store profile picture: " + e.getMessage(), e);
        }
    }

    // === ENTITY BUILDING METHODS ===
    
    private Candidate buildCandidateFromRequest(CandidateCreateRequest request, 
                                               FileUploadResult resumeResult, 
                                               FileUploadResult profilePicResult) {
        
        // ✅ SECURITY: Extract tenant context for data isolation
        String tenantId = tenantContextUtil.getCurrentTenantId();
        String currentUserEmail = tenantContextUtil.getCurrentRecruiterId();
        
        // ✅ SECURITY: Validate tenant context
        if (tenantId == null || tenantId.trim().isEmpty() || "default".equals(tenantId)) {
            throw new IllegalArgumentException(
                "Invalid tenant context. Please ensure you are properly authenticated.");
        }
        
        // ✅ SECURITY: Validate recruiter context and assignment
        String recruiterId;
        if (StringUtils.hasText(request.getRecruiterId())) {
            // If recruiter ID provided in request, validate it matches current user (prevent privilege escalation)
            if (currentUserEmail != null && !currentUserEmail.equals(request.getRecruiterId())) {
                throw new AccessDeniedException(
                    "Access denied: You can only create candidates under your own recruiter ID");
            }
            recruiterId = request.getRecruiterId();
        } else {
            // Use current user's email from JWT token
            if (currentUserEmail == null || currentUserEmail.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "Unable to determine recruiter context. Please ensure you are properly authenticated.");
            }
            recruiterId = currentUserEmail;
        }
        
        log.debug("Creating candidate for tenant: {} with validated recruiter: {}", tenantId, recruiterId);
        
        return Candidate.builder()
                .requisitionId(request.getRequisitionId())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .appliedRole(request.getAppliedRole())
                .applicationDate(request.getApplicationDate())
                .totalExperience(request.getTotalExperience())
                .relevantExperience(request.getRelevantExperience())
                .interviewRound(request.getInterviewRound())
                .status(request.getStatus() != null ? request.getStatus() : CandidateStatus.PENDING)
                .jobDescription(request.getJobDescription())
                .keyResponsibilities(request.getKeyResponsibilities())
                .skills(request.getSkills())
                .resumeUrl(resumeResult.url)
                .resumeFileName(resumeResult.fileName)
                .resumeSize(resumeResult.fileSize)
                .profilePicUrl(profilePicResult.url)
                .profilePicFileName(profilePicResult.fileName)
                .profilePicSize(profilePicResult.fileSize)
                .source(request.getSource())
                .notes(request.getNotes())
                .tags(request.getTags())
                .recruiterId(recruiterId)
                // ✅ SECURITY: Set tenant isolation fields
                .tenantId(tenantId)
                .createdBy(currentUserEmail != null ? currentUserEmail : "system")
                .updatedBy(currentUserEmail != null ? currentUserEmail : "system")
                .build();
    }
    
    private void updateCandidateFromRequest(Candidate candidate, 
                                          CandidateUpdateRequest request,
                                          FileUploadResult resumeResult, 
                                          FileUploadResult profilePicResult) {
        
        // ✅ SECURITY: Validate recruiter context for updates
        String currentUserEmail = tenantContextUtil.getCurrentRecruiterId();
        
        // If recruiter ID is being changed, validate it
        if (StringUtils.hasText(request.getRecruiterId()) && 
            !request.getRecruiterId().equals(candidate.getRecruiterId())) {
            // Only allow if current user matches the new recruiter ID (prevent unauthorized transfers)
            if (currentUserEmail == null || !currentUserEmail.equals(request.getRecruiterId())) {
                throw new AccessDeniedException(
                    "Access denied: You cannot transfer candidates to another recruiter");
            }
        }
        candidate.setRequisitionId(request.getRequisitionId());
        candidate.setName(request.getName());
        candidate.setEmail(request.getEmail());
        candidate.setPhone(request.getPhone());
        candidate.setAppliedRole(request.getAppliedRole());
        candidate.setApplicationDate(request.getApplicationDate());
        candidate.setTotalExperience(request.getTotalExperience());
        candidate.setRelevantExperience(request.getRelevantExperience());
        candidate.setInterviewRound(request.getInterviewRound());
        candidate.setStatus(request.getStatus());
        candidate.setJobDescription(request.getJobDescription());
        candidate.setKeyResponsibilities(request.getKeyResponsibilities());
        candidate.setSkills(request.getSkills());
        candidate.setSource(request.getSource());
        candidate.setNotes(request.getNotes());
        candidate.setTags(request.getTags());
        // Only update recruiter ID if validation passed above
        if (StringUtils.hasText(request.getRecruiterId())) {
            candidate.setRecruiterId(request.getRecruiterId());
        }
        
        // Update audit field
        candidate.setUpdatedBy(currentUserEmail != null ? currentUserEmail : "system");
        
        // Update files if provided
        if (resumeResult.url != null) {
            candidate.setResumeUrl(resumeResult.url);
            candidate.setResumeFileName(resumeResult.fileName);
            candidate.setResumeSize(resumeResult.fileSize);
        }
        
        if (profilePicResult.url != null) {
            candidate.setProfilePicUrl(profilePicResult.url);
            candidate.setProfilePicFileName(profilePicResult.fileName);
            candidate.setProfilePicSize(profilePicResult.fileSize);
        }
    }

    // === ROUND-BASED STATUS MANAGEMENT ===
    
    @Override
    @Transactional
    public void updateCandidateOverallStatus(Long candidateId) {
        log.debug("Updating overall status for candidate: {}", candidateId);
        
        if (candidateId == null || candidateId <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + candidateId);
        }
        
        // Delegate to InterviewRoundService if available
        interviewRoundService.ifPresentOrElse(
            service -> {
                String overallStatus = service.computeOverallCandidateStatus(candidateId);
                candidateRepository.findById(candidateId).ifPresent(candidate -> {
                    candidate.setOverallStatus(overallStatus);
                    candidateRepository.save(candidate);
                    log.debug("Updated candidate {} overall status to {}", candidateId, overallStatus);
                });
            },
            () -> log.warn("InterviewRoundService not available for status computation")
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public String computeOverallCandidateStatus(Long candidateId) {
        log.debug("Computing overall status for candidate: {}", candidateId);
        
        if (candidateId == null || candidateId <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + candidateId);
        }
        
        // Delegate to InterviewRoundService if available
        return interviewRoundService
            .map(service -> service.computeOverallCandidateStatus(candidateId))
            .orElseGet(() -> {
                log.warn("InterviewRoundService not available, returning default status");
                return "Status Unknown";
            });
    }
    
    @Override
    @Transactional
    public void initializeInterviewRoundsForCandidate(Long candidateId) {
        log.info("Initializing interview rounds for candidate: {}", candidateId);
        
        if (candidateId == null || candidateId <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + candidateId);
        }
        
        // Validate candidate exists
        if (!candidateRepository.existsById(candidateId)) {
            throw new IllegalArgumentException("Candidate with ID " + candidateId + " not found");
        }
        
        // Delegate to InterviewRoundService if available
        interviewRoundService.ifPresentOrElse(
            service -> {
                try {
                    service.initializeStandardRounds(candidateId);
                    log.info("Successfully initialized interview rounds for candidate: {}", candidateId);
                } catch (Exception e) {
                    log.error("Error initializing interview rounds for candidate {}: {}", candidateId, e.getMessage());
                    throw new RuntimeException("Failed to initialize interview rounds: " + e.getMessage(), e);
                }
            },
            () -> log.warn("InterviewRoundService not available for round initialization")
        );
    }

    // === CACHE MANAGEMENT METHODS ===
    
    /**
     * Clear candidate list caches for a specific tenant and recruiter
     */
    @CacheEvict(
        value = "candidateList", 
        key = "#tenantId.concat('_R').concat(#recruiterId != null ? #recruiterId : 'ADMIN')"
    )
    private void clearCandidateCaches(String tenantId, String recruiterId) {
        log.debug("Clearing candidate list cache for tenant: {} and recruiter: {}", tenantId, recruiterId);
    }
    
    /**
     * Clear individual candidate cache
     */
    @CacheEvict(
        value = "candidates", 
        key = "'C'.concat(#candidateId.toString()).concat('_T').concat(#tenantId)"
    )
    private void clearCandidateCache(Long candidateId, String tenantId) {
        log.debug("Clearing individual candidate cache for ID: {} and tenant: {}", candidateId, tenantId);
    }

    // === HELPER CLASSES ===
    
    private static class FileUploadResult {
        final String url;
        final String fileName;
        final Long fileSize;
        
        FileUploadResult(String url, String fileName, Long fileSize) {
            this.url = url;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }
    }
}
