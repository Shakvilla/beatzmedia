package org.shakvilla.beatzmedia.identity.application.port.in;

/**
 * Input port: request a password reset link. Trigger: POST /v1/me/password/reset. Public. Always
 * succeeds from the caller's point of view (no user enumeration) — if the email exists, a
 * single-use, time-boxed reset token is generated and mailed via the {@code Mailer} port; if it
 * does not, this is a silent no-op. LLFR-IDENTITY-01.5.
 */
public interface RequestPasswordReset {

  /** Requests a password reset for the given email. Never throws for an unknown email. */
  void request(RequestPasswordResetCommand command);

  /** Command record carrying the email address supplied by the caller. */
  record RequestPasswordResetCommand(String email) {}
}
