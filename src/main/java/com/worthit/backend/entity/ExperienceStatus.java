package com.worthit.backend.entity;

/**
 * Moderation lifecycle of a submitted experience (see {@code database-spec.md} §9).
 *
 * <p>Only {@link #published} experiences are returned by public read endpoints and only they
 * count toward aggregate stats.</p>
 */
public enum ExperienceStatus {
    pending,
    published,
    rejected
}
