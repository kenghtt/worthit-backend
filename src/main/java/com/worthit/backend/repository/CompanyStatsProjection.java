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

    /** Average weekly working hours across experiences ({@code null} if none report hours). */
    BigDecimal getAvgHoursPerWeek();

    /** Average total compensation (base + bonus + stock + signing bonus). */
    BigDecimal getAvgTotalComp();
}
