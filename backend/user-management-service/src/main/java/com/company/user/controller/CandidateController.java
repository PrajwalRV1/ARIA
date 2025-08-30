package com.company.user.controller;

import com.company.user.dto.*;
import com.company.user.model.CandidateStatus;
import com.company.user.service.inter.CandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/candidates") // Relative to context path /api/auth
@RequiredArgsConstructor
@Validated
public class CandidateController {

    private final CandidateService candidateService;

    /**
     * Create candidate (multipart):
     * - data : application/json (CandidateCreateRequest)
     * - resume : file (optional)
     * - profilePic : file (optional)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CandidateResponse createCandidate(
            @RequestPart("data") @Valid CandidateCreateRequest data,
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic) {
        return candidateService.createCandidate(data, resume, profilePic);
    }

    /**
     * Upload resume only and attempt to parse key fields for prefill
     */
    @PostMapping(value = "/upload-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsedResumeResponse uploadResume(@RequestPart("resume") MultipartFile resume) {
        return candidateService.parseResume(resume);
    }

    /**
     * Update candidate - supports both JSON and multipart
     */
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
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> getAllCandidates() {
        return candidateService.getAllCandidates();
    }

    /**
     * Fetch a single candidate by ID
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CandidateResponse> getCandidateById(@PathVariable Long id) {
        return candidateService.getCandidateById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Search candidates by name (partial match)
     */
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> searchCandidatesByName(@RequestParam(required = false) String name) {
        return candidateService.searchCandidatesByName(name);
    }

    /**
     * Get candidates filtered by status
     */
    @GetMapping(value = "/by-status/{status}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> getCandidatesByStatus(@PathVariable CandidateStatus status) {
        return candidateService.getCandidatesByStatus(status);
    }

    /**
     * Get candidates linked to a specific requisition
     */
    @GetMapping(value = "/by-requisition/{reqId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CandidateResponse> getCandidatesByRequisitionId(@PathVariable String reqId) {
        return candidateService.getCandidatesByRequisitionId(reqId);
    }

    /**
     * Upload audio file for a candidate
     */
    @PostMapping("/{id}/upload-audio")
    public ResponseEntity<AudioUploadResponse> uploadAudio(
            @PathVariable Long id,
            @RequestParam("audio") MultipartFile audioFile) throws IOException {
        
        AudioUploadResponse response = candidateService.uploadAudioFile(id, audioFile);
        return ResponseEntity.ok(response);
    }

}
