package org.shakvilla.beatzmedia.identity.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Creates, renews, and revokes a browser session without exposing its secret to JavaScript. */
public interface BrowserSessions {
  String start(AccountId accountId);

  Optional<AuthResult> renew(String cookieValue);

  void revoke(String cookieValue);
}
