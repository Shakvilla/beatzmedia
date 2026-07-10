package org.shakvilla.beatzmedia.admin.application.port.out;

import java.time.Instant;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.domain.UserFilter;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port resolving reads against the {@code identity} module's {@code account} table.
 * Implemented by an adapter that reads identity's JPA entity in-process (same JVM, no cross-module
 * FK) — {@code admin} never queries identity tables directly from application code. Admin ADD §4.3
 * (identity reader).
 *
 * <p>WU-ADM-2 additive methods ({@link #listUsers}, {@link #findUser}, {@link #countUsers})
 * back LLFR-ADMIN-02.1 (paged/filtered user list + detail + counts). Extends the same port WU-ADM-1
 * introduced, per admin ADD §13's stated precedent — a single {@code IdentityReader}, not a
 * parallel {@code IdentityAdminReader}.
 */
public interface IdentityReader {

  /** Resolves the display name for an account id, or empty if the account no longer exists. */
  Optional<String> displayNameOf(String accountId);

  /**
   * Count of accounts with {@code status = 'active'}, right now. Backs {@code
   * AdminOverview.kpis.activeUsers} (WU-ADM-1). Deliberately NOT time-boxed by a range — there is
   * no session/login-activity tracking anywhere in this codebase to compute a true "active in the
   * last N days" figure, so this is an honest, range-independent proxy (admin ADD §13 as-built).
   */
  int countActiveAccounts();

  /**
   * Count of accounts with {@code is_artist = true} and {@code created_at >= since}. Backs {@code
   * AdminOverview.kpis.newArtists} (WU-ADM-1) — callers pass the current period's start instant to
   * get an exact within-range count (the window's upper bound is implicitly "now").
   */
  int countNewArtists(Instant since);

  /**
   * Paged, filtered, free-text-searched list of accounts for {@code GET /admin/users}. {@code q}
   * matches (case-insensitive substring) name or email; {@code filter} narrows by role/verified/
   * suspended status. Ordered by {@code created_at DESC} (newest first). LLFR-ADMIN-02.1.
   */
  Page<AccountRow> listUsers(String q, UserFilter filter, PageRequest page);

  /** Finds a single account row for {@code GET /admin/users/:id}. LLFR-ADMIN-02.1. */
  Optional<AccountRow> findUser(String accountId);

  /**
   * Computes the {@code counts} block accompanying {@code PagedUsers} ({@code all, fans, artists,
   * verified, suspended}) — always computed across the WHOLE table, independent of any {@code q}/
   * {@code filter} applied to the paged list itself (matches {@code admin-data.ts}'s {@code
   * USER_COUNTS} semantics: a global summary, not a filtered count). LLFR-ADMIN-02.1.
   */
  UserCounts countUsers();

  /**
   * Read-model row for one account, shared by the list and detail endpoints. Deliberately admin's
   * own shape (not identity's {@code Account} domain type or {@code AccountEntity}) — the reader
   * adapter maps identity's JPA entity into this row so the application layer never depends on
   * identity's persistence types.
   */
  record AccountRow(
      String id,
      String name,
      String email,
      boolean isArtist,
      boolean verified,
      String status,
      Instant createdAt,
      Instant updatedAt) {}

  /** Global user counts backing {@code PagedUsers.counts}. */
  record UserCounts(int all, int fans, int artists, int verified, int suspended) {}
}
