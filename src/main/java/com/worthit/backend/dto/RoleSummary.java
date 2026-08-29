package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Per-role aggregate card for a company (see {@code api-endpoints.md} §2.3). The role list is
 * derived from the company's active experiences for that role (see {@code database-spec.md} §10).
 * Compensation
 * figures are whole USD ({@code avgTotalComp} is the mean of base salary + bonus + stock +
 * signing bonus for the role's experiences);
 * score averages are one-decimal. Stat fields are {@code null} (and {@code experienceCount} is
 * {@code 0}) when the role has no active experiences yet.
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
        @JsonProperty("avgTotalComp") Integer avgTotalComp
) {
}
