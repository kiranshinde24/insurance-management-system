package com.insurance.exception;

/**
 * Thrown when an authenticated user tries to access a resource that isn't theirs
 * (e.g. a customer trying to download another customer's policy certificate).
 * Mapped to HTTP 403 by {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
