package com.worthit.backend.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Generic API error envelope returned by {@link GlobalExceptionHandler}.
 *
 * Keep this intentionally minimal and product-agnostic so it can be reused for every endpoint.
 */
@Getter
@Builder
public class ApiErrorResponse {
    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    /** Optional field-level errors (e.g. for bean-validation failures). */
    private List<String> details;
}
