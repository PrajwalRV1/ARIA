package com.company.interview.client;

/**
 * Exception thrown when communication with an external service fails
 */
public class ServiceCommunicationException extends RuntimeException {

    public ServiceCommunicationException(String message) {
        super(message);
    }

    public ServiceCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
