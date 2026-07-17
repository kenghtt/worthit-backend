package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Company list/search item (see {@code api-endpoints.md} §2.1). Field names are forced to
 * camelCase via {@link JsonProperty} to match the UI contract, overriding the global
 * {@code snake_case} Jackson strategy.
 */
public record CompanySummary(
        @JsonProperty("slug") String slug,
        @JsonProperty("name") String name,
        @JsonProperty("industry") String industry,
        @JsonProperty("headquarters") String headquarters,
        @JsonProperty("experienceCount") long experienceCount,
        @JsonProperty("roleCount") long roleCount,
        @JsonProperty("avgWorthScore") BigDecimal avgWorthScore,
        @JsonProperty("avgStress") BigDecimal avgStress,
        @JsonProperty("avgHoursPerWeek") BigDecimal avgHoursPerWeek,
        @JsonProperty("avgTotalComp") BigDecimal avgTotalComp
) {
}
