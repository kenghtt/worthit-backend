package com.worthit.backend.controller;

import com.worthit.backend.dto.CompanyDetail;
import com.worthit.backend.dto.CompanySummary;
import com.worthit.backend.dto.ExperienceSummary;
import com.worthit.backend.dto.LevelSummary;
import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.dto.RoleSummary;
import com.worthit.backend.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints for companies (see {@code api-endpoints.md} §2).
 */
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {

    private final CompanyService companyService;

    /**
     * {@code GET /api/v1/companies} — list / search companies (see {@code api-endpoints.md} §2.1).
     * All query params are optional.
     */
    @GetMapping
    public PageResponse<CompanySummary> listCompanies(
            @RequestParam(required = false) String companySubstring,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/v1/companies q={} industry={} sort={} order={} cursor={} limit={}",
                companySubstring, industry, sort, order, cursor, limit);
        return companyService.listCompanies(companySubstring, industry, sort, order, cursor, limit);
    }

    /**
     * {@code GET /api/v1/companies/search} — lightweight typeahead for the search bar
     * (see {@code api-endpoints.md} §2.5). Returns a single capped page of matching companies'
     * basic profiles; a blank/missing {@code q} yields an empty page.
     *
     * <p>The literal {@code /search} segment is matched ahead of the {@code /{slug}} mapping
     * below, so it is never treated as a company slug.</p>
     */
    @GetMapping("/search")
    public PageResponse<CompanyDetail> searchCompanies(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/v1/companies/search q={} limit={}", q, limit);
        return companyService.searchCompanies(q, limit);
    }

    /**
     * {@code GET /api/v1/companies/{slug}} — single company detail (see {@code api-endpoints.md} §2.2).
     * Returns {@code 404} (via {@code GlobalExceptionHandler}) if no active company has the slug.
     */
    @GetMapping("/{slug}")
    public CompanyDetail getCompany(@PathVariable String slug) {
        log.debug("GET /api/v1/companies/{}", slug);
        return companyService.getCompany(slug);
    }

    /**
     * {@code GET /api/v1/companies/{slug}/roles} — roles available at a company, each with
     * per-role aggregate stats (see {@code api-endpoints.md} §2.3). Returns {@code 404}
     * (via {@code GlobalExceptionHandler}) if no active company has the slug.
     */
    @GetMapping("/{slug}/roles")
    public PageResponse<RoleSummary> listCompanyRoles(
            @PathVariable String slug,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/v1/companies/{}/roles cursor={} limit={}", slug, cursor, limit);
        return companyService.listCompanyRoles(slug, cursor, limit);
    }

    /**
     * {@code GET /api/v1/companies/{slug}/levels} — per-company level options for the submit-form
     * level picker (see {@code api-endpoints.md} §5), ordered by {@code normalizedRank}. Returns
     * {@code 404} (via {@code GlobalExceptionHandler}) if no active company has the slug.
     */
    @GetMapping("/{slug}/levels")
    public PageResponse<LevelSummary> listCompanyLevels(
            @PathVariable String slug,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/v1/companies/{}/levels cursor={} limit={}", slug, cursor, limit);
        return companyService.listCompanyLevels(slug, cursor, limit);
    }

    /**
     * {@code GET /api/v1/companies/{slug}/roles/{roleSlug}/experiences} — published experiences
     * for a company + role, newest first (see {@code api-endpoints.md} §2.4). Optional {@code city}
     * filters by location slug. Returns {@code 404} (via {@code GlobalExceptionHandler}) if no
     * active company or role has the given slug.
     */
    @GetMapping("/{slug}/roles/{roleSlug}/experiences")
    public PageResponse<ExperienceSummary> listExperiences(
            @PathVariable String slug,
            @PathVariable String roleSlug,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/v1/companies/{}/roles/{}/experiences city={} cursor={} limit={}",
                slug, roleSlug, city, cursor, limit);
        return companyService.listExperiences(slug, roleSlug, city, cursor, limit);
    }
}
