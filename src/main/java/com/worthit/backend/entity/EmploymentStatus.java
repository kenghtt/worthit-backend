package com.worthit.backend.entity;

/**
 * Whether the reviewer is/was current or former at the company.
 *
 * <p>See {@code database-spec.md} §9. The persisted values are lowercase to match the
 * API contract (the UI sends {@code current} / {@code former}). {@code past} is kept as the
 * internal name for the "former" state used by the seeder.</p>
 */
public enum EmploymentStatus {
    current,
    past
}
