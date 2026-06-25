package com.worthit.backend.repository;

import java.math.BigDecimal;

/**
 * Per-location aggregate stats computed from {@code published} experiences
 * (see {@code api-endpoints.md} §3.1, {@code database-spec.md} §10).
 */
public interface LocationStatsProjection {

    Long getLocationId();

    long getExperienceCount();

    long getCompanyCount();

    BigDecimal getAvgWorthScore();

    BigDecimal getAvgStress();
}
