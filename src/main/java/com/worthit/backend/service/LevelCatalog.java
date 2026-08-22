package com.worthit.backend.service;

import com.worthit.backend.dto.LevelSummary;

import java.util.List;

final class LevelCatalog {

    static final List<LevelSummary> DEFAULT_LEVELS = List.of(
            new LevelSummary("Junior", 1),
            new LevelSummary("Mid", 2),
            new LevelSummary("Senior", 3),
            new LevelSummary("Staff", 4),
            new LevelSummary("Principal", 5)
    );

    private LevelCatalog() {
    }

    static int highestDefaultNormalizedRank() {
        return DEFAULT_LEVELS.stream()
                .mapToInt(LevelSummary::normalizedRank)
                .max()
                .orElse(0);
    }
}
