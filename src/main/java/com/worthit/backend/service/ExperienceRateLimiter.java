package com.worthit.backend.service;

import com.worthit.backend.config.ApiRateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExperienceRateLimiter {

    private static final String LIMIT_NAME = "experience-success";

    private final ApiRateLimitProperties properties;
    private final InMemoryRateLimiter rateLimiter;

    public InMemoryRateLimiter.Reservation checkAndRecordSubmission(String clientIp) {
        ApiRateLimitProperties.Limit limit = properties.getSuccessfulExperienceSubmissions();
        return rateLimiter.checkAndRecord(
                LIMIT_NAME,
                clientIp,
                limit.getMaxRequests(),
                limit.getWindow(),
                properties.getMaxTrackedBuckets(),
                "You've submitted several experiences recently. Please try again later."
        );
    }

    public void rollBackSubmission(InMemoryRateLimiter.Reservation reservation) {
        rateLimiter.rollBack(reservation);
    }
}