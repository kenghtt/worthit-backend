package com.worthit.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Simple hello/health endpoint for the WorthIt backend starter.
 *
 * Exposes:
 *  - GET /api/hello : returns a small JSON payload, useful as a smoke test and a health probe.
 *
 * TODO(worthIt): replace or supplement with real domain endpoints (this is just a starter sanity check).
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class HelloController {

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        log.info("GET /api/hello");
        return Map.of(
                "app", "worthit-backend",
                "status", "ok",
                "message", "Hello from WorthIt backend"
        );
    }
}
