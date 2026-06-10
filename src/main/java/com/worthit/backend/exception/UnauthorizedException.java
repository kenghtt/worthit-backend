package com.worthit.backend.exception;

/** Thrown when a request is not authenticated or its token is invalid. Mapped to HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
