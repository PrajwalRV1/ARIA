package com.company.user.service.impl;

import com.company.user.dto.*;
import com.company.user.exception.BadRequestException;
import com.company.user.model.Candidate;
import com.company.user.model.CandidateStatus;
import com.company.user.repository.CandidateRepository;
import com.company.user.service.inter.CandidateService;
import com.company.user.service.inter.FileStorageService;
import com.company.user.service.inter.ResumeParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive CandidateService implementation with robust business logic,
 * validation, error handling, and status transition management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateServiceImpl implements CandidateService {

    private final CandidateRepository candidateRepository;
    private final FileStorageService fileStorageService;
    private final Optional<ResumeParsingService> resumeParsingService;

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
            
            // Build candidate entity
            Candidate candidate = buildCandidateFromRequest(request, resumeResult, profilePicResult);
            
            // Save candidate
            Candidate savedCandidate = candidateRepository.save(candidate);
            log.info("Successfully created candidate with ID: {}", savedCandidate.getId());
            
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
            
            // Find existing candidate
            Candidate existingCandidate = candidateRepository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Candidate with ID %d not found", request.getId())
                    ));
            
            // Validate status transition
            if (!existingCandidate.canTransitionTo(request.getStatus())) {
                throw new BadRequestException(
                    String.format("Invalid status transition from %s to %s", 
                                existingCandidate.getStatus(), request.getStatus())
                );
            }
            
            // Check for email conflicts (excluding current candidate)
            if (!existingCandidate.getEmail().equals(request.getEmail())) {
                if (candidateRepository.existsByEmailAndRequisitionId(request.getEmail(), request.getRequisitionId())) {
                    throw new BadRequestException(
                        String.format("Another candidate with email '%s' already exists for requisition '%s'", 
                                    request.getEmail(), request.getRequisitionId())
                    );
                }
            }
            
            // Process files
            FileUploadResult resumeResult = processResumeFile(resume);
            FileUploadResult profilePicResult = processProfilePicFile(profilePic);
            
            // Update candidate
            updateCandidateFromRequest(existingCandidate, request, resumeResult, profilePicResult);
            
            // Save updated candidate
            Candidate savedCandidate = candidateRepository.save(existingCandidate);
            log.info("Successfully updated candidate with ID: {}", savedCandidate.getId());
            
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
    public List<CandidateResponse> getAllCandidates() {
        log.debug("Fetching all candidates");
        List<Candidate> candidates = candidateRepository.findAll();
        return candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
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
    public Optional<CandidateResponse> getCandidateById(Long id) {
        log.debug("Fetching candidate by ID: {}", id);
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + id);
        }
        
        return candidateRepository.findByIdWithSkills(id)
                .map(CandidateResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> searchCandidatesByName(String name) {
        log.debug("Searching candidates by name: {}", name);
        if (!StringUtils.hasText(name)) {
            return getAllCandidates();
        }
        
        List<Candidate> candidates = candidateRepository.findByNameContainingIgnoreCase(name.trim());
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
        
        List<Candidate> candidates = candidateRepository.globalSearch(searchTerm.trim());
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
        
        List<Candidate> candidates = candidateRepository.findByStatusOrderByCreatedAtDesc(status);
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
        
        List<Candidate> candidates = candidateRepository.findByRequisitionIdOrderByCreatedAtDesc(requisitionId.trim());
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
        
        List<Candidate> candidates = candidateRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId.trim());
        return candidates.stream()
                .map(CandidateResponse::from)
                .collect(Collectors.toList());
    }

    // === DELETE OPERATIONS ===
    
    @Override
    @Transactional
    public void deleteCandidate(Long id) {
        log.info("Deleting candidate with ID: {}", id);
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + id);
        }
        
        if (!candidateRepository.existsById(id)) {
            throw new IllegalArgumentException("Candidate with ID " + id + " not found");
        }
        
        try {
            candidateRepository.deleteById(id);
            log.info("Successfully deleted candidate with ID: {}", id);
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
            int updatedCount = candidateRepository.bulkUpdateStatus(candidateIds, newStatus);
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
        
        // Find candidate
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Candidate with ID %d not found", candidateId)
                ));
        
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
                .recruiterId(request.getRecruiterId())
                .build();
    }
    
    private void updateCandidateFromRequest(Candidate candidate, 
                                          CandidateUpdateRequest request,
                                          FileUploadResult resumeResult, 
                                          FileUploadResult profilePicResult) {
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
        candidate.setRecruiterId(request.getRecruiterId());
        
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
