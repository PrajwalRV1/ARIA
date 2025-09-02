package com.company.user.repository;

import com.company.user.model.Candidate;
import com.company.user.dto.CandidateUpdateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Custom repository implementation for handling PostgreSQL-specific operations
 * with explicit enum casting to resolve Hibernate enum type issues.
 */
@Repository
// @Slf4j - Temporarily disabled due to compilation issues
public class CandidateRepositoryImpl implements CandidateRepositoryCustom {

    private static final Logger log = LoggerFactory.getLogger(CandidateRepositoryImpl.class);
    
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Candidate saveWithEnumCasting(Candidate candidate) {
        if (candidate.getId() == null) {
            // New candidate - use INSERT with explicit casting
            return insertWithEnumCasting(candidate);
        } else {
            // Existing candidate - use UPDATE with explicit casting
            return updateWithEnumCasting(candidate);
        }
    }

    @Override
    @Transactional
    public Candidate updateWithEnumCasting(Candidate candidate) {
        log.debug("Updating candidate {} with PostgreSQL enum casting", candidate.getId());
        
        String updateSql = """
            UPDATE candidates SET 
                requisition_id = :requisitionId,
                name = :name,
                email = :email,
                phone = :phone,
                applied_role = :appliedRole,
                application_date = :applicationDate,
                total_experience = :totalExperience,
                relevant_experience = :relevantExperience,
                interview_round = :interviewRound,
                status = CAST(:status AS candidate_status),
                job_description = :jobDescription,
                key_responsibilities = :keyResponsibilities,
                resume_file_name = :resumeFileName,
                resume_url = :resumeUrl,
                resume_size = :resumeSize,
                profile_pic_file_name = :profilePicFileName,
                profile_pic_url = :profilePicUrl,
                profile_pic_size = :profilePicSize,
                audio_filename = :audioFilename,
                audio_url = :audioUrl,
                audio_size = :audioSize,
                source = :source,
                notes = :notes,
                tags = :tags,
                recruiter_id = :recruiterId,
                tenant_id = :tenantId,
                overall_status = :overallStatus,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            """;

        Query query = entityManager.createNativeQuery(updateSql);
        setUpdateParameters(query, candidate);
        
        int updatedRows = query.executeUpdate();
        
        if (updatedRows == 0) {
            throw new RuntimeException("Candidate with ID " + candidate.getId() + " not found for update");
        }
        
        // Detach the original entity and create a fresh query to get updated data
        entityManager.detach(candidate);
        
        // Query fresh data with explicit entity loading
        String selectSql = "SELECT * FROM candidates WHERE id = :id";
        Query selectQuery = entityManager.createNativeQuery(selectSql, Candidate.class);
        selectQuery.setParameter("id", candidate.getId());
        
        @SuppressWarnings("unchecked")
        java.util.List<Candidate> results = selectQuery.getResultList();
        
        if (results.isEmpty()) {
            throw new RuntimeException("Failed to reload candidate after update");
        }
        
        Candidate updatedCandidate = results.get(0);
        log.info("Successfully updated candidate {} with enum casting", updatedCandidate.getId());
        return updatedCandidate;
    }

    private Candidate insertWithEnumCasting(Candidate candidate) {
        log.debug("Inserting new candidate with PostgreSQL enum casting");
        
        String insertSql = """
            INSERT INTO candidates (
                requisition_id, name, email, phone, applied_role, application_date,
                total_experience, relevant_experience, interview_round, status,
                job_description, key_responsibilities, resume_file_name, resume_url, resume_size,
                profile_pic_file_name, profile_pic_url, profile_pic_size,
                audio_filename, audio_url, audio_size, source, notes, tags,
                recruiter_id, tenant_id, overall_status, created_at, updated_at
            ) VALUES (
                :requisitionId, :name, :email, :phone, :appliedRole, :applicationDate,
                :totalExperience, :relevantExperience, :interviewRound, CAST(:status AS candidate_status),
                :jobDescription, :keyResponsibilities, :resumeFileName, :resumeUrl, :resumeSize,
                :profilePicFileName, :profilePicUrl, :profilePicSize,
                :audioFilename, :audioUrl, :audioSize, :source, :notes, :tags,
                :recruiterId, :tenantId, :overallStatus, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            """;

        Query query = entityManager.createNativeQuery(insertSql);
        setUpdateParameters(query, candidate);
        
        query.executeUpdate();
        
        // Get the generated ID - for PostgreSQL with BIGSERIAL
        Query idQuery = entityManager.createNativeQuery("SELECT LASTVAL()");
        Number generatedId = (Number) idQuery.getSingleResult();
        candidate.setId(generatedId.longValue());
        
        log.info("Successfully inserted candidate {} with enum casting", candidate.getId());
        return candidate;
    }

