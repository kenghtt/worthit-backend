package com.worthit.backend.dto;

import com.worthit.backend.entity.EmploymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A single published experience for a company + role (see {@code api-endpoints.md} §2.4).
 *
 * <p>Unlike the company/role summary DTOs, this one is shaped to mirror the {@code experience}
 * DB columns (see {@code database-spec.md} §8) rather than the UI's mock field names: it relies
 * on the global {@code snake_case} Jackson strategy, so e.g. {@code worthItScore} serializes as
 * {@code worth_it_score}, {@code stressLevel} as {@code stress_level}, {@code wishKnew} as
 * {@code wish_knew}, and {@code createdAt} as {@code created_at} (ISO-8601). Foreign-key relations
 * are exposed via their natural identifiers (company/role slug + name, location slug/city/state,
 * level name).</p>
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
        String whyStay,
        String whyLeave,
        String wishKnew,
        OffsetDateTime createdAt
) {
}
