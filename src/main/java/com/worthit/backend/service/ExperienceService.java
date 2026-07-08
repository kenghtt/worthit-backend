package com.worthit.backend.service;

import com.worthit.backend.dto.CreateExperienceRequest;
import com.worthit.backend.dto.ExperienceSummary;
import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.entity.Company;
import com.worthit.backend.entity.CompanyRole;
import com.worthit.backend.entity.EmploymentStatus;
import com.worthit.backend.entity.Experience;
import com.worthit.backend.entity.ExperienceStatus;
import com.worthit.backend.entity.Level;
import com.worthit.backend.entity.Location;
import com.worthit.backend.entity.Role;
import com.worthit.backend.repository.CompanyRepository;
import com.worthit.backend.repository.CompanyRoleRepository;
import com.worthit.backend.repository.ExperienceRepository;
import com.worthit.backend.repository.LevelRepository;
import com.worthit.backend.repository.LocationRepository;
import com.worthit.backend.repository.RoleRepository;
import com.worthit.backend.exception.ResourceNotFoundException;
import com.worthit.backend.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Write-side logic for the submit-experience endpoint (see {@code api-endpoints.md} §4.1).
 *
 * <p>Resolves the submitted company / role / location, creating any that don't yet exist
 * (find-or-create, mirroring the seeder's upsert-by-slug approach in {@code DataSeeder}). Since new
 * experiences are persisted as {@code pending} (see {@code database-spec.md} §9), they — and any
 * companies/roles created alongside them — stay out of the public read endpoints until a published
 * experience exists. Records the experience and returns it in the §2.4 shape.</p>
 */
@Service
@RequiredArgsConstructor
public class ExperienceService {

    /** Default page size when {@code limit} is omitted (see §1). */
    static final int DEFAULT_LIMIT = 20;
    /** Maximum allowed page size (see §1). */
    static final int MAX_LIMIT = 50;

    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final LocationRepository locationRepository;
    private final LevelRepository levelRepository;
    private final CompanyRoleRepository companyRoleRepository;
    private final ExperienceRepository experienceRepository;

    /**
     * Lists the {@code published} experiences for a company + role (see {@code api-endpoints.md}
     * §2.4), newest first, optionally filtered to a single city (by location slug). Each item
     * mirrors the {@code experience} DB columns (see {@link ExperienceSummary}).
     *
     * <p>Company and role are supplied as slugs; the matching rows are sorted and paged in
     * memory.</p>
     *
     * @throws ResourceNotFoundException if no active company or role with the given slug exists,
     *                                   or the role is not offered at the company
     */
    @Transactional(readOnly = true)
    public PageResponse<ExperienceSummary> listExperiences(String slug, String roleSlug, String city,
                                                           String cursor, Integer limit) {
        Company company = companyRepository.findBySlug(slug)
                .filter(Company::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + slug));
        Role role = roleRepository.findBySlug(roleSlug)
                .filter(Role::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleSlug));
        if (!companyRoleRepository.existsByCompany_IdAndRole_Id(company.getId(), role.getId())) {
            throw new ResourceNotFoundException(
                    "Role not offered at company: " + slug + "/" + roleSlug);
        }

        int pageSize = normalizeLimit(limit);
        String citySlug = (city == null || city.isBlank()) ? null : city.trim();

        // Newest first, with id as a stable tiebreaker so cursor paging is deterministic.
        Comparator<Experience> newestFirst = Comparator
                .comparing(Experience::getCreatedAt)
                .thenComparing(Experience::getId)
                .reversed();

        List<ExperienceSummary> all = experienceRepository
                .findForCompanyRole(company.getId(), role.getId(), ExperienceStatus.published, true)
                .stream()
                .filter(e -> citySlug == null || citySlug.equalsIgnoreCase(e.getLocation().getSlug()))
                .sorted(newestFirst)
                .map(ExperienceSummary::from)
                .toList();

        return paginate(all, cursor, pageSize);
    }

    /**
     * Creates a new {@code pending} experience from the submit form (see {@code api-endpoints.md}
     * §4.1). Bean Validation on {@link CreateExperienceRequest} has already run; this method handles
     * the remaining domain mapping and entity resolution.
     *
     * @throws IllegalArgumentException if {@code employmentStatus} is not {@code current}/{@code former}
     *                                  (surfaced as {@code 400} via {@code GlobalExceptionHandler})
     */
    @Transactional
    public ExperienceSummary createExperience(CreateExperienceRequest req) {
        Company company = resolveCompany(req);
        Role role = resolveRole(req);
        linkCompanyRoleIfMissing(company, role);
        Location location = resolveLocation(req);

        Level level = (req.level() == null || req.level().isBlank()) ? null
                : levelRepository.findByCompany_IdAndName(company.getId(), req.level().trim()).orElse(null);

        Experience experience = Experience.builder()
                .company(company)
                .role(role)
                .location(location)
                .level(level)
                .levelName(blankToNull(req.level()))
                .employmentStatus(mapEmploymentStatus(req.employmentStatus()))
                .yearsExperience(req.yearsExperience())
                .yearsAtCompany(req.yearsAtCompany())
                .baseSalary(req.baseSalary())
                .bonus(zeroIfNull(req.bonus()))
                .stock(zeroIfNull(req.stock()))
                .signingBonus(zeroIfNull(req.signingBonus()))
                .compensationYear(req.compensationYear())
                .stressLevel(req.stressLevel())
                .hoursPerWeek(req.hoursPerWeek())
                .worthItScore(req.worthItScore())
                .whyStay(blankToNull(req.whyStay()))
                .whyLeave(blankToNull(req.whyLeave()))
                .wishKnew(blankToNull(req.wishKnew()))
                .status(ExperienceStatus.pending)
                .active(false)
                .build();

        return ExperienceSummary.from(experienceRepository.save(experience));
    }

    /**
     * Resolves the company by {@code companySlug} (or the slugified display name), creating an
     * active company if none exists yet.
     */
    private Company resolveCompany(CreateExperienceRequest req) {
        String slug = isPresent(req.companySlug()) ? req.companySlug().trim()
                : SlugUtil.slugify(req.company());
        String displayName = isPresent(req.company()) ? req.company().trim() : slug;
        return companyRepository.findBySlug(slug)
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .slug(slug)
                        .name(displayName)
                        .active(true)
                        .build()));
    }

    /**
     * Resolves the role by {@code roleSlug} (or the slugified display name / custom role), creating
     * an active global role if none exists yet. {@code customRole} takes precedence as the display
     * name when the user typed a role not in the list.
     */
    private Role resolveRole(CreateExperienceRequest req) {
        String displayName = isPresent(req.customRole()) ? req.customRole().trim()
                : (isPresent(req.role()) ? req.role().trim() : null);
        String slug = isPresent(req.roleSlug()) ? req.roleSlug().trim()
                : SlugUtil.slugify(displayName);
        String name = displayName != null ? displayName : slug;
        return roleRepository.findBySlug(slug)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .slug(slug)
                        .name(name)
                        .active(true)
                        .build()));
    }

    /** Ensures the role is offered at the company (see {@code database-spec.md} §5). */
    private void linkCompanyRoleIfMissing(Company company, Role role) {
        if (!companyRoleRepository.existsByCompany_IdAndRole_Id(company.getId(), role.getId())) {
            companyRoleRepository.save(CompanyRole.builder()
                    .company(company)
                    .role(role)
                    .active(true)
                    .build());
        }
    }

    /**
     * Resolves the location by {@code city}/{@code state}, creating an active location if none
     * exists yet. The slug matches the seeder's {@code slugify(city + " " + state)} convention.
     */
    private Location resolveLocation(CreateExperienceRequest req) {
        String city = req.city().trim();
        String state = isPresent(req.state()) ? req.state().trim() : "";
        return locationRepository.findByCityAndState(city, state)
                .orElseGet(() -> locationRepository.save(Location.builder()
                        .slug(SlugUtil.slugify(city + " " + state))
                        .city(city)
                        .state(state)
                        .active(true)
                        .build()));
    }

    /**
     * Maps the API contract's {@code employmentStatus} ({@code current}/{@code former}) to the DB
     * enum ({@code current}/{@code past}); note {@code former} maps to {@link EmploymentStatus#past}.
     */
    private static EmploymentStatus mapEmploymentStatus(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "current" -> EmploymentStatus.current;
            case "former", "past" -> EmploymentStatus.past;
            default -> throw new IllegalArgumentException(
                    "employmentStatus must be one of: current, former");
        };
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * Applies offset cursor pagination to an already filtered/sorted list: slices out the page
     * starting at the decoded {@code cursor} offset and computes the {@code next_cursor}
     * (see {@code api-endpoints.md} §1 "Pagination"). An out-of-range/exhausted offset yields an
     * empty page with a {@code null} cursor.
     */
    private static <T> PageResponse<T> paginate(List<T> all, String cursor, int pageSize) {
        int offset = decodeCursor(cursor);
        if (offset < 0 || offset >= all.size()) {
            return new PageResponse<>(List.of(), null);
        }
        int end = Math.min(offset + pageSize, all.size());
        String nextCursor = end < all.size() ? encodeCursor(end) : null;
        return new PageResponse<>(all.subList(offset, end), nextCursor);
    }

    private static String encodeCursor(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("o:" + offset).getBytes(StandardCharsets.UTF_8));
    }

    private static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (decoded.startsWith("o:")) {
                return Integer.parseInt(decoded.substring(2));
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed cursor — fall through to treat as start.
        }
        return 0;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return isPresent(value) ? value.trim() : null;
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
