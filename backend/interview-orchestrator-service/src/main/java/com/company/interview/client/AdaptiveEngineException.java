package com.company.interview.client;

/**
 * Exception thrown when communication with the Adaptive Engine service fails
 */
public class AdaptiveEngineException extends RuntimeException {

    public AdaptiveEngineException(String message) {
        super(message);
    }

    public AdaptiveEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
