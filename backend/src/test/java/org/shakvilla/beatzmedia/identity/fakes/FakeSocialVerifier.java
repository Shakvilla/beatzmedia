package org.shakvilla.beatzmedia.identity.fakes;

import java.util.HashMap;
import java.util.Map;

import org.shakvilla.beatzmedia.identity.application.port.out.SocialVerifier;
import org.shakvilla.beatzmedia.identity.domain.SocialProvider;
import org.shakvilla.beatzmedia.identity.domain.SocialTokenInvalidException;

/**
 * Deterministic fake for {@link SocialVerifier}. Tokens are registered up front via {@link
 * #register}; verifying an unregistered token throws {@link SocialTokenInvalidException}, exactly
 * mirroring the real adapter contract.
 */
public class FakeSocialVerifier implements SocialVerifier {

  private final Map<String, VerifiedIdentity> tokens = new HashMap<>();

  /** Registers {@code token} for {@code provider} to resolve to {@code identity}. */
  public void register(SocialProvider provider, String token, VerifiedIdentity identity) {
    tokens.put(key(provider, token), identity);
  }

  @Override
  public VerifiedIdentity verify(SocialProvider provider, String providerToken) {
    VerifiedIdentity identity = tokens.get(key(provider, providerToken));
    if (identity == null) {
      throw new SocialTokenInvalidException();
    }
    return identity;
  }

  private static String key(SocialProvider provider, String token) {
    return provider + ":" + token;
  }
}
