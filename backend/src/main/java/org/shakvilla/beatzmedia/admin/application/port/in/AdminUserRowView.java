package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;

/**
 * Read-model matching {@code AdminUserRow} in {@code Frontend/src/lib/admin-data.ts}: {@code {
 * id, name, initial, email, role, verified, joined, lastActive, status } }. Used as list items
 * ({@code PagedUsersView.items}), the {@code UserDetailView.summary}, and the response of every
 * verify/suspend/reactivate mutation. Admin ADD §6 (LLFR-ADMIN-02.*).
 *
 * <p>{@code initial}, {@code role} ({@code "fan"|"artist"}), {@code joined}/{@code lastActive}
 * (mapped from real {@code createdAt}/{@code updatedAt}) are all derived at the mapping boundary
 * from the underlying account row — never fabricated. {@code status} is the raw account status
 * wire value ({@code "active"|"pending"|"suspended"}; a future {@code "banned"} state from a not-
 * yet-built ban action would pass through unchanged too).
 */
public record AdminUserRowView(
    String id,
    String name,
    String initial,
    String email,
    String role,
    boolean verified,
    Instant joined,
    Instant lastActive,
    String status) {}
