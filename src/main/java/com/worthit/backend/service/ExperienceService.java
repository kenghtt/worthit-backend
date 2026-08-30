package com.worthit.backend.service;

import com.worthit.backend.dto.CreateExperienceRequest;
import com.worthit.backend.dto.ExperienceSummary;
import com.worthit.backend.dto.ExperienceStatsSummary;
import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.entity.Company;
import com.worthit.backend.entity.EmploymentStatus;
import com.worthit.backend.entity.Experience;
import com.worthit.backend.entity.Level;
import com.worthit.backend.entity.Location;
import com.worthit.backend.entity.Role;
import com.worthit.backend.repository.CompanyRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Write-side logic for the submit-experience endpoint (see {@code api-endpoints.md} §4.1).
 *
 * <p>Resolves the submitted company / role / location, creating any that don't yet exist
 * (find-or-create, mirroring the seeder's upsert-by-slug approach in {@code DataSeeder}). New
 * experiences are persisted as inactive, so they stay out of public read endpoints until someone
 * explicitly activates them. Records the experience and returns it in the §2.4 shape.</p>
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
    private final ExperienceRepository experienceRepository;

    /**
     * Lists the active experiences for a company + role (see {@code api-endpoints.md}
     * §2.4), newest first, optionally filtered to a single city (by location slug). Each item
     * mirrors the {@code experience} DB columns (see {@link ExperienceSummary}).
     *
     * <p>Company and role are supplied as slugs; the matching rows are sorted and paged in
     * memory.</p>
     *
     * @throws ResourceNotFoundException if no active company or no role with the given slug exists
     */
    @Transactional(readOnly = true)
    public PageResponse<ExperienceSummary> listExperiences(String slug, String roleSlug, String city,
                                                           String cursor, Integer limit) {
        Company company = companyRepository.findBySlug(slug)
                .filter(Company::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + slug));
        Role role = roleRepository.findBySlug(roleSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleSlug));
        int pageSize = normalizeLimit(limit);
        String citySlug = (city == null || city.isBlank()) ? null : city.trim();

        // Newest first, with id as a stable tiebreaker so cursor paging is deterministic.
        Comparator<Experience> newestFirst = Comparator
                .comparing(Experience::getCreatedAt)
                .thenComparing(Experience::getId)
                .reversed();

        List<ExperienceSummary> all = experienceRepository
                .findForCompanyRole(company.getId(), role.getId(), true)
                .stream()
                .filter(e -> citySlug == null || citySlug.equalsIgnoreCase(e.getLocation().getSlug()))
                .sorted(newestFirst)
                .map(ExperienceSummary::from)
                .toList();

        return paginate(all, cursor, pageSize);
    }

    /**
     * Computes aggregate stats across all matching active experiences for the role experiences
     * page. Company and role are required slugs; level is required and location may further
     * narrow the result only when level is present.
     */
    @Transactional(readOnly = true)
    public ExperienceStatsSummary getExperienceStats(String companySlug, String roleSlug,
                                                     String level, String location) {
        String levelName = normalizeStatsFilter(level);
        if (levelName == null) {
            throw new IllegalArgumentException("level is required when requesting experience stats");
        }
        String locationCity = normalizeStatsFilter(location);

        Company company = companyRepository.findBySlug(companySlug)
                .filter(Company::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companySlug));
        Role role = roleRepository.findBySlug(roleSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleSlug));

        var stats = locationCity == null
                ? experienceRepository.aggregateForCompanyRoleAndLevel(company.getId(), role.getId(), levelName)
                : experienceRepository.aggregateForCompanyRoleAndLevelAndLocation(
                        company.getId(), role.getId(), levelName, locationCity);

        long count = stats.getExperienceCount();
        return new ExperienceStatsSummary(
                count,
                scaleScore(stats.getAvgWorthScore()),
                scaleScore(stats.getAvgStress()),
                roundCurrency(stats.getAvgTotalComp())
        );
    }

    private String normalizeStatsFilter(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * Creates a new inactive experience from the submit form (see {@code api-endpoints.md}
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
        Location location = resolveLocation(req);

        Level level = resolveLevel(company, req.level());

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
                .worthItReason(blankToNull(req.worthItReason()))
                .active(false)
                .build();

        return ExperienceSummary.from(experienceRepository.save(experience));
    }

    /**
     * Resolves a company-specific level by exact submitted name, creating a new inactive row when
     * the user provides a level that does not yet exist. Newly created levels append after the
     * highest saved rank for the company; if the company has no levels yet, they append after the
     * backend default fallback ladder.
     */
    private Level resolveLevel(Company company, String requestedLevel) {
        String levelName = blankToNull(requestedLevel);
        if (levelName == null) {
            return null;
        }

        return levelRepository.findByCompany_IdAndName(company.getId(), levelName)
                .orElseGet(() -> levelRepository.save(Level.builder()
                        .company(company)
                        .name(levelName)
                        .normalizedRank(nextNormalizedRank(company))
                        .active(false)
                        .build()));
    }

    private int nextNormalizedRank(Company company) {
        OptionalInt highestSavedRank = levelRepository.findByCompany_IdOrderByNormalizedRankAsc(company.getId())
                .stream()
                .mapToInt(Level::getNormalizedRank)
                .max();
        return highestSavedRank.orElse(LevelCatalog.highestDefaultNormalizedRank()) + 1;
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
                        .active(false)
                        .build()));
    }

    /**
     * Resolves the role by {@code roleSlug} (or the slugified display name / custom role), creating
     * an inactive global role if none exists yet. {@code customRole} takes precedence as the display
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
                        .active(false)
                        .build()));
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
                        .active(false)
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

    private static BigDecimal scaleScore(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    private static Integer roundCurrency(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
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
