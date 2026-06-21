package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Per-role aggregate card for a company (see {@code api-endpoints.md} §2.3). The role list is
 * driven by the {@code company_role} join (see {@code database-spec.md} §5); the stats are
 * computed from the company's {@code published} experiences for that role (see §10). Salary
 * figures are whole USD ({@code baseSalaryAverage} is the mean of the role's base salaries);
 * score averages are one-decimal. Stat fields are {@code null} (and {@code experienceCount} is
 * {@code 0}) when the role has no published experiences yet.
 *
 * <p>Field names are forced to camelCase via {@link JsonProperty} to match the UI contract,
 * overriding the global {@code snake_case} Jackson strategy.</p>
 */
public record RoleSummary(
        @JsonProperty("slug") String slug,
        @JsonProperty("name") String name,
        @JsonProperty("experienceCount") long experienceCount,
        @JsonProperty("avgWorthScore") BigDecimal avgWorthScore,
        @JsonProperty("avgStress") BigDecimal avgStress,
        @JsonProperty("baseSalaryMin") Integer baseSalaryMin,
        @JsonProperty("baseSalaryMax") Integer baseSalaryMax,
        @JsonProperty("baseSalaryAverage") Integer baseSalaryAverage
) {
}
