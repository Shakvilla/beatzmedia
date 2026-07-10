package org.shakvilla.beatzmedia.identity.application.port.out;

import java.time.Duration;
import java.util.Set;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port for JWT issuance. Issues short-lived access tokens only (OQ-3 default: no refresh
 * token for v1). Identity ADD §4.2 / §5.2.
 */
public interface TokenIssuer {

  /**
   * Issues a signed JWT with {@code sub} = accountId and {@code roles} = the supplied set, using
   * the default configured access-token TTL ({@code beatz.jwt.access-ttl-seconds}). The minimum
   * role for any account is {@code "fan"}; artists also carry {@code "artist"}.
   */
  String issue(AccountId subject, Set<String> roles);

  /**
   * Issues a signed JWT with an explicit, caller-supplied TTL instead of the default access-token
   * TTL. Added for LLFR-ADMIN-02.5 (admin impersonation): impersonation tokens use a short,
   * independently-tunable TTL ({@code beatz.jwt.impersonation-ttl-seconds}), distinct from the
   * normal login TTL. Default implementation delegates to {@link #issue(AccountId, Set)} so
   * existing callers/fakes that only implement the two-arg method keep compiling.
   */
  default String issue(AccountId subject, Set<String> roles, Duration ttl) {
    return issue(subject, roles);
  }
}
