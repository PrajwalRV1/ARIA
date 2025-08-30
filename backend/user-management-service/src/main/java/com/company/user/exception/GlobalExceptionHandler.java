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
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex, WebRequest req) {
        ApiError err = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation failed")
                .message("Invalid request")
                .path(req.getDescription(false))
                .details(ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .collect(Collectors.toList()))
                .build();
        return ResponseEntity.badRequest().body(err);
    }

    @ExceptionHandler({ IllegalArgumentException.class, BadRequestException.class })
    public ResponseEntity<ApiError> onBadArg(RuntimeException ex, WebRequest req) {
        ApiError err = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .path(req.getDescription(false))
                .build();
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
        log.error("File storage error: {}", ex.getMessage(), ex);
        
        ApiError err = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("File Processing Error")
                .message("Failed to process uploaded file")
                .path(req.getDescription(false))
                .build();
        
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onGeneric(Exception ex, WebRequest req) {
        // Log full error details internally for debugging
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        // Return sanitized error response to prevent information leakage
        ApiError err = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .path(req.getDescription(false))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}
