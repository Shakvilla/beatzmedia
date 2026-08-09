package org.shakvilla.beatzmedia.identity.application.port.out;

/**
 * Output port: sends transactional identity emails. Adapter: {@code SmtpPasswordResetMailer}, over
 * Quarkus Mailer. Dev/test point at Mailpit and {@code %test.quarkus.mailer.mock=true} mocks the
 * transport, so no real mail leaves a developer machine or CI. Identity ADD §4.2.
 */
public interface Mailer {

  /** Sends the single-use password reset link/token to {@code email}. */
  void sendPasswordReset(String email, String resetToken);
}
