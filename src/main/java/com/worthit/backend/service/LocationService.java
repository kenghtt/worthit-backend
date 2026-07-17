package com.worthit.backend.service;

import com.worthit.backend.dto.LocationCompanySummary;
import com.worthit.backend.dto.LocationSummary;
import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.entity.Company;
import com.worthit.backend.entity.Experience;
import com.worthit.backend.entity.ExperienceStatus;
import com.worthit.backend.entity.Location;
import com.worthit.backend.exception.ResourceNotFoundException;
import com.worthit.backend.repository.ExperienceRepository;
import com.worthit.backend.repository.LocationRepository;
import com.worthit.backend.repository.LocationStatsProjection;
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
 * Read-side logic for the location endpoints (see {@code api-endpoints.md} §3): location
 * list/search with per-city aggregate stats.
 *
 * <p>Mirrors {@link CompanyService}: aggregate stats are computed from {@code published}
 * experiences and results are filtered/sorted/paged in memory (see {@link #paginate}).</p>
 */
@Service
@RequiredArgsConstructor
public class LocationService {

    /** Default page size when {@code limit} is omitted (see §1). */
    static final int DEFAULT_LIMIT = 20;
    /** Maximum allowed page size (see §1). */
    static final int MAX_LIMIT = 50;

    private final LocationRepository locationRepository;
    private final ExperienceRepository experienceRepository;

    /**
     * Lists / searches locations, each with per-city aggregate stats
     * (see {@code api-endpoints.md} §3.1). The optional {@code q} is a case-insensitive substring
     * match on the city name; stats — experience count, distinct company count, and average
     * worth/stress — are computed from {@code published} experiences. By default, locations with no
     * published experiences are excluded; pass {@code includeZeroExperience=true} to list them
     * (with {@code 0} counts and {@code null} averages).
     *
     * <p>Results are city-name sorted (slug tiebreaker) and cursor-paged.</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<LocationSummary> listLocations(String q, Boolean includeZeroExperience,
                                                       String cursor, Integer limit) {
        int pageSize = normalizeLimit(limit);
        boolean includeZeroExp = Boolean.TRUE.equals(includeZeroExperience);

        Map<Long, LocationStatsProjection> statsByLocation = experienceRepository
                .aggregateByLocation(ExperienceStatus.published, true)
                .stream()
                .collect(Collectors.toMap(LocationStatsProjection::getLocationId, Function.identity()));

        String qLower = (q == null || q.isBlank()) ? null : q.trim().toLowerCase(Locale.ROOT);

        List<LocationSummary> all = locationRepository.findAll().stream()
                .filter(Location::isActive)
                .filter(l -> qLower == null || l.getCity().toLowerCase(Locale.ROOT).contains(qLower))
                .map(l -> toSummary(l, statsByLocation.get(l.getId())))
                .filter(l -> includeZeroExp || l.experienceCount() > 0)
                .sorted(Comparator
                        .comparing((LocationSummary l) -> l.city().toLowerCase(Locale.ROOT))
                        .thenComparing(LocationSummary::slug))
                .toList();

        return paginate(all, cursor, pageSize);
    }

    /**
     * Returns a single location with its per-city aggregate stats by slug
     * (see {@code api-endpoints.md} §3.2). Same shape as the §3.1 list item; stats are computed
     * from {@code published} experiences, so a location with none yet still resolves with
     * {@code 0} counts and {@code null} averages.
     *
     * @throws ResourceNotFoundException if no active location with the slug exists
     */
    @Transactional(readOnly = true)
    public LocationSummary getLocation(String slug) {
        Location location = locationRepository.findBySlug(slug)
                .filter(Location::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + slug));

        LocationStatsProjection stats = experienceRepository
                .aggregateByLocation(ExperienceStatus.published, true)
                .stream()
                .filter(s -> s.getLocationId().equals(location.getId()))
                .findFirst()
                .orElse(null);

        return toSummary(location, stats);
    }

    /**
     * Lists the companies that have {@code published} experiences in a location, each with stats
     * scoped to that city (see {@code api-endpoints.md} §3.3). A company only appears if it has at
     * least one published experience in the city; stats — experience count and average
     * worth/stress — are computed from just those experiences.
     *
     * <p>Like §3.1, results are name-sorted (slug tiebreaker) and cursor-paged in memory.</p>
     *
     * @throws ResourceNotFoundException if no active location with the slug exists
     */
    @Transactional(readOnly = true)
    public PageResponse<LocationCompanySummary> listLocationCompanies(String slug, String cursor,
                                                                      Integer limit) {
        Location location = locationRepository.findBySlug(slug)
                .filter(Location::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + slug));

        int pageSize = normalizeLimit(limit);

        List<LocationCompanySummary> all = experienceRepository
                .findForLocation(location.getId(), ExperienceStatus.published, true)
                .stream()
                .collect(Collectors.groupingBy(e -> e.getCompany().getId()))
                .values()
                .stream()
                .map(this::toLocationCompanySummary)
                .sorted(Comparator
                        .comparing((LocationCompanySummary c) -> c.name().toLowerCase(Locale.ROOT))
                        .thenComparing(LocationCompanySummary::slug))
                .toList();

        return paginate(all, cursor, pageSize);
    }

    private LocationCompanySummary toLocationCompanySummary(List<Experience> experiences) {
        Company company = experiences.get(0).getCompany();
        return new LocationCompanySummary(
                company.getSlug(),
                company.getName(),
                company.getIndustry(),
                experiences.size(),
                averageScore(experiences, Experience::getWorthItScore),
                averageScore(experiences, Experience::getStressLevel)
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

    private LocationSummary toSummary(Location l, LocationStatsProjection stats) {
        long experienceCount = stats == null ? 0L : stats.getExperienceCount();
        long companyCount = stats == null ? 0L : stats.getCompanyCount();
        BigDecimal avgWorth = stats == null ? null : scale(stats.getAvgWorthScore());
        BigDecimal avgStress = stats == null ? null : scale(stats.getAvgStress());
        return new LocationSummary(
                l.getSlug(),
                l.getCity(),
                l.getState(),
                experienceCount,
                companyCount,
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
