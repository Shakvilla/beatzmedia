package org.shakvilla.beatzmedia.identity.application.port.in;

/**
 * Input port: authenticate with email + password. Trigger: POST /v1/auth/login. Public. Emits
 * nothing. LLFR-IDENTITY-01.2.
 */
public interface Login {

  /** Returns a JWT + account view for valid credentials, or throws a domain exception. */
  AuthResult login(LoginCommand command);

  /** Command record carrying the login credentials. */
  record LoginCommand(String email, String rawPassword) {}
}
