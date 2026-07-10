package org.shakvilla.beatzmedia.identity.adapter.out.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.identity.application.port.out.TokenIssuer;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

import io.smallrye.jwt.build.Jwt;

/**
 * SmallRye JWT implementation of {@link TokenIssuer}. Issues RS256-signed access tokens. Key
 * material is configured via {@code smallrye.jwt.sign.key.location} (dev keypair in
 * {@code jwt/dev-private.pem}). Identity ADD §5.2 / OQ-3 (no refresh for v1).
 */
@ApplicationScoped
public class JwtTokenIssuer implements TokenIssuer {

  private final String issuer;
  private final long accessTtlSeconds;
  private final Clock clock;

  @Inject
  public JwtTokenIssuer(
      @ConfigProperty(name = "mp.jwt.verify.issuer") String issuer,
      @ConfigProperty(name = "beatz.jwt.access-ttl-seconds", defaultValue = "900")
          long accessTtlSeconds,
      Clock clock) {
    this.issuer = issuer;
    this.accessTtlSeconds = accessTtlSeconds;
    this.clock = clock;
  }

  @Override
  public String issue(AccountId subject, Set<String> roles) {
    return issue(subject, roles, Duration.ofSeconds(accessTtlSeconds));
  }

  @Override
  public String issue(AccountId subject, Set<String> roles, Duration ttl) {
    Instant now = clock.now();
    Instant expiry = now.plus(ttl);
    return Jwt.issuer(issuer)
        .subject(subject.value())
        .groups(roles)
        .issuedAt(now.getEpochSecond())
        .expiresAt(expiry.getEpochSecond())
        .sign();
  }
}
