package com.worthit.backend.controller;

import com.worthit.backend.dto.CreateExperienceRequest;
import com.worthit.backend.dto.ExperienceFilterOptions;
import com.worthit.backend.dto.ExperienceSummary;
import com.worthit.backend.dto.ExperienceStatsSummary;
import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.service.ExperienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Write endpoint for experiences (see {@code api-endpoints.md} §4).
 */
@RestController
@RequestMapping("/api/v1/experiences")
@RequiredArgsConstructor
@Slf4j
public class ExperienceController {

    private final ExperienceService experienceService;

    /**
     * {@code GET /api/v1/experiences} — list active experiences filtered by company + role
     * (see {@code api-endpoints.md} §2.4), newest first. The {@code company} and {@code role}
     * query params are the company and role slugs; optional {@code level}, {@code city}, and
     * {@code state} filters are applied before pagination. City and state must be supplied
     * together. Returns {@code 404} (via {@code GlobalExceptionHandler}) if no active company or
     * role has the given slug.
     */
    @GetMapping
    public PageResponse<ExperienceSummary> listExperiences(
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/v1/experiences company={} role={} level={} city={} state={} cursor={} limit={}",
                company, role, level, city, state, cursor, limit);
        return experienceService.listExperiences(company, role, level, city, state, cursor, limit);
    }

    /** Complete level and city/state choices for the paginated experiences table. */
    @GetMapping("/filter-options")
    public ExperienceFilterOptions getExperienceFilterOptions(
            @RequestParam String company,
            @RequestParam String role) {
        log.debug("GET /api/v1/experiences/filter-options company={} role={}", company, role);
        return experienceService.getExperienceFilterOptions(company, role);
    }

    /**
     * {@code GET /api/v1/experiences/stats} — aggregate active experience stats for the role
     * experiences page. Company and role are required slugs; level is required and location is an
     * optional refinement used only alongside that level filter.
     */
    @GetMapping("/stats")
    public ExperienceStatsSummary getExperienceStats(
            @RequestParam String company,
            @RequestParam String role,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state) {
        log.debug("GET /api/v1/experiences/stats company={} role={} level={} city={} state={}",
                company, role, level, city, state);
        return experienceService.getExperienceStats(company, role, level, city, state);
    }

    /**
     * {@code POST /api/v1/experiences} — create a new experience (see {@code api-endpoints.md} §4.1).
     * The submission is persisted as inactive and does not appear in read endpoints until activated.
     * Returns {@code 201 Created} with the created experience (§2.4 shape), or
     * {@code 400} with validation {@code details} (via {@code GlobalExceptionHandler}).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExperienceSummary createExperience(@Valid @RequestBody CreateExperienceRequest request) {
        log.debug("POST /api/v1/experiences company={} companySlug={} role={} customRole={} city={}",
                request.company(), request.companySlug(), request.role(), request.customRole(), request.city());
        return experienceService.createExperience(request);
    }
}
