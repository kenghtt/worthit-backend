package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Global role picker item (see {@code api-endpoints.md} §5). Lightweight lookup shape with no
 * aggregate stats — use {@link RoleSummary} for per-company role cards (§2.3).
 *
 * <p>Field names are forced to camelCase via {@link JsonProperty} to match the UI contract,
 * overriding the global {@code snake_case} Jackson strategy.</p>
 */
public record RoleLookupSummary(
        @JsonProperty("slug") String slug,
        @JsonProperty("name") String name,
        @JsonProperty("family") String family
) {
}
