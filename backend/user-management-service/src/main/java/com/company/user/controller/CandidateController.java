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
     * Create candidate - supports both JSON and multipart:
     * JSON: CandidateCreateRequest as request body
     * Multipart: data part + optional resume/profilePic files
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCandidate(
            @RequestPart(value = "data", required = false) @Valid CandidateCreateRequest dataFromPart,
            @RequestBody(required = false) @Valid CandidateCreateRequest dataFromJson,
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic,
            HttpServletRequest request) {
        
        try {
            // Determine which data to use based on content type
            CandidateCreateRequest data;
            String contentType = request.getContentType();
            
            if (contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                // JSON request
                if (dataFromJson == null) {
                    throw new IllegalArgumentException("Request body is required for JSON content type");
                }
                data = dataFromJson;
            } else if (contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
                // Multipart request
                if (dataFromPart == null) {
                    throw new IllegalArgumentException("'data' part is required for multipart content type");
                }
                data = dataFromPart;
            } else {
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
            }
            
            log.info("Creating candidate: {} for requisition: {}", data.getEmail(), data.getRequisitionId());
            log.debug("Request details - Content-Type: {}, Parts: data={}, resume={}, profilePic={}", 
                     request.getContentType(), 
                     data != null ? "present" : "missing",
                     resume != null ? resume.getOriginalFilename() + " (" + resume.getSize() + " bytes)" : "not provided",
                     profilePic != null ? profilePic.getOriginalFilename() + " (" + profilePic.getSize() + " bytes)" : "not provided");
            
            CandidateResponse response = candidateService.createCandidate(data, resume, profilePic);
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
     * Upload resume only and attempt to parse key fields for prefill
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @PostMapping(value = "/upload-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsedResumeResponse uploadResume(@RequestPart("resume") MultipartFile resume) {
        return candidateService.parseResume(resume);
    }

    /**
     * Update candidate - supports both JSON and multipart
     */
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN') or hasRole('INTERNAL_SERVICE')")
    @RequestMapping(value = "/{id}", 
                   method = RequestMethod.PUT,
                   consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE},
                   produces = MediaType.APPLICATION_JSON_VALUE)
    public CandidateResponse updateCandidate(
            @PathVariable Long id,
            @RequestPart(value = "data", required = false) @Valid CandidateUpdateRequest dataFromPart,
            @RequestBody(required = false) @Valid CandidateUpdateRequest dataFromJson,
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic,
            HttpServletRequest request) {
        
        // Validate ID from path
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + id);
        }
        
        // Determine which data to use based on content type
        CandidateUpdateRequest data;
        String contentType = request.getContentType();
        
        if (contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            // JSON request
            if (dataFromJson == null) {
                throw new IllegalArgumentException("Request body is required for JSON content type");
            }
            data = dataFromJson;
        } else if (contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            // Multipart request
            if (dataFromPart == null) {
                throw new IllegalArgumentException("'data' part is required for multipart content type");
            }
            data = dataFromPart;
        } else {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
        
        // Set the ID from path variable (overrides any ID in the request body)
        data.setId(id);
        return candidateService.updateCandidate(data, resume, profilePic);
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

    /**
     * Debug endpoint to test multipart form data processing without authentication
     * This helps identify if the issue is with auth or multipart processing
     */
    @PostMapping(value = "/debug-multipart", 
                consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> debugMultipart(
            @RequestPart(value = "data", required = false) String jsonData,
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic,
            HttpServletRequest request) {
        
        try {
            log.info("Debug multipart endpoint called");
            log.info("Content-Type: {}", request.getContentType());
            log.info("JSON Data: {}", jsonData);
            log.info("Resume: {}", resume != null ? resume.getOriginalFilename() + " (" + resume.getSize() + " bytes)" : "null");
            log.info("ProfilePic: {}", profilePic != null ? profilePic.getOriginalFilename() + " (" + profilePic.getSize() + " bytes)" : "null");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Multipart form data processed successfully",
                "data", Map.of(
                    "contentType", request.getContentType(),
                    "jsonData", jsonData != null ? jsonData : "null",
                    "resumeInfo", resume != null ? Map.of(
                        "filename", resume.getOriginalFilename(),
                        "size", resume.getSize(),
                        "contentType", resume.getContentType()
                    ) : "null",
                    "profilePicInfo", profilePic != null ? Map.of(
                        "filename", profilePic.getOriginalFilename(),
                        "size", profilePic.getSize(),
                        "contentType", profilePic.getContentType()
                    ) : "null"
                ),
                "timestamp", java.time.Instant.now().toString()
            ));
            
        } catch (Exception e) {
            log.error("Error in debug multipart endpoint: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Debug endpoint failed",
                "error", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }

}
