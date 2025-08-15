package com.company.user.service.inter;

import com.company.user.dto.*;
import com.company.user.model.CandidateStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface CandidateService {
    CandidateResponse createCandidate(CandidateCreateRequest request,
                                      MultipartFile resume,
                                      MultipartFile profilePic);

    CandidateResponse updateCandidate(CandidateUpdateRequest request,
                                      MultipartFile resume,
                                      MultipartFile profilePic);

    ParsedResumeResponse parseResume(MultipartFile resumeFile);

    List<CandidateResponse> getAllCandidates();

    Optional<CandidateResponse> getCandidateById(Long id);

    List<CandidateResponse> searchCandidatesByName(String name);

    List<CandidateResponse> getCandidatesByStatus(CandidateStatus status);

    List<CandidateResponse> getCandidatesByRequisitionId(String reqId);
}