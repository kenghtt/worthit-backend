package com.worthit.backend.dto;

import com.worthit.backend.entity.EmploymentStatus;
import com.worthit.backend.entity.Experience;
import com.worthit.backend.entity.Location;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A single active experience for a company + role (see {@code api-endpoints.md} §2.4).
 *
 * <p>Unlike the company/role summary DTOs, this one is shaped to mirror the {@code experience}
 * DB columns (see {@code database-spec.md} §8) rather than the UI's mock field names: it relies
 * on the global {@code snake_case} Jackson strategy, so e.g. {@code worthItScore} serializes as
 * {@code worth_it_score}, {@code stressLevel} as {@code stress_level}, {@code worthItReason} as
 * {@code worth_it_reason}, and {@code createdAt} as {@code created_at} (ISO-8601). Foreign-key relations
 * are exposed via their natural identifiers (company/role slug + name, location slug/city/state,
 * level name). The experience ID is included as stable row identity for paginated UI lists.</p>
 */
public record ExperienceSummary(
        Long id,
        String companySlug,
        String companyName,
        String roleSlug,
        String roleName,
        String locationSlug,
        String city,
        String state,
        String levelName,
        EmploymentStatus employmentStatus,
        short yearsExperience,
        Short yearsAtCompany,
        int baseSalary,
        int bonus,
        int stock,
        int signingBonus,
        short compensationYear,
        BigDecimal stressLevel,
        Short hoursPerWeek,
        BigDecimal worthItScore,
        boolean active,
        String worthItReason,
        OffsetDateTime createdAt
) {

    /**
     * Maps an {@link Experience} to this response shape (see {@code api-endpoints.md} §2.4), expanding
     * the foreign keys into their natural identifiers. The level name prefers the experience's own
     * {@code levelName} snapshot, falling back to the linked {@code level}'s name when present.
     *
     * <p>Relations ({@code company}, {@code role}, {@code location}, optionally {@code level}) must be
     * loaded before calling, otherwise lazy access may fail outside a session.</p>
     */
    public static ExperienceSummary from(Experience e) {
        Location loc = e.getLocation();
        String levelName = e.getLevelName() != null ? e.getLevelName()
                : (e.getLevel() != null ? e.getLevel().getName() : null);
        return new ExperienceSummary(
                e.getId(),
                e.getCompany().getSlug(),
                e.getCompany().getName(),
                e.getRole().getSlug(),
                e.getRole().getName(),
                loc.getSlug(),
                loc.getCity(),
                loc.getState(),
                levelName,
                e.getEmploymentStatus(),
                e.getYearsExperience(),
                e.getYearsAtCompany(),
                e.getBaseSalary(),
                e.getBonus(),
                e.getStock(),
                e.getSigningBonus(),
                e.getCompensationYear(),
                e.getStressLevel(),
                e.getHoursPerWeek(),
                e.getWorthItScore(),
                e.isActive(),
                e.getWorthItReason(),
                e.getCreatedAt()
        );
    }
}
