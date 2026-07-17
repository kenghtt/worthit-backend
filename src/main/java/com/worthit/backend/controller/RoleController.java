package com.worthit.backend.controller;

import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.dto.RoleLookupSummary;
import com.worthit.backend.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reference / lookup endpoints for roles (see {@code api-endpoints.md} §5).
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {

    private final RoleService roleService;

    /**
     * {@code GET /api/v1/roles} — global list of roles for the submit-form role picker
     * (see {@code api-endpoints.md} §5). Returns {@code slug}, {@code name}, and {@code family}
     * only (no aggregate stats). Results are name-sorted and cursor-paged.
     */
    @GetMapping
    public PageResponse<RoleLookupSummary> listRoles(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/v1/roles cursor={} limit={}", cursor, limit);
        return roleService.listRoles(cursor, limit);
    }
}
