package org.shakvilla.beatzmedia.library.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Input port: like or unlike a track. Idempotent PUT/DELETE. LLFR-LIBRARY-01.2. */
public interface ToggleLike {

  /** Like a track. Validates existence; throws domain exception if not found. */
  void like(AccountId account, String trackId);

  /** Unlike a track. Idempotent — no-op if not liked. */
  void unlike(AccountId account, String trackId);
}
