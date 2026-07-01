package org.shakvilla.beatzmedia.identity.application.port.out;

import org.shakvilla.beatzmedia.identity.domain.SocialProvider;
import org.shakvilla.beatzmedia.identity.domain.SocialTokenInvalidException;

/**
 * Output port: verifies a third-party provider token and resolves the caller's verified identity.
 * Adapter: {@code StubSocialVerifier} (config-driven dev/test stub for v1 — real provider SDK
 * introspection is out of scope, see OQ note in identity ADD §5.2). Identity ADD §4.2.
 */
public interface SocialVerifier {

  /**
   * Verifies {@code providerToken} against {@code provider}. Throws {@link
   * SocialTokenInvalidException} if the token is invalid, expired, or unrecognised.
   */
  VerifiedIdentity verify(SocialProvider provider, String providerToken);

  /** The verified identity returned by the provider once the token has been validated. */
  record VerifiedIdentity(String providerUid, String email, String name, String avatar) {}
}
