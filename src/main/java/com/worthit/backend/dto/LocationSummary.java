package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Location list/search item (see {@code api-endpoints.md} §3.1/§3.2). Field names are forced to
 * camelCase via {@link JsonProperty} to match the UI contract, overriding the global
 * {@code snake_case} Jackson strategy.
 */
public record LocationSummary(
        @JsonProperty("slug") String slug,
        @JsonProperty("city") String city,
        @JsonProperty("state") String state,
        @JsonProperty("experienceCount") long experienceCount,
        @JsonProperty("companyCount") long companyCount,
        @JsonProperty("avgWorthScore") BigDecimal avgWorthScore,
        @JsonProperty("avgStress") BigDecimal avgStress
) {
}
