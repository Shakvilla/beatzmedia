package org.shakvilla.beatzmedia.library.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Input port: save or unsave an album. Idempotent. LLFR-LIBRARY-01.4. */
public interface ToggleSave {

  /** Save album. Validates existence; throws domain exception if not found. */
  void save(AccountId account, String albumId);

  /** Unsave album. Idempotent — no-op if not saved. */
  void unsave(AccountId account, String albumId);
}
