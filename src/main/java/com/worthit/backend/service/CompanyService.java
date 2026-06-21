package com.worthit.backend.service;

import com.worthit.backend.dto.CompanyDetail;
import com.worthit.backend.dto.CompanySummary;
import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.entity.Company;
import com.worthit.backend.entity.ExperienceStatus;
import com.worthit.backend.exception.ResourceNotFoundException;
import com.worthit.backend.repository.CompanyRepository;
import com.worthit.backend.repository.CompanyStatsProjection;
import com.worthit.backend.repository.ExperienceRepository;
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
 * Read-side logic for {@code GET /api/v1/companies} (see {@code api-endpoints.md} §2.1):
 * case-insensitive name search, industry filter, sorting, and cursor pagination.
 *
 * <p>Aggregate stats are computed from {@code published} experiences. Given the current data
 * size the company list is filtered/sorted/paged in memory, which keeps support for sorting by
 * derived aggregates (worth score, experience count) straightforward.</p>
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
    private final ExperienceRepository experienceRepository;

    @Transactional(readOnly = true)
    public PageResponse<CompanySummary> listCompanies(String companySubstring, String industry,
                                                      String sort, String order,
                                                      String cursor, Integer limit) {
        int pageSize = normalizeLimit(limit);

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
                .sorted(comparator(sort, order))
                .toList();

        int offset = decodeCursor(cursor);
        if (offset < 0 || offset >= all.size()) {
            return new PageResponse<>(List.of(), null);
        }

        int end = Math.min(offset + pageSize, all.size());
        List<CompanySummary> pageItems = all.subList(offset, end);
        String nextCursor = end < all.size() ? encodeCursor(end) : null;
        return new PageResponse<>(pageItems, nextCursor);
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

    private CompanySummary toSummary(Company c, CompanyStatsProjection stats) {
        long experienceCount = stats == null ? 0L : stats.getExperienceCount();
        long roleCount = stats == null ? 0L : stats.getRoleCount();
        BigDecimal avgWorth = stats == null ? null : scale(stats.getAvgWorthScore());
        BigDecimal avgStress = stats == null ? null : scale(stats.getAvgStress());
        return new CompanySummary(
                c.getSlug(),
                c.getName(),
                c.getIndustry(),
                c.getHeadquarters(),
                experienceCount,
                roleCount,
                avgWorth,
                avgStress
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
