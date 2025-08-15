package com.company.user.repository;

import com.company.user.model.Recruiter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RecruiterRepository extends JpaRepository<Recruiter, String> {
    Optional<Recruiter> findByEmail(String email);
    boolean existsByEmail(String email);
}