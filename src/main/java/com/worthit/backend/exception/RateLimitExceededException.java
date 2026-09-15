package com.worthit.backend.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;
    private final String limitName;

    public RateLimitExceededException(long retryAfterSeconds, String limitName, String message) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.limitName = limitName;
    }
}