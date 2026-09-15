package com.worthit.backend.service;

import com.worthit.backend.config.FeedbackProperties;
import com.worthit.backend.exception.FeedbackRateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackRateLimiter {

    private final FeedbackProperties properties;
    private final Map<String, Deque<Instant>> submissionsByIp = new HashMap<>();

    public synchronized Instant checkAndRecordSubmission(String remoteIp) {
        Instant now = Instant.now();
        Duration window = properties.getWindow();
        Instant cutoff = now.minus(window);

        submissionsByIp.values().removeIf(submissions -> {
            removeExpired(submissions, cutoff);
            return submissions.isEmpty();
        });

        String key = normalizeRemoteIp(remoteIp);
        Deque<Instant> submissions = submissionsByIp.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        if (submissions.size() >= properties.getMaxSubmissions()) {
            long retryAfterSeconds = Math.max(1,
                    Duration.between(now, submissions.getFirst().plus(window)).getSeconds());
            throw new FeedbackRateLimitExceededException(retryAfterSeconds);
        }

        submissions.addLast(now);
        return now;
    }

    public synchronized void rollBackSubmission(String remoteIp, Instant submissionTime) {
        String key = normalizeRemoteIp(remoteIp);
        Deque<Instant> submissions = submissionsByIp.get(key);
        if (submissions == null) return;

        submissions.remove(submissionTime);
        if (submissions.isEmpty()) {
            submissionsByIp.remove(key);
        }
    }

    private static void removeExpired(Deque<Instant> submissions, Instant cutoff) {
        while (!submissions.isEmpty() && !submissions.getFirst().isAfter(cutoff)) {
            submissions.removeFirst();
        }
    }

    private static String normalizeRemoteIp(String remoteIp) {
        return remoteIp == null || remoteIp.isBlank() ? "unknown" : remoteIp;
    }
}