    private void setUpdateParameters(Query query, Candidate candidate) {
        query.setParameter("requisitionId", candidate.getRequisitionId());
        query.setParameter("name", candidate.getName());
        query.setParameter("email", candidate.getEmail());
        query.setParameter("phone", candidate.getPhone());
        query.setParameter("appliedRole", candidate.getAppliedRole());
        query.setParameter("applicationDate", candidate.getApplicationDate());
        query.setParameter("totalExperience", candidate.getTotalExperience());
        query.setParameter("relevantExperience", candidate.getRelevantExperience());
        query.setParameter("interviewRound", candidate.getInterviewRound());
        query.setParameter("status", candidate.getStatus() != null ? candidate.getStatus().name() : "PENDING");
        query.setParameter("jobDescription", candidate.getJobDescription());
        query.setParameter("keyResponsibilities", candidate.getKeyResponsibilities());
        query.setParameter("resumeFileName", candidate.getResumeFileName());
        query.setParameter("resumeUrl", candidate.getResumeUrl());
        query.setParameter("resumeSize", candidate.getResumeSize());
        query.setParameter("profilePicFileName", candidate.getProfilePicFileName());
        query.setParameter("profilePicUrl", candidate.getProfilePicUrl());
        query.setParameter("profilePicSize", candidate.getProfilePicSize());
        query.setParameter("audioFilename", candidate.getAudioFilename());
        query.setParameter("audioUrl", candidate.getAudioUrl());
        query.setParameter("audioSize", candidate.getAudioSize());
        query.setParameter("source", candidate.getSource());
        query.setParameter("notes", candidate.getNotes());
        query.setParameter("tags", candidate.getTags());
        query.setParameter("recruiterId", candidate.getRecruiterId());
        query.setParameter("tenantId", candidate.getTenantId());
        query.setParameter("overallStatus", candidate.getOverallStatus());
        
        // Set ID for updates
        if (candidate.getId() != null) {
            query.setParameter("id", candidate.getId());
        }
    }
    
