package com.insurance.exception;

/**
 * Thrown when the caller's input is malformed or fails a business validation rule
 * (missing required field, invalid amount, invalid date range, etc.).
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
