package com.worthit.backend.service;

import com.worthit.backend.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class InMemoryRateLimiter {

    private static final int CLEANUP_INTERVAL = 256;
    private static final Duration BLOCKED_LOG_INTERVAL = Duration.ofMinutes(1);

    private final Map<BucketKey, Bucket> buckets = new LinkedHashMap<>();
    private final Map<String, Long> blockedByLimit = new LinkedHashMap<>();
    private long operations;
    private Instant nextBlockedLog = Instant.now().plus(BLOCKED_LOG_INTERVAL);

    public synchronized Reservation checkAndRecord(String limitName, String clientKey,
                                                   int maxRequests, Duration window,
                                                   int maxTrackedBuckets, String message) {
        Instant now = Instant.now();
        if (++operations % CLEANUP_INTERVAL == 0) {
            removeExpiredBuckets(now);
            logBlockedSummaryIfDue(now);
        }

        BucketKey bucketKey = new BucketKey(limitName, normalize(clientKey));
        Bucket bucket = buckets.get(bucketKey);
        if (bucket == null) {
            if (buckets.size() >= maxTrackedBuckets) {
                removeExpiredBuckets(now);
            }
            if (buckets.size() >= maxTrackedBuckets) {
                throwExceeded(limitName, 60, message, now);
            }
            bucket = new Bucket(window);
            buckets.put(bucketKey, bucket);
        }

        bucket.window = window;
        removeExpired(bucket.requests, now.minus(window));
        if (bucket.requests.size() >= maxRequests) {
            long retryAfterSeconds = secondsUntil(now, bucket.requests.getFirst().plus(window));
            throwExceeded(limitName, retryAfterSeconds, message, now);
        }

        bucket.requests.addLast(now);
        return new Reservation(limitName, bucketKey.clientKey(), now);
    }

    public synchronized void rollBack(Reservation reservation) {
        if (reservation == null) {
            return;
        }

        BucketKey bucketKey = new BucketKey(reservation.limitName(), reservation.clientKey());
        Bucket bucket = buckets.get(bucketKey);
        if (bucket == null) {
            return;
        }

        bucket.requests.remove(reservation.recordedAt());
        if (bucket.requests.isEmpty()) {
            buckets.remove(bucketKey);
        }
    }

    private void throwExceeded(String limitName, long retryAfterSeconds, String message, Instant now) {
        blockedByLimit.merge(limitName, 1L, Long::sum);
        logBlockedSummaryIfDue(now);
        throw new RateLimitExceededException(retryAfterSeconds, limitName, message);
    }

    private void removeExpiredBuckets(Instant now) {
        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            removeExpired(bucket.requests, now.minus(bucket.window));
            return bucket.requests.isEmpty();
        });
    }

    private void logBlockedSummaryIfDue(Instant now) {
        if (now.isBefore(nextBlockedLog)) {
            return;
        }
        if (!blockedByLimit.isEmpty()) {
            log.warn("Rate limiter blocked requests during the previous interval: {}", blockedByLimit);
            blockedByLimit.clear();
        }
        nextBlockedLog = now.plus(BLOCKED_LOG_INTERVAL);
    }

    private static void removeExpired(Deque<Instant> requests, Instant cutoff) {
        while (!requests.isEmpty() && !requests.getFirst().isAfter(cutoff)) {
            requests.removeFirst();
        }
    }

    private static long secondsUntil(Instant now, Instant availableAt) {
        long millis = Math.max(1, Duration.between(now, availableAt).toMillis());
        return Math.max(1, (millis + 999) / 1_000);
    }

    private static String normalize(String clientKey) {
        return clientKey == null || clientKey.isBlank() ? "unknown" : clientKey;
    }

    public record Reservation(String limitName, String clientKey, Instant recordedAt) {
    }

    private record BucketKey(String limitName, String clientKey) {
    }

    private static final class Bucket {
        private final Deque<Instant> requests = new ArrayDeque<>();
        private Duration window;

        private Bucket(Duration window) {
            this.window = window;
        }
    }
}