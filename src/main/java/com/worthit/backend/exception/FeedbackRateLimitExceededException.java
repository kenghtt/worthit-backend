package com.worthit.backend.exception;

import lombok.Getter;

@Getter
public class FeedbackRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public FeedbackRateLimitExceededException(long retryAfterSeconds) {
        super("You've submitted several messages recently. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
