package com.worthit.backend.exception;

/** Raised when Turnstile verification is required but the backend is not configured for it. */
public class TurnstileConfigurationException extends RuntimeException {
    public TurnstileConfigurationException(String message) {
        super(message);
    }
}