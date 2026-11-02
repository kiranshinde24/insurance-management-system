package com.insurance.exception;

/**
 * Thrown when a request conflicts with the current state of a resource
 * (duplicate username, PAN already registered, deleting a policy type still in use, etc.).
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
