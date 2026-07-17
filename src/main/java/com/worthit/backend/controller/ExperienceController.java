package com.worthit.backend.controller;

import com.worthit.backend.dto.CreateExperienceRequest;
import com.worthit.backend.dto.ExperienceSummary;
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
     * {@code GET /api/v1/experiences} — list published experiences filtered by company + role
     * (see {@code api-endpoints.md} §2.4), newest first. The {@code company} and {@code role}
     * query params are the company and role slugs; the optional {@code city} filters by location
     * slug. Returns {@code 404} (via {@code GlobalExceptionHandler}) if no active company or role
     * has the given slug, or the role is not offered at the company.
     */
    @GetMapping
    public PageResponse<ExperienceSummary> listExperiences(
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/v1/experiences company={} role={} city={} cursor={} limit={}",
                company, role, city, cursor, limit);
        return experienceService.listExperiences(company, role, city, cursor, limit);
    }

    /**
     * {@code POST /api/v1/experiences} — create a new experience (see {@code api-endpoints.md} §4.1).
     * The submission is persisted as {@code pending} and does not appear in read endpoints until
     * moderated/published. Returns {@code 201 Created} with the created experience (§2.4 shape), or
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
