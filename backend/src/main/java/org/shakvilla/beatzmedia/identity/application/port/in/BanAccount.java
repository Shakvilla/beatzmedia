package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port for banning an account, called in-process by the {@code admin} module's trust &amp;
 * safety {@code ban} use case (LLFR-ADMIN-07.1) via its {@code AccountAdminPort} adapter. Pure
 * domain mutation ({@code → banned}, terminal) — does NOT append an AuditEntry; the {@code admin}
 * module owns INV-10 for this admin-driven action (the actor-facing boundary), the same split as
 * {@link SuspendAccount}. Identity ADD §4.1.
 *
 * <p><strong>Session revocation.</strong> A banned account cannot obtain new tokens
 * ({@link org.shakvilla.beatzmedia.identity.domain.Account#canAuthenticate()} is false). Existing
 * JWTs expire naturally — there is no server-side session store to invalidate (stateless JWT, OQ-3
 * default; same bound as suspend). No {@code reason} parameter: the account carries no field for it;
 * the reason lives only in the admin audit entry.
 *
 * <p>Idempotent: banning an already-banned account is a no-op success (no 409), matching the risk
 * {@code ban} endpoint contract (admin ADD §12).
 */
public interface BanAccount {

  /**
   * Bans the target account and returns its updated admin view.
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   *     (404) — e.g. when a risk signal's {@code subjectRef} is not a resolvable account
   */
  AccountAdminView ban(AccountId target);
}
