package com.company.user.repository;

import com.company.user.model.Candidate;
import com.company.user.model.CandidateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    boolean existsByEmailAndRequisitionId(String email, String requisitionId);
    Optional<Candidate> findByEmailAndRequisitionId(String email, String requisitionId);
    List<Candidate> findByNameContainingIgnoreCase(String name);
    List<Candidate> findByStatus(CandidateStatus status);
    List<Candidate> findByRequisitionId(String requisitionId);
}