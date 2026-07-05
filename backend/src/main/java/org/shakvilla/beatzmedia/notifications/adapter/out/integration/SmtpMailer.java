package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.Mailer;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;

/**
 * {@link Mailer} implementation over Quarkus Mailer (SMTP). Dev/test config points at Mailpit
 * ({@code mail:1025}) so NO real email is ever sent outside prod (OQ-9, docker-compose.yml,
 * application.properties {@code quarkus.mailer.host}); {@code %test.quarkus.mailer.mock=true}
 * additionally mocks the transport entirely in unit/integration test runs. Notifications ADD §5.2.
 *
 * <p><strong>No PII in logs.</strong> The recipient address is never logged — only a fixed,
 * non-identifying message on success/failure.
 *
 * <p><strong>Failure classification.</strong> Quarkus Mailer wraps SMTP failures in a runtime
 * exception with no stable subtype to distinguish transient (connection/timeout) from permanent
 * (invalid recipient) failures at this abstraction level, so this adapter conservatively treats
 * every send failure as {@link TransientDeliveryException} (retryable) — a message is never
 * silently dropped as unrecoverable without at least one retry attempt. A future refinement can
 * inspect the underlying Vert.x mail SMTP reply code to detect permanent (5xx) rejections.
 */
@ApplicationScoped
public class SmtpMailer implements Mailer {

  private static final Logger LOG = Logger.getLogger(SmtpMailer.class);

  private final io.quarkus.mailer.Mailer quarkusMailer;

  @Inject
  public SmtpMailer(io.quarkus.mailer.Mailer quarkusMailer) {
    this.quarkusMailer = quarkusMailer;
  }

  @Override
  public void send(EmailMessage message) {
    try {
      io.quarkus.mailer.Mail mail =
          io.quarkus.mailer.Mail.withText(message.to(), message.subject(), message.body());
      quarkusMailer.send(mail);
      LOG.info("email dispatched successfully");
    } catch (RuntimeException e) {
      LOG.warn("email dispatch failed; classified as transient (eligible for retry)");
      throw new TransientDeliveryException("email send failed", e);
    }
  }
}
