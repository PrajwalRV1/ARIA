package com.company.user.controller;

import com.company.user.dto.*;
import com.company.user.exception.BadRequestException;
import com.company.user.model.CandidateStatus;
import com.company.user.model.InterviewRoundType;
import com.company.user.service.inter.CandidateService;

import java.util.Arrays;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/candidates") // Relative to context path /api/auth
@RequiredArgsConstructor
@Validated
@Slf4j
public class CandidateController {

    private final CandidateService candidateService;

    /**
     * Create candidate - JSON only
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCandidate(@RequestBody @Valid CandidateCreateRequest data) {
        
        try {
            log.info("Creating candidate: {} for requisition: {}", data.getEmail(), data.getRequisitionId());
            log.debug("Request details - Content-Type: {}, Data: present", MediaType.APPLICATION_JSON_VALUE);
            
            CandidateResponse response = candidateService.createCandidate(data, null, null);
            log.info("Successfully created candidate with ID: {}", response.getId());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating candidate: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation Error",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        } catch (BadRequestException e) {
            log.error("Bad request creating candidate: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        } catch (Exception e) {
            log.error("Unexpected error creating candidate: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal Server Error",
                "message", "An unexpected error occurred while creating the candidate",
                "details", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }

    /**
     * Create candidate with multipart form data (for file uploads)
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @PostMapping(value = "/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCandidateWithFiles(
            @RequestPart(value = "data") @Valid CandidateCreateRequest data,
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic,
            HttpServletRequest request) {
        
        try {
            log.info("Creating candidate with files: {} for requisition: {}", data.getEmail(), data.getRequisitionId());
            log.debug("Request details - Content-Type: {}, Parts: data=present, resume={}, profilePic={}", 
                     request.getContentType(), 
                     resume != null ? resume.getOriginalFilename() + " (" + resume.getSize() + " bytes)" : "not provided",
                     profilePic != null ? profilePic.getOriginalFilename() + " (" + profilePic.getSize() + " bytes)" : "not provided");
            
            CandidateResponse response = candidateService.createCandidate(data, resume, profilePic);
            log.info("Successfully created candidate with files, ID: {}", response.getId());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating candidate with files: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation Error",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        } catch (BadRequestException e) {
            log.error("Bad request creating candidate with files: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        } catch (Exception e) {
            log.error("Unexpected error creating candidate with files: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal Server Error",
                "message", "An unexpected error occurred while creating the candidate with files",
                "details", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }

    /**
     * Upload resume only and attempt to parse key fields for prefill
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @PostMapping(value = "/upload-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsedResumeResponse uploadResume(@RequestPart("resume") MultipartFile resume) {
        return candidateService.parseResume(resume);
    }

    /**
     * Update candidate - JSON only (no files)
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateCandidateJson(
            @PathVariable Long id,
            @RequestBody @Valid CandidateUpdateRequest data) {
        
        try {
            // Validate ID from path
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Invalid candidate ID: " + id);
            }
            
            log.info("Updating candidate (JSON only) with ID: {}", id);
            
            // Set the ID from path variable (overrides any ID in the request body)
            data.setId(id);
            CandidateResponse response = candidateService.updateCandidate(data, null, null);
            log.info("Successfully updated candidate with ID: {}", id);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating candidate: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation Error",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        } catch (Exception e) {
            log.error("Unexpected error updating candidate: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal Server Error",
                "message", "An unexpected error occurred while updating the candidate",
                "details", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }
    
    /**
     * Update candidate with multipart form data (for file uploads)
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @PutMapping(value = "/{id}/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateCandidateWithFiles(
            @PathVariable Long id,
            @RequestPart(value = "data") @Valid CandidateUpdateRequest data,
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic,
            HttpServletRequest request) {
        
        try {
            // Validate ID from path
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Invalid candidate ID: " + id);
            }
            
            log.info("Updating candidate with files, ID: {}", id);
            log.debug("Request details - Content-Type: {}, Parts: data=present, resume={}, profilePic={}", 
                     request.getContentType(), 
                     resume != null ? resume.getOriginalFilename() + " (" + resume.getSize() + " bytes)" : "not provided",
                     profilePic != null ? profilePic.getOriginalFilename() + " (" + profilePic.getSize() + " bytes)" : "not provided");
            
            // Set the ID from path variable (overrides any ID in the request body)
            data.setId(id);
            CandidateResponse response = candidateService.updateCandidate(data, resume, profilePic);
            log.info("Successfully updated candidate with files, ID: {}", id);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating candidate with files: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation Error",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        } catch (Exception e) {
            log.error("Unexpected error updating candidate with files: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal Server Error",
                "message", "An unexpected error occurred while updating the candidate with files",
                "details", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }

    /**
     * Fetch all candidates for recruiter dashboard
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> getAllCandidates() {
        return candidateService.getAllCandidates();
    }

    /**
     * Fetch a single candidate by ID
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CandidateResponse> getCandidateById(@PathVariable Long id) {
        return candidateService.getCandidateById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Search candidates by name (partial match)
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> searchCandidatesByName(@RequestParam(required = false) String name) {
        return candidateService.searchCandidatesByName(name);
    }

    /**
     * Get candidates filtered by status
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @GetMapping(value = "/by-status/{status}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> getCandidatesByStatus(@PathVariable CandidateStatus status) {
        return candidateService.getCandidatesByStatus(status);
    }

    /**
     * Get candidates linked to a specific requisition
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @GetMapping(value = "/by-requisition/{reqId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> getCandidatesByRequisitionId(@PathVariable String reqId) {
        return candidateService.getCandidatesByRequisitionId(reqId);
    }

    /**
     * Get available interview round options for frontend dropdowns
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @GetMapping(value = "/interview-round-options", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getInterviewRoundOptions() {
        return Arrays.stream(InterviewRoundType.values())
                .map(InterviewRoundType::getDisplayName)
                .toList();
    }
    
    /**
     * Get available candidate status options for frontend dropdowns
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @GetMapping(value = "/status-options", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getCandidateStatusOptions() {
        return Arrays.stream(CandidateStatus.values())
                .map(Enum::name)
                .toList();
    }

    /**
     * Upload audio file for a candidate
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @PostMapping("/{id}/upload-audio")
    public ResponseEntity<AudioUploadResponse> uploadAudio(
            @PathVariable Long id,
            @RequestParam("audio") MultipartFile audioFile) throws IOException {
        
        AudioUploadResponse response = candidateService.uploadAudioFile(id, audioFile);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a candidate by ID with tenant isolation
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteCandidate(@PathVariable Long id) {
        // Validate ID from path
        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Invalid candidate ID: " + id)
            );
        }
        
        try {
            candidateService.deleteCandidate(id);
            return ResponseEntity.ok(
                Map.of("success", true, "message", "Candidate deleted successfully")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of("success", false, "message", "Failed to delete candidate: " + e.getMessage())
            );
        }
    }


}
