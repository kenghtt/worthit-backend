package com.worthit.backend.repository;

import java.math.BigDecimal;

/**
 * Per-company aggregate stats computed from {@code published} experiences
 * (see {@code database-spec.md} §10).
 */
public interface CompanyStatsProjection {

    Long getCompanyId();

    long getExperienceCount();

    long getRoleCount();

    BigDecimal getAvgWorthScore();

    BigDecimal getAvgStress();
}
