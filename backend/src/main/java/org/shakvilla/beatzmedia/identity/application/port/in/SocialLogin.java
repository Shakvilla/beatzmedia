package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.SocialProvider;

/**
 * Input port: authenticate (or register) via a third-party provider token. Trigger: POST
 * /v1/auth/social. Public. Links to an existing account by verified email, or creates a new fan
 * account (no password credential) when none exists. Emits {@code AccountRegistered} on create.
 * LLFR-IDENTITY-01.3.
 */
public interface SocialLogin {

  /** Verifies the provider token and returns a JWT + account view, creating the account if new. */
  AuthResult socialLogin(SocialLoginCommand command);

  /** Command record carrying the claimed provider and the opaque provider token to verify. */
  record SocialLoginCommand(SocialProvider provider, String providerToken) {}
}
