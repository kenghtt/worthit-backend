package com.worthit.backend.controller;

import com.worthit.backend.dto.CompanySummary;
import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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
}
