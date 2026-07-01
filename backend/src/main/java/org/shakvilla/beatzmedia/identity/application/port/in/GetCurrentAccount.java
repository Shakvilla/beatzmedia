package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: read the authenticated caller's account. Trigger: GET /v1/me. Authz: any
 * authenticated role. LLFR-IDENTITY-02.1.
 */
public interface GetCurrentAccount {

  /** Returns the current account view for the given id, or throws if it no longer exists. */
  AccountView current(AccountId account);
}
