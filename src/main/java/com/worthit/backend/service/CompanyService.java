package com.worthit.backend.service;

import com.worthit.backend.dto.CompanyDetail;
import com.worthit.backend.dto.CompanySummary;
import com.worthit.backend.dto.ExperienceSummary;
import com.worthit.backend.dto.LevelSummary;
import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.dto.RoleSummary;
import com.worthit.backend.entity.Company;
import com.worthit.backend.entity.CompanyRole;
import com.worthit.backend.entity.Experience;
import com.worthit.backend.entity.ExperienceStatus;
import com.worthit.backend.entity.Level;
import com.worthit.backend.entity.Role;
import com.worthit.backend.exception.ResourceNotFoundException;
import com.worthit.backend.repository.CompanyRepository;
import com.worthit.backend.repository.CompanyRoleRepository;
import com.worthit.backend.repository.CompanyStatsProjection;
import com.worthit.backend.repository.ExperienceRepository;
import com.worthit.backend.repository.LevelRepository;
import com.worthit.backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-side logic for the company endpoints (see {@code api-endpoints.md} §2.1–2.5): company
 * list/search, company detail, search-bar typeahead, per-company roles, and the experiences for
 * a company + role.
 *
 * <p>Aggregate stats are computed from {@code published} experiences. Given the current data
 * size results are filtered/sorted/paged in memory (see {@link #paginate}), which keeps support
 * for sorting by derived aggregates (worth score, experience count) straightforward.</p>
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    /** Default page size when {@code limit} is omitted (see §1). */
    static final int DEFAULT_LIMIT = 20;
    /** Maximum allowed page size (see §1). */
    static final int MAX_LIMIT = 50;

    /** Default result cap for the typeahead search when {@code limit} is omitted (see §2.5). */
    static final int SEARCH_DEFAULT_LIMIT = 8;
    /** Maximum number of typeahead results (see §2.5). */
    static final int SEARCH_MAX_LIMIT = 20;

    private final CompanyRepository companyRepository;
    private final CompanyRoleRepository companyRoleRepository;
    private final RoleRepository roleRepository;
    private final ExperienceRepository experienceRepository;
    private final LevelRepository levelRepository;

    @Transactional(readOnly = true)
    public PageResponse<CompanySummary> listCompanies(String companySubstring, Boolean includeZeroExperience, String industry,
                                                      String sort, String order,
                                                      String cursor, Integer limit) {
        int pageSize = normalizeLimit(limit);
        boolean includeZeroExp = Boolean.TRUE.equals(includeZeroExperience);

        Map<Long, CompanyStatsProjection> statsByCompany = experienceRepository
                .aggregateByCompany(ExperienceStatus.published)
                .stream()
                .collect(Collectors.toMap(CompanyStatsProjection::getCompanyId, Function.identity()));

        String qLower = (companySubstring == null || companySubstring.isBlank()) ? null : companySubstring.trim().toLowerCase(Locale.ROOT);
        String industryFilter = (industry == null || industry.isBlank()) ? null : industry.trim();

        List<CompanySummary> all = companyRepository.findAll().stream()
                .filter(Company::isActive)
                .filter(c -> qLower == null || c.getName().toLowerCase(Locale.ROOT).contains(qLower))
                .filter(c -> industryFilter == null
                        || (c.getIndustry() != null && c.getIndustry().equalsIgnoreCase(industryFilter)))
                .map(c -> toSummary(c, statsByCompany.get(c.getId())))
                .filter(c -> includeZeroExp || c.experienceCount() > 0)
                .sorted(comparator(sort, order))
                .toList();

        return paginate(all, cursor, pageSize);
    }

    /**
     * Returns a single company's basic profile by slug (see {@code api-endpoints.md} §2.2).
     * Lightweight by design: no aggregate stats, just the company's identifying fields.
     *
     * @throws ResourceNotFoundException if no active company with the slug exists
     */
    @Transactional(readOnly = true)
    public CompanyDetail getCompany(String slug) {
        Company company = companyRepository.findBySlug(slug)
                .filter(Company::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + slug));

        return toDetail(company);
    }

    /**
     * Lightweight company typeahead for the search bar (see {@code api-endpoints.md} §2.5):
     * case-insensitive substring match on company name, name-sorted, capped to a single page.
     *
     * <p>A blank/missing {@code q} yields an empty page (nothing to match yet). No aggregate
     * stats are computed, keeping this path cheap for per-keystroke calls.</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<CompanyDetail> searchCompanies(String q, Integer limit) {
        String qLower = (q == null || q.isBlank()) ? null : q.trim().toLowerCase(Locale.ROOT);
        if (qLower == null) {
            return new PageResponse<>(List.of(), null);
        }

        int max = normalizeSearchLimit(limit);
        List<CompanyDetail> items = companyRepository.findAll().stream()
                .filter(Company::isActive)
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(qLower))
                .sorted(Comparator
                        .comparing((Company c) -> c.getName().toLowerCase(Locale.ROOT))
                        .thenComparing(Company::getSlug))
                .limit(max)
                .map(this::toDetail)
                .toList();

        return new PageResponse<>(items, null);
    }

    private CompanyDetail toDetail(Company c) {
        return new CompanyDetail(
                c.getSlug(),
                c.getName(),
                c.getIndustry(),
                c.getHeadquarters()
        );
    }

    /**
     * Lists the roles available at a company, each with per-role aggregate stats
     * (see {@code api-endpoints.md} §2.3). The role list is driven by the {@code company_role}
     * join (see {@code database-spec.md} §5); stats — counts, average worth/stress, and the
     * base-salary min/max/average — are computed from the company's {@code published} experiences
     * (see §10). Roles with no published experiences are still listed, with {@code null} stats.
     *
     * <p>Like §2.1, aggregation is done in memory and the result is name-sorted and
     * cursor-paged.</p>
     *
     * @throws ResourceNotFoundException if no active company with the slug exists
     */
    @Transactional(readOnly = true)
    public PageResponse<RoleSummary> listCompanyRoles(String slug, String cursor, Integer limit) {
        Company company = companyRepository.findBySlug(slug)
                .filter(Company::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + slug));

        int pageSize = normalizeLimit(limit);

        Map<Long, List<Experience>> experiencesByRole = experienceRepository
                .findByCompany_IdAndStatus(company.getId(), ExperienceStatus.published)
                .stream()
                .collect(Collectors.groupingBy(e -> e.getRole().getId()));

        List<RoleSummary> all = companyRoleRepository.findActiveWithRoleByCompanyId(company.getId()).stream()
                .map(CompanyRole::getRole)
                .filter(Role::isActive)
                .map(role -> toRoleSummary(role, experiencesByRole.getOrDefault(role.getId(), List.of())))
                .sorted(Comparator
                        .comparing((RoleSummary r) -> r.name().toLowerCase(Locale.ROOT))
                        .thenComparing(RoleSummary::slug))
                .toList();

        return paginate(all, cursor, pageSize);
    }

    /**
     * Lists the level options for a company's submit-form level picker (see {@code api-endpoints.md}
     * §5), ordered by {@code normalizedRank} ascending. Returns {@code 404} if no active company
     * has the slug.
     *
     * @throws ResourceNotFoundException if no active company with the slug exists
     */
    @Transactional(readOnly = true)
    public PageResponse<LevelSummary> listCompanyLevels(String slug, String cursor, Integer limit) {
        Company company = companyRepository.findBySlug(slug)
                .filter(Company::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + slug));

        int pageSize = normalizeLimit(limit);

        List<LevelSummary> all = levelRepository.findByCompany_IdOrderByNormalizedRankAsc(company.getId()).stream()
                .filter(Level::isActive)
                .map(l -> new LevelSummary(l.getName(), l.getNormalizedRank()))
                .toList();

        return paginate(all, cursor, pageSize);
    }

    private RoleSummary toRoleSummary(Role role, List<Experience> experiences) {
        Integer salaryMin = experiences.isEmpty() ? null
                : experiences.stream().mapToInt(Experience::getBaseSalary).min().getAsInt();
        Integer salaryMax = experiences.isEmpty() ? null
                : experiences.stream().mapToInt(Experience::getBaseSalary).max().getAsInt();
        return new RoleSummary(
                role.getSlug(),
                role.getName(),
                experiences.size(),
                averageScore(experiences, Experience::getWorthItScore),
                averageScore(experiences, Experience::getStressLevel),
                salaryMin,
                salaryMax,
                averageSalary(experiences)
        );
    }

    private static BigDecimal averageScore(List<Experience> experiences,
                                           Function<Experience, BigDecimal> field) {
        if (experiences.isEmpty()) {
            return null;
        }
        BigDecimal sum = experiences.stream()
                .map(field)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(experiences.size()), 1, RoundingMode.HALF_UP);
    }

    private static Integer averageSalary(List<Experience> experiences) {
        if (experiences.isEmpty()) {
            return null;
        }
        // Sum as long to avoid overflow, then divide by count and round to whole USD.
        long sum = experiences.stream().mapToLong(Experience::getBaseSalary).sum();
        return (int) Math.round((double) sum / experiences.size());
    }

    /**
     * Lists the {@code published} experiences for a company + role (see {@code api-endpoints.md}
     * §2.4), newest first, optionally filtered to a single city (by location slug). Each item
     * mirrors the {@code experience} DB columns (see {@link ExperienceSummary}).
     *
     * <p>Like §2.1/§2.3, the matching rows are sorted and paged in memory.</p>
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
                .findForCompanyRole(company.getId(), role.getId(), ExperienceStatus.published)
                .stream()
                .filter(e -> citySlug == null || citySlug.equalsIgnoreCase(e.getLocation().getSlug()))
                .sorted(newestFirst)
                .map(ExperienceSummary::from)
                .toList();

        return paginate(all, cursor, pageSize);
    }

    private CompanySummary toSummary(Company c, CompanyStatsProjection stats) {
        long experienceCount = stats == null ? 0L : stats.getExperienceCount();
        long roleCount = stats == null ? 0L : stats.getRoleCount();
        BigDecimal avgWorth = stats == null ? null : scale(stats.getAvgWorthScore());
        BigDecimal avgStress = stats == null ? null : scale(stats.getAvgStress());
        BigDecimal avgHours = stats == null ? null : scale(stats.getAvgHoursPerWeek());
        BigDecimal avgTotalComp = stats == null || stats.getAvgTotalComp() == null
                ? null
                : stats.getAvgTotalComp().setScale(0, RoundingMode.HALF_UP);
        return new CompanySummary(
                c.getSlug(),
                c.getName(),
                c.getIndustry(),
                c.getHeadquarters(),
                experienceCount,
                roleCount,
                avgWorth,
                avgStress,
                avgHours,
                avgTotalComp
        );
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static int normalizeSearchLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return SEARCH_DEFAULT_LIMIT;
        }
        return Math.min(limit, SEARCH_MAX_LIMIT);
    }

    private static Comparator<CompanySummary> comparator(String sort, String order) {
        String sortField = sort == null ? "name" : sort.trim().toLowerCase(Locale.ROOT);
        Comparator<CompanySummary> base = switch (sortField) {
            case "worthscore", "avgworthscore" -> Comparator.comparing(
                    CompanySummary::avgWorthScore,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "experiences", "experiencecount" -> Comparator.comparingLong(CompanySummary::experienceCount);
            case "stress", "avgstress" -> Comparator.comparing(
                    CompanySummary::avgStress,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT));
        };
        // Stable tiebreaker so cursor paging is deterministic.
        Comparator<CompanySummary> withTieBreak = base.thenComparing(CompanySummary::slug);
        boolean descending = order != null && order.trim().equalsIgnoreCase("desc");
        return descending ? withTieBreak.reversed() : withTieBreak;
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
}
