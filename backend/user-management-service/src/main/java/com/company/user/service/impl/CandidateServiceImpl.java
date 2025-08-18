package com.company.user.service.impl;

import java.io.IOException;

import com.company.user.dto.*;
import com.company.user.model.Candidate;
import com.company.user.model.CandidateStatus;
import com.company.user.repository.CandidateRepository;
import com.company.user.service.inter.CandidateService;
import com.company.user.service.inter.FileStorageService;
import com.company.user.service.inter.ResumeParsingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateServiceImpl implements CandidateService {

    private final Optional<ResumeParsingService> resumeParsingService;
    private final CandidateRepository repo;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public CandidateResponse createCandidate(CandidateCreateRequest req,
                                             MultipartFile resume,
                                             MultipartFile profilePic) {
        if (repo.existsByEmailAndRequisitionId(req.getEmail(), req.getRequisitionId())) {
            throw new IllegalArgumentException("Candidate with same email already exists for this requisition");
        }

        String resumeUrl = null;
        String resumeFileName = null;
        Long resumeSize = null;
        if (resume != null && !resume.isEmpty()) {
            String ct = resume.getContentType();
            if (ct == null || !(ct.equals("application/pdf")
                    || ct.equals("application/msword")
                    || ct.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
                throw new IllegalArgumentException("Resume must be PDF or DOC/DOCX");
            }
            resumeUrl = fileStorageService.storeResume(resume);
            resumeFileName = resume.getOriginalFilename();
            resumeSize = resume.getSize();
        }

        String picUrl = null;
        String picFileName = null;
        Long picSize = null;
        if (profilePic != null && !profilePic.isEmpty()) {
            if (profilePic.getContentType() == null || !profilePic.getContentType().startsWith("image/")) {
                throw new IllegalArgumentException("Profile picture must be an image");
            }
            picUrl = fileStorageService.storeProfilePic(profilePic);
            picFileName = profilePic.getOriginalFilename();
            picSize = profilePic.getSize();
        }

        Candidate c = Candidate.builder()
                .requisitionId(req.getRequisitionId())
                .name(req.getName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .appliedRole(req.getAppliedRole())
                .applicationDate(req.getApplicationDate())
                .totalExperience(req.getTotalExperience())
                .relevantExperience(req.getRelevantExperience())
                .interviewRound(req.getInterviewRound())
                .status(req.getStatus())
                .jobDescription(req.getJobDescription())
                .keyResponsibilities(req.getKeyResponsibilities())
                .skills(req.getSkills())
                .resumeUrl(resumeUrl)
                .resumeFileName(resumeFileName)
                .resumeSize(resumeSize)
                .profilePicUrl(picUrl)
                .profilePicFileName(picFileName)
                .profilePicSize(picSize)
                .source(req.getSource())
                .notes(req.getNotes())
                .tags(req.getTags())
                .recruiterId(req.getRecruiterId())
                .build();

        Candidate saved = repo.save(c);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CandidateResponse updateCandidate(CandidateUpdateRequest req,
                                             MultipartFile resume,
                                             MultipartFile profilePic) {
        Candidate c = repo.findById(req.getId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));
        c.setRequisitionId(req.getRequisitionId());
        c.setName(req.getName());
        c.setEmail(req.getEmail());
        c.setPhone(req.getPhone());
        c.setAppliedRole(req.getAppliedRole());
        c.setApplicationDate(req.getApplicationDate());
        c.setTotalExperience(req.getTotalExperience());
        c.setRelevantExperience(req.getRelevantExperience());
        c.setInterviewRound(req.getInterviewRound());
        c.setStatus(req.getStatus());
        c.setJobDescription(req.getJobDescription());
        c.setKeyResponsibilities(req.getKeyResponsibilities());
        c.setSkills(req.getSkills());
        c.setSource(req.getSource());
        c.setNotes(req.getNotes());
        c.setTags(req.getTags());
        c.setRecruiterId(req.getRecruiterId());

        if (resume != null && !resume.isEmpty()) {
            c.setResumeUrl(fileStorageService.storeResume(resume));
            c.setResumeFileName(resume.getOriginalFilename());
            c.setResumeSize(resume.getSize());
        }
        if (profilePic != null && !profilePic.isEmpty()) {
            c.setProfilePicUrl(fileStorageService.storeProfilePic(profilePic));
            c.setProfilePicFileName(profilePic.getOriginalFilename());
            c.setProfilePicSize(profilePic.getSize());
        }

        Candidate saved = repo.save(c);
        return toResponse(saved);
    }

    @Override
    public ParsedResumeResponse parseResume(MultipartFile resumeFile) {
        return resumeParsingService
                .map(svc -> svc.parse(resumeFile))
                .orElse(ParsedResumeResponse.builder().build());
    }

    @Override
    public List<CandidateResponse> getAllCandidates() {
        return repo.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CandidateResponse> getCandidateById(Long id) {
        return repo.findById(id).map(this::toResponse);
    }

    @Override
    public List<CandidateResponse> searchCandidatesByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getAllCandidates();
        }
        return repo.findAll().stream()
                .filter(c -> c.getName().toLowerCase().contains(name.toLowerCase()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CandidateResponse> getCandidatesByStatus(CandidateStatus status) {
        return repo.findAll().stream()
                .filter(c -> c.getStatus() == status)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CandidateResponse> getCandidatesByRequisitionId(String reqId) {
        return repo.findAll().stream()
                .filter(c -> c.getRequisitionId().equals(reqId))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private CandidateResponse toResponse(Candidate c) {
        return CandidateResponse.builder()
                .id(c.getId())
                .requisitionId(c.getRequisitionId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .appliedRole(c.getAppliedRole())
                .applicationDate(c.getApplicationDate())
                .totalExperience(c.getTotalExperience())
                .relevantExperience(c.getRelevantExperience())
                .interviewRound(c.getInterviewRound())
                .status(c.getStatus())
                .jobDescription(c.getJobDescription())
                .keyResponsibilities(c.getKeyResponsibilities())
                .skills(c.getSkills())
                .resumeUrl(c.getResumeUrl())
                .resumeFileName(c.getResumeFileName())
                .resumeSize(c.getResumeSize())
                .profilePicUrl(c.getProfilePicUrl())
                .profilePicFileName(c.getProfilePicFileName())
                .profilePicSize(c.getProfilePicSize())
                .source(c.getSource())
                .notes(c.getNotes())
                .tags(c.getTags())
                .recruiterId(c.getRecruiterId())
                .build();
    }

    @Override
    @Transactional
    public AudioUploadResponse uploadAudioFile(Long candidateId, MultipartFile audioFile) throws IOException {
        Candidate candidate = repo.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        // validate type
        String contentType = audioFile.getContentType();
        if (contentType == null ||
                !(contentType.equals("audio/mpeg") || contentType.equals("audio/wav"))) {
            throw new IllegalArgumentException("Invalid file type. Only MP3 and WAV allowed.");
        }
        // validate size (<= 10 MB)
        if (audioFile.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Audio file exceeds 10MB limit");
        }

        // store audio
        String storedFilename = fileStorageService.storeAudio(audioFile);
        String url = "/files/audio/" + storedFilename;

        candidate.setAudioFilename(audioFile.getOriginalFilename());
        candidate.setAudioUrl(url);
        candidate.setAudioSize(audioFile.getSize());
        repo.save(candidate);

        return new AudioUploadResponse(audioFile.getOriginalFilename(), url, audioFile.getSize());
    }
}
