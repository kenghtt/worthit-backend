package com.worthit.backend.exception;

import com.worthit.backend.service.SubmissionAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Generic, product-agnostic error handler.
 *
 * <p>Maps common exceptions to a consistent {@link ApiErrorResponse} payload so all endpoints
 * return uniform error JSON. Add worthIt-specific exception mappings as new domains are introduced.</p>
 *
 * <p>TODO(worthIt): add domain-specific exception handlers here (e.g. business-rule violations).</p>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final SubmissionAnalyticsService submissionAnalyticsService;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", "Requested resource was not found", request, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized request at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required or token is invalid", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to access this resource", request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        log.warn("Validation failed at {}: {}", request.getRequestURI(), details);
        submissionAnalyticsService.captureFailure(request, "validation", HttpStatus.BAD_REQUEST);
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        log.warn("Constraint violation at {}: {}", request.getRequestURI(), details);
        submissionAnalyticsService.captureFailure(request, "validation", HttpStatus.BAD_REQUEST);
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed", request, details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument at {}: {}", request.getRequestURI(), ex.getMessage());
        submissionAnalyticsService.captureFailure(request, "domain_validation", HttpStatus.BAD_REQUEST);
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid request", request, null);
    }

    @ExceptionHandler(TurnstileVerificationException.class)
    public ResponseEntity<ApiErrorResponse> handleTurnstileVerification(TurnstileVerificationException ex,
                                                                        HttpServletRequest request) {
        log.warn("Turnstile verification failed at {}: {}", request.getRequestURI(), ex.getMessage());
        submissionAnalyticsService.captureFailure(request, "turnstile_rejected", HttpStatus.BAD_REQUEST);
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Turnstile verification failed", request,
                List.of("turnstileToken: " + ex.getMessage()));
    }

    @ExceptionHandler(TurnstileConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleTurnstileConfiguration(TurnstileConfigurationException ex,
                                                                         HttpServletRequest request) {
        log.error("Turnstile configuration error at {}: {}", request.getRequestURI(), ex.getMessage());
        submissionAnalyticsService.captureFailure(request, "turnstile_unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "Submission verification is temporarily unavailable", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        submissionAnalyticsService.captureFailure(request, "internal", HttpStatus.INTERNAL_SERVER_ERROR);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String error, String message,
                                                   HttpServletRequest request, List<String> details) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .details(details)
                .build();
        return new ResponseEntity<>(body, status);
    }
}
