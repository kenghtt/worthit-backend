package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-company level picker item (see {@code api-endpoints.md} §5), ordered by
 * {@code normalizedRank} ascending.
 *
 * <p>Field names are forced to camelCase via {@link JsonProperty} to match the UI contract,
 * overriding the global {@code snake_case} Jackson strategy.</p>
 */
public record LevelSummary(
        @JsonProperty("name") String name,
        @JsonProperty("normalizedRank") int normalizedRank
) {
}
