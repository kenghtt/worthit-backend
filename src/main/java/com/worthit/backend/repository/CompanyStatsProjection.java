package com.worthit.backend.repository;

import java.math.BigDecimal;

/**
 * Per-company aggregate stats computed from active experiences
 * (see {@code database-spec.md} §10).
 */
public interface CompanyStatsProjection {

    Long getCompanyId();

    long getExperienceCount();

    long getRoleCount();

    BigDecimal getAvgWorthScore();

    BigDecimal getAvgStress();

    /** Average total compensation (base + bonus + stock + signing bonus). */
    BigDecimal getAvgTotalComp();
}
