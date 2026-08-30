package com.worthit.backend.repository;

import java.math.BigDecimal;

/**
 * Aggregate stats for a company+role experience slice.
 */
public interface ExperienceStatsProjection {

    long getExperienceCount();

    BigDecimal getAvgWorthScore();

    BigDecimal getAvgStress();

    BigDecimal getAvgTotalComp();
}