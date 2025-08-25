package com.company.interview.client;

/**
 * Exception thrown when communication with the Job Analysis service fails
 */
public class JobAnalysisException extends RuntimeException {

    public JobAnalysisException(String message) {
        super(message);
    }

    public JobAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
