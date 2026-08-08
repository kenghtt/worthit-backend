package com.worthit.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness probe for the WorthIt backend (see {@code api-endpoints.md} §6.1).
 *
 * <p>{@code GET /api/hello} returns a small JSON payload confirming the JVM and Spring context
 * are up. Does not verify database connectivity — suitable for smoke tests and container
 * liveness probes, not deep readiness checks.</p>
 */
@RestController
@Slf4j
public class HelloController {

    @GetMapping("/")
    public Map<String, Object> root() {
        log.debug("GET /");
        return Map.of(
                "app", "worthit-backend",
                "status", "ok",
                "message", "WorthIt backend is running",
                "hello", "/api/hello"
        );
    }

    @GetMapping("/api/hello")
    public Map<String, Object> hello() {
        log.info("GET /api/hello");
        return Map.of(
                "app", "worthit-backend",
                "status", "ok",
                "message", "Hello from WorthIt backend"
        );
    }
}
