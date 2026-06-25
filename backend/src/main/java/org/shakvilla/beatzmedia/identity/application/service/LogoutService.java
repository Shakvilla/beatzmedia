package org.shakvilla.beatzmedia.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.identity.application.port.in.Logout;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Application service for LLFR-IDENTITY-01.4 (logout). Stateless JWT — this is a no-op; repeat
 * calls are idempotent. Identity ADD §4.1.
 */
@ApplicationScoped
public class LogoutService implements Logout {

  @Override
  public void logout(AccountId accountId) {
    // Stateless JWT: no server-side session to invalidate. Always succeeds. OQ-3 default.
  }
}
