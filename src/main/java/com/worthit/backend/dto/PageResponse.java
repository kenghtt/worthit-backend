package com.worthit.backend.dto;

import java.util.List;

/**
 * Cursor-based pagination envelope (see {@code api-endpoints.md} §1 "Pagination").
 *
 * <p>Serializes as {@code {"items": [...], "next_cursor": "..."|null}} — {@code next_cursor}
 * comes from the global {@code snake_case} Jackson strategy.</p>
 */
public record PageResponse<T>(List<T> items, String nextCursor) {
}
