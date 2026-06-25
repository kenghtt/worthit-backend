package com.worthit.backend.controller;

import com.worthit.backend.dto.LocationSummary;
import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints for locations (see {@code api-endpoints.md} §3).
 */
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Slf4j
public class LocationController {

    private final LocationService locationService;

    /**
     * {@code GET /api/v1/locations} — list / search locations with per-city aggregate stats
     * (see {@code api-endpoints.md} §3.1). All query params are optional; {@code q} is a
     * case-insensitive substring match on the city name.
     */
    @GetMapping
    public PageResponse<LocationSummary> listLocations(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/v1/locations q={} cursor={} limit={}", q, cursor, limit);
        return locationService.listLocations(q, cursor, limit);
    }

    /**
     * {@code GET /api/v1/locations/{slug}} — single location detail with per-city aggregate stats
     * (see {@code api-endpoints.md} §3.2). Returns {@code 404} (via {@code GlobalExceptionHandler})
     * if no active location has the slug.
     */
    @GetMapping("/{slug}")
    public LocationSummary getLocation(@PathVariable String slug) {
        log.debug("GET /api/v1/locations/{}", slug);
        return locationService.getLocation(slug);
    }
}
