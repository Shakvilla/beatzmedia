package org.shakvilla.beatzmedia.identity.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.identity.application.port.out.SocialVerifier;
import org.shakvilla.beatzmedia.identity.domain.SocialProvider;
import org.shakvilla.beatzmedia.identity.domain.SocialTokenInvalidException;

/**
 * Config-driven dev/test stub implementation of {@link SocialVerifier} (OQ note, identity ADD
 * §5.2 / WU-IDN-2). Real provider SDK token introspection (Google/Facebook/Twitter) is out of
 * scope for v1: this adapter parses a deterministic pipe-delimited fixture token of the form
 * {@code "<providerUid>|<email>|<name>|<avatar-or-empty>"} so tests and local/dev clients can
 * exercise the social-login flow without live provider credentials.
 *
 * <p>Any token that does not match this fixture format — or is null/blank — is rejected with
 * {@link SocialTokenInvalidException}, mapped to 401 {@code SOCIAL_TOKEN_INVALID}.
 *
 * <p><b>Production note:</b> before go-live this adapter must be swapped for real provider token
 * introspection (Google tokeninfo, Facebook Graph API debug_token, Twitter OAuth2 userinfo) behind
 * the same {@link SocialVerifier} port — no application/domain code changes required.
 */
@ApplicationScoped
public class StubSocialVerifier implements SocialVerifier {

  private static final String FIELD_SEPARATOR = "\\|";

  @Override
  public VerifiedIdentity verify(SocialProvider provider, String providerToken) {
    if (provider == null || providerToken == null || providerToken.isBlank()) {
      throw new SocialTokenInvalidException();
    }

    String[] parts = providerToken.split(FIELD_SEPARATOR, -1);
    if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
      throw new SocialTokenInvalidException();
    }

    String providerUid = parts[0];
    String email = parts[1];
    String name = parts.length > 2 && !parts[2].isBlank() ? parts[2] : email;
    String avatar = parts.length > 3 && !parts[3].isBlank() ? parts[3] : null;

    return new VerifiedIdentity(providerUid, email, name, avatar);
  }
}
