package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Per-company card scoped to a single location (see {@code api-endpoints.md} §3.3): a company that
 * has {@code published} experiences in the city, with its stats computed from only that city's
 * experiences. Score averages are one-decimal.
 *
 * <p>Field names are forced to camelCase via {@link JsonProperty} to match the UI contract,
 * overriding the global {@code snake_case} Jackson strategy.</p>
 */
public record LocationCompanySummary(
        @JsonProperty("slug") String slug,
        @JsonProperty("name") String name,
        @JsonProperty("industry") String industry,
        @JsonProperty("experienceCount") long experienceCount,
        @JsonProperty("avgWorthScore") BigDecimal avgWorthScore,
        @JsonProperty("avgStress") BigDecimal avgStress
) {
}
