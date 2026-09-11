package com.worthit.backend.exception;

/** Raised when a Turnstile token is missing, expired, or rejected by Cloudflare. */
public class TurnstileVerificationException extends RuntimeException {
    public TurnstileVerificationException(String message) {
        super(message);
    }
}