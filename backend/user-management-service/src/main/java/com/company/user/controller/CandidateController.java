package com.company.user.controller;

import com.company.user.dto.*;
import com.company.user.model.CandidateStatus;
import com.company.user.service.inter.CandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/candidates")
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
     * Update candidate (multipart)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateResponse updateCandidate(
            @PathVariable Long id,
            @RequestPart("data") @Valid CandidateUpdateRequest data,
            @RequestPart(value = "resume", required = false) MultipartFile resume,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic) {
        // Validate ID from path
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid candidate ID: " + id);
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
}