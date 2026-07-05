package org.shakvilla.beatzmedia.notifications.application.port.out;

import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;

/**
 * Output port for the email delivery channel (WU-NOT-2, LLFR-NOTIF-02.1). Notifications ADD §4.2.
 *
 * <p>Implementations: dev/test → {@code SmtpMailer} over Quarkus Mailer → Mailpit; prod → a real
 * SMTP/provider adapter behind the same port (config/secrets human deploy gate — never real
 * provider calls in dev/test, OQ-9).
 */
public interface Mailer {

  /**
   * Sends {@code message}. Throws {@link TransientDeliveryException} for a retryable failure
   * (timeout, 5xx) or {@link PermanentDeliveryException} for a non-retryable one (invalid address,
   * hard 4xx rejection). Never throws any other exception type.
   */
  void send(EmailMessage message);
}
