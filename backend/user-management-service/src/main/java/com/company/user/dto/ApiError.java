package com.company.user.dto;

import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiError {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String errorId;     // ✅ ADD: Unique error ID for support tracking
    private List<String> details;
    
    /**
     * ✅ SECURE: Create sanitized error response to prevent information disclosure
     */
    public static ApiError createSanitized(HttpStatus status, String message, String path) {
        return ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(sanitizeMessage(message))
                .path(sanitizePath(path))
                .errorId(generateErrorId())
                .build();
    }
    
    /**
     * ✅ SECURE: Create sanitized error response with details
     */
    public static ApiError createSanitized(HttpStatus status, String message, String path, List<String> details) {
        return ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(sanitizeMessage(message))
                .path(sanitizePath(path))
                .errorId(generateErrorId())
                .details(details)
                .build();
    }
    
    /**
     * Sanitize error messages to prevent information disclosure
     */
    private static String sanitizeMessage(String message) {
        if (message == null) return "An error occurred";
        
        return message
                // Remove database-related terms
                .replaceAll("(?i)(database|sql|query|table|column|schema|constraint)", "data")
                // Remove system-related terms  
                .replaceAll("(?i)(exception|stack|trace|error)", "issue")
                // Remove file paths
                .replaceAll("(?i)([a-zA-Z]:\\\\[\\w\\\\]+|/[\\w/]+)", "[PATH]")
                // Remove IP addresses
                .replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "[IP]")
                // Remove URLs
                .replaceAll("(?i)(https?://[\\w.-]+)", "[URL]")
                // Truncate long messages
                .substring(0, Math.min(message.length(), 200));
    }
    
    /**
     * Sanitize request paths to prevent information disclosure
     */
    private static String sanitizePath(String path) {
        if (path == null) return "";
        
        return path
                .replaceAll("uri=", "")
                .replaceAll("[<>\"']", "")
                // Remove potentially sensitive query parameters
                .replaceAll("(?i)[&?](token|key|secret|password)=[^&]*", "")
                // Truncate long paths
                .substring(0, Math.min(path.length(), 100));
    }
    
    /**
     * Generate unique error ID for support tracking
     */
    public static String generateErrorId() {
        return "ERR-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString(ThreadLocalRandom.current().nextInt(1000, 9999));
    }
}
