package com.worthit.backend.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Slug helper that mirrors the UI's {@code slug()} output (see {@code worthit/src/utils/slug.js}
 * and {@code database-spec.md} §1): lowercase, accents stripped, non-alphanumerics collapsed to
 * {@code -}, leading/trailing dashes trimmed.
 */
public final class SlugUtil {

    private SlugUtil() {
    }

    /**
     * Produce a URL-safe slug from arbitrary input. Returns an empty string for {@code null}/blank.
     */
    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}
