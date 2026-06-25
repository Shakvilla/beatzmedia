package org.shakvilla.beatzmedia.identity.application.port.out;

import java.util.Set;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port for JWT issuance. Issues short-lived access tokens only (OQ-3 default: no refresh
 * token for v1). Identity ADD §4.2 / §5.2.
 */
public interface TokenIssuer {

  /**
   * Issues a signed JWT with {@code sub} = accountId and {@code roles} = the supplied set. The
   * minimum role for any account is {@code "fan"}; artists also carry {@code "artist"}.
   */
  String issue(AccountId subject, Set<String> roles);
}
