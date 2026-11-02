package com.insurance.exception;

/**
 * Thrown when authentication itself fails (bad credentials, invalid/expired token).
 * Mapped to HTTP 401 by {@link GlobalExceptionHandler}.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
