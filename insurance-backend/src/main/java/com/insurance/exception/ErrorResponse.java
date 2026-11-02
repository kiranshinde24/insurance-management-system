package com.insurance.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform JSON shape returned for every error response, instead of a raw stack trace.
 *
 * Example:
 * {
 *   "timestamp": "2026-07-16T10:15:30Z",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Policy not found",
 *   "path": "/api/admin/policies/99"
 * }
 */
@Getter
@Builder
@Schema(description = "Standard error response returned for all failed requests")
public class ErrorResponse {

    @Schema(description = "When the error occurred", example = "2026-07-16T10:15:30Z")
    private final Instant timestamp;

    @Schema(description = "HTTP status code", example = "404")
    private final int status;

    @Schema(description = "HTTP status reason phrase", example = "Not Found")
    private final String error;

    @Schema(description = "Human-readable error message", example = "Policy not found")
    private final String message;

    @Schema(description = "The request path that caused the error", example = "/api/admin/policies/99")
    private final String path;

    @Schema(description = "Field-level validation errors, present only for 400 responses caused by @Valid failures")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Map<String, String> fieldErrors;
}
