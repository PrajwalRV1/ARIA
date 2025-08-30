package com.company.user.repository;

import com.company.user.model.Candidate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom repository implementation for handling PostgreSQL-specific operations
 * with explicit enum casting to resolve Hibernate enum type issues.
 */
@Repository
@Slf4j
public class CandidateRepositoryImpl implements CandidateRepositoryCustom {

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
        
        // Refresh the entity from database to get updated timestamps
        entityManager.refresh(candidate);
        
        log.info("Successfully updated candidate {} with enum casting", candidate.getId());
        return candidate;
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
                recruiter_id, overall_status, created_at, updated_at
            ) VALUES (
                :requisitionId, :name, :email, :phone, :appliedRole, :applicationDate,
                :totalExperience, :relevantExperience, :interviewRound, CAST(:status AS candidate_status),
                :jobDescription, :keyResponsibilities, :resumeFileName, :resumeUrl, :resumeSize,
                :profilePicFileName, :profilePicUrl, :profilePicSize,
                :audioFilename, :audioUrl, :audioSize, :source, :notes, :tags,
                :recruiterId, :overallStatus, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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
        query.setParameter("overallStatus", candidate.getOverallStatus());
        
        // Set ID for updates
        if (candidate.getId() != null) {
            query.setParameter("id", candidate.getId());
        }
    }
}
