package com.worthit.backend.service;

import com.worthit.backend.dto.PageResponse;
import com.worthit.backend.dto.RoleLookupSummary;
import com.worthit.backend.entity.Role;
import com.worthit.backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Read-side logic for the global role lookup endpoint (see {@code api-endpoints.md} §5).
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    /** Default page size when {@code limit} is omitted (see §1). */
    static final int DEFAULT_LIMIT = 20;
    /** Maximum allowed page size (see §1). */
    static final int MAX_LIMIT = 50;

    private final RoleRepository roleRepository;

    /**
     * Lists all active global roles for the submit-form role picker (see {@code api-endpoints.md}
     * §5), name-sorted (slug tiebreaker) and cursor-paged.
     */
    @Transactional(readOnly = true)
    public PageResponse<RoleLookupSummary> listRoles(String cursor, Integer limit) {
        int pageSize = normalizeLimit(limit);

        List<RoleLookupSummary> all = roleRepository.findAll().stream()
                .filter(Role::isActive)
                .sorted(Comparator
                        .comparing((Role r) -> r.getName().toLowerCase(Locale.ROOT))
                        .thenComparing(Role::getSlug))
                .map(r -> new RoleLookupSummary(r.getSlug(), r.getName(), r.getFamily()))
                .toList();

        return paginate(all, cursor, pageSize);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static <T> PageResponse<T> paginate(List<T> all, String cursor, int pageSize) {
        int offset = decodeCursor(cursor);
        if (offset < 0 || offset >= all.size()) {
            return new PageResponse<>(List.of(), null);
        }
        int end = Math.min(offset + pageSize, all.size());
        String nextCursor = end < all.size() ? encodeCursor(end) : null;
        return new PageResponse<>(all.subList(offset, end), nextCursor);
    }

    private static String encodeCursor(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("o:" + offset).getBytes(StandardCharsets.UTF_8));
    }

    private static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (decoded.startsWith("o:")) {
                return Integer.parseInt(decoded.substring(2));
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed cursor — fall through to treat as start.
        }
        return 0;
    }
}
