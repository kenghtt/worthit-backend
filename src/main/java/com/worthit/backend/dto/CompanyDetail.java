package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Company detail response (see {@code api-endpoints.md} §2.2): a single company's basic profile,
 * looked up by slug. Intentionally lightweight — no aggregate stats — so it can back fast lookups
 * (e.g. the search bar resolving a typed company). Field names are forced to camelCase via
 * {@link JsonProperty} to match the UI contract, overriding the global {@code snake_case}
 * Jackson strategy.
 */
public record CompanyDetail(
        @JsonProperty("slug") String slug,
        @JsonProperty("name") String name,
        @JsonProperty("industry") String industry,
        @JsonProperty("headquarters") String headquarters
) {
}
