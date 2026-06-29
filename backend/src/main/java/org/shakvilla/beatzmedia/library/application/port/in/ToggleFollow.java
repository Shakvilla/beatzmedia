package org.shakvilla.beatzmedia.library.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.domain.FollowKind;

/** Input port: follow or unfollow an artist/playlist/show. Idempotent. LLFR-LIBRARY-01.3. */
public interface ToggleFollow {

  /** Follow a target. Validates existence via CatalogReader. */
  void follow(AccountId account, FollowKind kind, String targetId);

  /** Unfollow a target. Idempotent — no-op if not followed. */
  void unfollow(AccountId account, FollowKind kind, String targetId);
}
