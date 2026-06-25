package org.shakvilla.beatzmedia.identity.application.port.in;

/**
 * Input port: register a new fan account. Trigger: POST /v1/auth/signup. Public; not idempotent
 * (email-unique). Emits AccountRegistered on success. LLFR-IDENTITY-01.1.
 */
public interface RegisterFan {

  /** Registers a new fan account and returns a JWT + account view on success. */
  AuthResult register(RegisterFanCommand command);

  /** Command record carrying validated (but un-sanitised) fan signup data. */
  record RegisterFanCommand(String name, String email, String rawPassword) {}
}
