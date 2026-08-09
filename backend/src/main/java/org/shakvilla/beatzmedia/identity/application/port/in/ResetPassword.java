package org.shakvilla.beatzmedia.identity.application.port.in;

/**
 * Input port: redeem a password-reset token and set a new password. Trigger:
 * POST /v1/auth/password/reset. Public — the token is the only credential the caller has.
 *
 * <p>The other half of {@link RequestPasswordReset}. That port minted single-use tokens and
 * {@code password_reset_token.used} was designed to be flipped on redemption, but nothing ever
 * redeemed one: there was no service, no port and no endpoint, so every reset request produced a
 * token that could not be spent. LLFR-IDENTITY-01.5.
 */
public interface ResetPassword {

  /**
   * Redeems {@code token} and replaces the account's password.
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.InvalidResetTokenException if the token is
   *     unknown, already used, or expired (410 — deliberately indistinguishable)
   * @throws org.shakvilla.beatzmedia.identity.domain.WeakPasswordException if the new password is
   *     shorter than the minimum length (422)
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountSuspendedException if the account may
   *     not authenticate (403) — a suspended account must not regain access this way
   */
  void reset(ResetPasswordCommand command);

  /** Command carrying the plaintext token from the emailed link and the chosen new password. */
  record ResetPasswordCommand(String token, String newPassword) {}
}
