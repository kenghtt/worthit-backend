package com.worthit.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthit.backend.config.ApiRateLimitProperties;
import com.worthit.backend.exception.ApiErrorResponse;
import com.worthit.backend.exception.RateLimitExceededException;
import com.worthit.backend.service.ClientIpResolver;
import com.worthit.backend.service.InMemoryRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;

@RequiredArgsConstructor
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final String GENERIC_MESSAGE = "Too many requests. Please try again later.";
    private static final String SUBMISSION_MESSAGE =
            "You've made several submission attempts recently. Please try again later.";

    private final ApiRateLimitProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final InMemoryRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !properties.isEnabled()
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || !(path.startsWith("/api/") || isProbe(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = clientIpResolver.resolve(request);
        try {
            if (request.getRequestURI().startsWith("/api/")) {
                apply("global", properties.getGlobal(), clientIp, GENERIC_MESSAGE);
            }
            applyEndpointLimit(request, clientIp);
            filterChain.doFilter(request, response);
        } catch (RateLimitExceededException ex) {
            writeRateLimitResponse(request, response, ex);
        }
    }

    private void applyEndpointLimit(HttpServletRequest request, String clientIp) {
        String path = request.getRequestURI();
        if (isProbe(path)) {
            apply("probes", properties.getProbes(), clientIp, GENERIC_MESSAGE);
        } else if (isSubmissionAttempt(request, path)) {
            apply("submission-attempts", properties.getSubmissionAttempts(), clientIp, SUBMISSION_MESSAGE);
        } else if (HttpMethod.GET.matches(request.getMethod()) && isSearch(path)) {
            apply("search", properties.getSearch(), clientIp, GENERIC_MESSAGE);
        } else if (HttpMethod.GET.matches(request.getMethod()) && path.startsWith("/api/v1/")) {
            apply("read", properties.getRead(), clientIp, GENERIC_MESSAGE);
        }
    }

    private void apply(String limitName, ApiRateLimitProperties.Limit limit,
                       String clientIp, String message) {
        rateLimiter.checkAndRecord(
                limitName,
                clientIp,
                limit.getMaxRequests(),
                limit.getWindow(),
                properties.getMaxTrackedBuckets(),
                message
        );
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response,
                                        RateLimitExceededException ex) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build());
    }

    private static boolean isSubmissionAttempt(HttpServletRequest request, String path) {
        return HttpMethod.POST.matches(request.getMethod())
                && ("/api/v1/feedback".equals(path) || "/api/v1/experiences".equals(path));
    }

    private static boolean isSearch(String path) {
        return "/api/v1/companies/search".equals(path);
    }

    private static boolean isProbe(String path) {
        return "/".equals(path) || "/api/hello".equals(path) || "/actuator/health".equals(path);
    }
}