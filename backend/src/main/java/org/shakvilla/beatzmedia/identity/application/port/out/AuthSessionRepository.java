package org.shakvilla.beatzmedia.identity.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AuthSession;

/** Persists server-side browser sessions; lookup locks the row for renewal/revocation. */
public interface AuthSessionRepository {
  void save(AuthSession session);

  Optional<AuthSession> findForUpdate(String id);
}
