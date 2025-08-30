package com.company.user.exception;

import com.company.user.dto.ApiError;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ✅ SECURE: Global exception handler with sanitized error responses
 * Prevents information disclosure through error messages
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex, WebRequest req) {
        String errorId = ApiError.generateErrorId();
        log.warn("Validation error [{}]: {}", errorId, ex.getMessage());
        
        List<String> sanitizedErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> sanitizeFieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        
        ApiError err = ApiError.createSanitized(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                req.getDescription(false),
                sanitizedErrors
        );
        
        return ResponseEntity.badRequest().body(err);
    }

    @ExceptionHandler({ IllegalArgumentException.class, BadRequestException.class })
    public ResponseEntity<ApiError> onBadArg(RuntimeException ex, WebRequest req) {
        String errorId = ApiError.generateErrorId();
        log.warn("Bad request error [{}]: {}", errorId, ex.getMessage());
        
        ApiError err = ApiError.createSanitized(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                req.getDescription(false)
        );
        
        return ResponseEntity.badRequest().body(err);
    }

    /**
     * Handle MaxUploadSizeExceededException for file uploads that exceed Spring's configured limits
     */
    /**
     * Handle file storage exceptions
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiError> handleFileStorageException(FileStorageException ex, WebRequest req) {
        String errorId = ApiError.generateErrorId();
        log.error("File storage error [{}]: {}", errorId, ex.getMessage(), ex);
        
        ApiError err = ApiError.createSanitized(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "File processing failed. Contact support with Error ID: " + errorId,
                req.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }

    /**
     * Handle authentication errors
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiError> handleAuthenticationError(AuthenticationCredentialsNotFoundException ex, WebRequest req) {
        log.warn("Authentication error: {}", ex.getMessage());
        
        ApiError err = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Authentication Required")
                .message("Valid authentication credentials are required")
                .path(req.getDescription(false))
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
    }

    /**
     * Handle access denied errors (authorization)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, WebRequest req) {
        log.warn("Access denied: {}", ex.getMessage());
        
        ApiError err = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Access Denied")
                .message("Insufficient permissions to access this resource")
                .path(req.getDescription(false))
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, 
            WebRequest req) {
        
        log.warn("File size exceeded: {}", ex.getMessage());
        
        ApiError err = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .error("Payload Too Large")
                .message("Uploaded file exceeds the maximum allowed size")
                .path(req.getDescription(false))
                .build();
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(err);
    }

    /**
     * ✅ CRITICAL: Sanitized generic exception handler
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onGeneric(Exception ex, WebRequest req) {
        String errorId = ApiError.generateErrorId();
        
        // ✅ SECURE: Log full details internally with error ID
        log.error("Unexpected error [{}]: {} | Path: {} | Exception: {}", 
                 errorId, ex.getMessage(), req.getDescription(false), ex.getClass().getSimpleName(), ex);
        
        // ✅ SECURE: Return sanitized response to client
        ApiError err = ApiError.createSanitized(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Contact support with Error ID: " + errorId,
                req.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
    
    /**
     * Sanitize field errors to prevent information disclosure
     */
    private String sanitizeFieldError(String field, String message) {
        // Remove potentially sensitive information from validation messages
        String sanitizedMessage = message
                .replaceAll("(?i)(database|sql|query|table|column)", "data")
                .replaceAll("(?i)(exception|error|stack)", "issue")
                .replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "[IP]") // IP addresses
                .replaceAll("\\b[A-Za-z]:\\\\[\\w\\\\]+", "[PATH]"); // Windows paths
        
        return field + ": " + sanitizedMessage;
    }
}
