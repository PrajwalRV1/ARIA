package com.company.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for the Interview Orchestrator Service
 * Handles AI-driven adaptive interview orchestration with real-time WebRTC integration
 */
@SpringBootApplication
@EnableJpaRepositories
@EnableAsync
@EnableTransactionManagement
public class InterviewOrchestratorApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(InterviewOrchestratorApplication.class, args);
    }
}
