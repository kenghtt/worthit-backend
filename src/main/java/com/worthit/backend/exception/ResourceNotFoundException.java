package com.worthit.backend.exception;

/** Thrown when a requested resource cannot be found. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
