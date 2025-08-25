package com.company.interview.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Client service for communicating with User Management Service
 */
@Service
public class UserManagementClient {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementClient.class);
    private final RestTemplate restTemplate;
    private final String userManagementUrl;

    public UserManagementClient(RestTemplate restTemplate, 
                               @Value("${app.services.user-management.url:http://localhost:8080}") String userManagementUrl) {
        this.restTemplate = restTemplate;
        this.userManagementUrl = userManagementUrl;
    }

    /**
     * Get user details by ID
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Optional<UserDetails> getUserById(Long userId) {
        try {
            logger.debug("Fetching user details for ID: {}", userId);
            
            String url = userManagementUrl + "/api/users/{userId}";
            var response = restTemplate.getForEntity(url, UserDetails.class, userId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully retrieved user details for ID: {}", userId);
                return Optional.of(response.getBody());
            }
            
            return Optional.empty();
            
        } catch (HttpClientErrorException.NotFound e) {
            logger.info("User not found: {}", userId);
            return Optional.empty();
            
        } catch (HttpClientErrorException e) {
            logger.error("Client error while fetching user {}: {} - {}", 
                userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServiceCommunicationException("Failed to fetch user details", e);
            
        } catch (HttpServerErrorException e) {
            logger.error("Server error while fetching user {}: {}", userId, e.getStatusCode());
            throw new ServiceCommunicationException("User management service error", e);
            
        } catch (ResourceAccessException e) {
            logger.error("Network error while fetching user {}: {}", userId, e.getMessage());
            throw new ServiceCommunicationException("Failed to connect to user management service", e);
        }
    }

    /**
     * Verify user authentication token
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 2,
        backoff = @Backoff(delay = 500)
    )
    public Optional<TokenValidationResult> validateToken(String token) {
        try {
            logger.debug("Validating user token");
            
            String url = userManagementUrl + "/api/auth/validate";
            var request = Map.of("token", token);
            var response = restTemplate.postForEntity(url, request, TokenValidationResult.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            
            return Optional.empty();
            
        } catch (HttpClientErrorException.Unauthorized e) {
            logger.debug("Token validation failed: unauthorized");
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error validating token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Update user interview statistics
     */
    public void updateUserInterviewStats(Long userId, InterviewStatsUpdate statsUpdate) {
        try {
            logger.debug("Updating interview stats for user: {}", userId);
            
            String url = userManagementUrl + "/api/users/{userId}/interview-stats";
            restTemplate.put(url, statsUpdate, userId);
            
            logger.debug("Successfully updated interview stats for user: {}", userId);
            
        } catch (Exception e) {
            logger.error("Error updating interview stats for user {}: {}", userId, e.getMessage());
            // Don't throw exception for stats updates - they're not critical
        }
    }

    /**
     * Check if user management service is available
     */
    public boolean isServiceAvailable() {
        try {
            String url = userManagementUrl + "/actuator/health";
            var response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            logger.debug("User management service is not available: {}", e.getMessage());
            return false;
        }
    }

    // DTOs for User Management Service communication

    public static class UserDetails {
        private Long id;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
        private boolean isActive;
        private boolean isVerified;

        // Constructors
        public UserDetails() {}

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }

        public boolean isVerified() { return isVerified; }
        public void setVerified(boolean verified) { isVerified = verified; }
        
        // Helper method to get full name
        public String getName() {
            if (firstName != null && lastName != null) {
                return firstName + " " + lastName;
            } else if (firstName != null) {
                return firstName;
            } else if (lastName != null) {
                return lastName;
            } else {
                return email != null ? email : "Unknown User";
            }
        }
    }

    public static class TokenValidationResult {
        private boolean valid;
        private Long userId;
        private String email;
        private String role;
        private String message;

        // Constructors
        public TokenValidationResult() {}

        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class InterviewStatsUpdate {
        private int totalInterviews;
        private int completedInterviews;
        private double averageScore;
        private String lastInterviewDate;

        // Constructors
        public InterviewStatsUpdate() {}

        public InterviewStatsUpdate(int totalInterviews, int completedInterviews, double averageScore) {
            this.totalInterviews = totalInterviews;
            this.completedInterviews = completedInterviews;
            this.averageScore = averageScore;
        }

        // Getters and setters
        public int getTotalInterviews() { return totalInterviews; }
        public void setTotalInterviews(int totalInterviews) { this.totalInterviews = totalInterviews; }

        public int getCompletedInterviews() { return completedInterviews; }
        public void setCompletedInterviews(int completedInterviews) { this.completedInterviews = completedInterviews; }

        public double getAverageScore() { return averageScore; }
        public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

        public String getLastInterviewDate() { return lastInterviewDate; }
        public void setLastInterviewDate(String lastInterviewDate) { this.lastInterviewDate = lastInterviewDate; }
    }

    /**
     * Custom exception for service communication errors
     */
    public static class ServiceCommunicationException extends RuntimeException {
        public ServiceCommunicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
