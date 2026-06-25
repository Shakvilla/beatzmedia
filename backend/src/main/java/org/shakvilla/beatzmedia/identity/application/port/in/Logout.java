package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: log out the current session. Trigger: POST /v1/auth/logout. Auth: any authenticated.
 * Stateless JWT — this is an idempotent no-op. LLFR-IDENTITY-01.4.
 */
public interface Logout {

  /** No-op for stateless JWT. Always succeeds; repeat calls are safe. */
  void logout(AccountId accountId);
}
