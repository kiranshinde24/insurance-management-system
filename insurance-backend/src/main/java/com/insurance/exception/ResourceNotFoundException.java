package com.insurance.exception;

/**
 * Thrown when a requested entity (customer, policy, claim, agent, ...) doesn't exist.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
