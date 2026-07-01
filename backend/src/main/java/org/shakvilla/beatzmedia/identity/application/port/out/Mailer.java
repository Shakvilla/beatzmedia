package org.shakvilla.beatzmedia.identity.application.port.out;

/**
 * Output port: sends transactional identity emails. Adapter: {@code LoggingMailer} (config-driven
 * dev/test capture stub for v1 — logs the message instead of sending real SMTP, see OQ note in
 * identity ADD §5.2). Identity ADD §4.2.
 */
public interface Mailer {

  /** Sends the single-use password reset link/token to {@code email}. */
  void sendPasswordReset(String email, String resetToken);
}
