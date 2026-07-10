package org.shakvilla.beatzmedia.admin.application.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * Output port resolving an opaque {@code requesterRef} (an account id) to a display name for the
 * support-ticket views. Implemented by an adapter that reads the {@code identity} module's {@code
 * account} table in-process (same JVM, no cross-module FK) — {@code admin} never queries identity
 * tables directly from application code. Admin ADD §4.3 (identity reader).
 */
public interface IdentityReader {

  /** Resolves the display name for an account id, or empty if the account no longer exists. */
  Optional<String> displayNameOf(String accountId);

  /**
   * Count of accounts with {@code status = 'active'}, right now. Backs {@code
   * AdminOverview.kpis.activeUsers} (WU-ADM-1). Deliberately NOT time-boxed by a range — there is
   * no session/login-activity tracking anywhere in this codebase to compute a true "active in the
   * last N days" figure, so this is an honest, range-independent proxy (admin ADD §16 as-built).
   */
  int countActiveAccounts();

  /**
   * Count of accounts with {@code is_artist = true} and {@code created_at >= since}. Backs {@code
   * AdminOverview.kpis.newArtists} (WU-ADM-1) — callers pass the current period's start instant to
   * get an exact within-range count (the window's upper bound is implicitly "now").
   */
  int countNewArtists(Instant since);
}
