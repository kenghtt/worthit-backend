package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Aggregate stats for the role experiences page, computed across all matching active experiences.
 * Field names are forced to camelCase via {@link JsonProperty} to match the UI contract.
 */
public record ExperienceStatsSummary(
        @JsonProperty("experienceCount") long experienceCount,
        @JsonProperty("avgWorthScore") BigDecimal avgWorthScore,
        @JsonProperty("avgStress") BigDecimal avgStress,
        @JsonProperty("avgTotalComp") Integer avgTotalComp
) {
}