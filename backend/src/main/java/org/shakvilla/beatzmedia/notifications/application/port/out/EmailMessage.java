package org.shakvilla.beatzmedia.notifications.application.port.out;

/**
 * Outbound email payload for {@link Mailer#send}. Framework-free DTO — no javax.mail /
 * quarkus-mailer types leak into the application layer. Notifications ADD §4.2.
 *
 * @param to recipient email address
 * @param subject email subject line
 * @param body plain-text body (pre-rendered display text; no PII beyond the recipient's own
 *     address, which is never logged — see {@code SmtpMailer})
 * @param htmlBody optional HTML alternative for clients that support rich email
 * @param idempotencyKey provider-facing idempotency key (notification id + channel), so a retried
 *     send is deduped by the provider itself where supported
 */
public record EmailMessage(String to, String subject, String body, String htmlBody, String idempotencyKey) {
  /** Compatibility constructor for existing text-only notification templates. */
  public EmailMessage(String to, String subject, String body, String idempotencyKey) {
    this(to, subject, body, null, idempotencyKey);
  }
}
