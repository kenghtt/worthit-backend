package com.worthit.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthit.backend.config.PostHogProperties;
import com.worthit.backend.dto.ExperienceSummary;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SubmissionAnalyticsService {

    public static final String SUBMISSION_ID_HEADER = "X-WorthIt-Submission-Id";
    public static final String DISTINCT_ID_HEADER = "X-PostHog-Distinct-Id";

    private static final String SUBMISSION_PATH = "/api/v1/experiences";
    private static final int MAX_DISTINCT_ID_LENGTH = 200;

    private final PostHogProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SubmissionAnalyticsService(PostHogProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public void captureSuccess(HttpServletRequest request, ExperienceSummary experience) {
        if (!isSubmission(request)) {
            return;
        }

        Map<String, Object> eventProperties = new LinkedHashMap<>();
        eventProperties.put("company_slug", experience.companySlug());
        eventProperties.put("role_slug", experience.roleSlug());
        eventProperties.put("experience_id", experience.id());
        eventProperties.put("active", experience.active());
        capture("backend_experience_submitted", request, eventProperties);
    }

    public void captureFailure(HttpServletRequest request, String failureType, HttpStatus status) {
        if (!isSubmission(request)) {
            return;
        }

        capture("backend_experience_submission_failed", request, Map.of(
                "failure_type", failureType,
                "http_status", status.value()
        ));
    }

    private void capture(String event, HttpServletRequest request, Map<String, Object> eventProperties) {
        if (!StringUtils.hasText(properties.getProjectToken()) || !StringUtils.hasText(properties.getHost())) {
            return;
        }

        AnalyticsContext context = analyticsContext(request);
        Map<String, Object> combinedProperties = new LinkedHashMap<>();
        combinedProperties.put("$process_person_profile", false);
        combinedProperties.put("source", "backend");
        combinedProperties.put("submission_id", context.submissionId());
        combinedProperties.putAll(eventProperties);

        Map<String, Object> payload = Map.of(
                "api_key", properties.getProjectToken(),
                "event", event,
                "distinct_id", context.distinctId(),
                "properties", combinedProperties
        );

        try {
            HttpRequest postHogRequest = HttpRequest.newBuilder(captureUri())
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            httpClient.sendAsync(postHogRequest, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            log.warn("PostHog submission event could not be delivered: {}", error.getMessage());
                        } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            log.warn("PostHog submission event returned HTTP {}", response.statusCode());
                        }
                    });
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.warn("PostHog submission event could not be prepared: {}", ex.getMessage());
        }
    }

    private AnalyticsContext analyticsContext(HttpServletRequest request) {
        String submissionId = validSubmissionId(request.getHeader(SUBMISSION_ID_HEADER));
        String distinctId = request.getHeader(DISTINCT_ID_HEADER);
        if (!StringUtils.hasText(distinctId) || distinctId.trim().length() > MAX_DISTINCT_ID_LENGTH) {
            distinctId = "backend-" + submissionId;
        } else {
            distinctId = distinctId.trim();
        }
        return new AnalyticsContext(submissionId, distinctId);
    }

    private String validSubmissionId(String candidate) {
        if (StringUtils.hasText(candidate)) {
            try {
                return UUID.fromString(candidate.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // Replace malformed client input with a safe uncorrelated identifier.
            }
        }
        return UUID.randomUUID().toString();
    }

    private URI captureUri() {
        return URI.create(properties.getHost().replaceAll("/+$", "") + "/i/v0/e");
    }

    private boolean isSubmission(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && SUBMISSION_PATH.equals(request.getRequestURI());
    }

    private record AnalyticsContext(String submissionId, String distinctId) {
    }
}