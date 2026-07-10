package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader.UserCounts;

/**
 * Response of {@code GET /admin/users}: {@code { items, page, size, total, counts }}. {@code
 * counts} is a real, whole-table summary (Category A) — always computed independently of the
 * request's {@code q}/{@code filter}, matching {@code admin-data.ts}'s {@code USER_COUNTS}
 * semantics. Admin ADD §6 (LLFR-ADMIN-02.1).
 */
public record PagedUsersView(
    List<AdminUserRowView> items, int page, int size, long total, UserCounts counts) {}