    @Override
    @Transactional
    public Candidate updateCandidateFields(Long candidateId, CandidateUpdateRequest request, 
                                          String resumeUrl, String resumeFileName, Long resumeSize,
                                          String profilePicUrl, String profilePicFileName, Long profilePicSize) {
        log.debug("Updating candidate {} fields with PostgreSQL enum casting", candidateId);
        
        String updateSql = """
            UPDATE candidates SET 
                requisition_id = :requisitionId,
                name = :name,
                email = :email,
                phone = :phone,
                applied_role = :appliedRole,
                application_date = :applicationDate,
                total_experience = :totalExperience,
                relevant_experience = :relevantExperience,
                interview_round = :interviewRound,
                status = CAST(:status AS candidate_status),
                job_description = :jobDescription,
                key_responsibilities = :keyResponsibilities,
                resume_file_name = COALESCE(:resumeFileName, resume_file_name),
                resume_url = COALESCE(:resumeUrl, resume_url),
                resume_size = COALESCE(:resumeSize, resume_size),
                profile_pic_file_name = COALESCE(:profilePicFileName, profile_pic_file_name),
                profile_pic_url = COALESCE(:profilePicUrl, profile_pic_url),
                profile_pic_size = COALESCE(:profilePicSize, profile_pic_size),
                source = :source,
                notes = :notes,
                tags = :tags,
                recruiter_id = COALESCE(:recruiterId, recruiter_id),
                updated_at = CURRENT_TIMESTAMP,
                updated_by = :updatedBy
            WHERE id = :id
            """;

        Query query = entityManager.createNativeQuery(updateSql);
        
        // Set parameters from request
        query.setParameter("requisitionId", request.getRequisitionId());
        query.setParameter("name", request.getName());
        query.setParameter("email", request.getEmail());
        query.setParameter("phone", request.getPhone());
        query.setParameter("appliedRole", request.getAppliedRole());
        query.setParameter("applicationDate", request.getApplicationDate());
        query.setParameter("totalExperience", request.getTotalExperience());
        query.setParameter("relevantExperience", request.getRelevantExperience());
        query.setParameter("interviewRound", request.getInterviewRound());
        query.setParameter("status", request.getStatus() != null ? request.getStatus().name() : "PENDING");
        query.setParameter("jobDescription", request.getJobDescription());
        query.setParameter("keyResponsibilities", request.getKeyResponsibilities());
        query.setParameter("source", request.getSource());
        query.setParameter("notes", request.getNotes());
        query.setParameter("tags", request.getTags());
        
        // Set file parameters (only update if provided)
        query.setParameter("resumeFileName", resumeFileName);
        query.setParameter("resumeUrl", resumeUrl);
        query.setParameter("resumeSize", resumeSize);
        query.setParameter("profilePicFileName", profilePicFileName);
        query.setParameter("profilePicUrl", profilePicUrl);
        query.setParameter("profilePicSize", profilePicSize);
        
        // Set recruiter ID if provided, otherwise keep existing
        query.setParameter("recruiterId", StringUtils.hasText(request.getRecruiterId()) ? request.getRecruiterId() : null);
        
        // Set audit fields 
        query.setParameter("updatedBy", "system"); // Will be updated by service layer
        query.setParameter("id", candidateId);
        
        int updatedRows = query.executeUpdate();
        
        if (updatedRows == 0) {
            throw new RuntimeException("Candidate with ID " + candidateId + " not found for update");
        }
        
        // Query fresh data with explicit entity loading
        String selectSql = "SELECT * FROM candidates WHERE id = :id";
        Query selectQuery = entityManager.createNativeQuery(selectSql, Candidate.class);
        selectQuery.setParameter("id", candidateId);
        
        @SuppressWarnings("unchecked")
        java.util.List<Candidate> results = selectQuery.getResultList();
        
        if (results.isEmpty()) {
            throw new RuntimeException("Failed to reload candidate after update");
        }
        
        Candidate updatedCandidate = results.get(0);
        log.info("Successfully updated candidate {} fields with enum casting", updatedCandidate.getId());
        return updatedCandidate;
    }
    
    @Override
    @Transactional(readOnly = true)
    public java.util.List<Candidate> findByStatusWithEnumCasting(String status, String tenantId, String recruiterId) {
        log.debug("Finding candidates by status {} with enum casting for tenant {} and recruiter {}", status, tenantId, recruiterId);
        
        String selectSql = """
            SELECT * FROM candidates 
            WHERE status = CAST(:status AS candidate_status) 
            AND tenant_id = :tenantId 
            AND recruiter_id = :recruiterId 
            ORDER BY created_at DESC
            """;
        
        Query query = entityManager.createNativeQuery(selectSql, Candidate.class);
        query.setParameter("status", status);
        query.setParameter("tenantId", tenantId);
        query.setParameter("recruiterId", recruiterId);
        
        @SuppressWarnings("unchecked")
        java.util.List<Candidate> results = query.getResultList();
        
        log.debug("Found {} candidates with status {} using enum casting", results.size(), status);
        return results;
    }
    
    @Override
    @Transactional(readOnly = true)
    public java.util.List<Candidate> findByStatusWithEnumCasting(String status, String tenantId) {
        log.debug("Finding candidates by status {} with enum casting for tenant {} (admin access)", status, tenantId);
        
        String selectSql = """
            SELECT * FROM candidates 
            WHERE status = CAST(:status AS candidate_status) 
            AND tenant_id = :tenantId 
            ORDER BY created_at DESC
            """;
        
        Query query = entityManager.createNativeQuery(selectSql, Candidate.class);
        query.setParameter("status", status);
        query.setParameter("tenantId", tenantId);
        
        @SuppressWarnings("unchecked")
        java.util.List<Candidate> results = query.getResultList();
        
        log.debug("Found {} candidates with status {} using enum casting", results.size(), status);
        return results;
    }
}
