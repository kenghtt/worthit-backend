package com.worthit.backend.dto;

import java.util.List;

/** Complete filter choices for one active company/role experience slice. */
public record ExperienceFilterOptions(
        List<String> levels,
        List<ExperienceLocationOption> locations
) {
}
