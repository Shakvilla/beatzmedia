package org.shakvilla.beatzmedia.identity.fakes;

import java.time.Duration;
import java.util.Set;

import org.shakvilla.beatzmedia.identity.application.port.out.TokenIssuer;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Deterministic fake for {@link TokenIssuer}. Returns a predictable token string of the form
 * {@code "token:<subject>:<roles>"} (or, for impersonation, {@code
 * "token:<subject>:<roles>:act=<actor>"}) so tests can assert on specific fields without real JWT
 * cryptography.
 */
public class FakeTokenIssuer implements TokenIssuer {

  @Override
  public String issue(AccountId subject, Set<String> roles) {
    return "token:" + subject.value() + ":" + String.join(",", roles);
  }

  @Override
  public String issueImpersonation(AccountId subject, Set<String> roles, AccountId actor, Duration ttl) {
    return "token:" + subject.value() + ":" + String.join(",", roles) + ":act=" + actor.value();
  }
}
