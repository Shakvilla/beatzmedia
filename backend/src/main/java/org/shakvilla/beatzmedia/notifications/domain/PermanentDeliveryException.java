package org.shakvilla.beatzmedia.notifications.domain;

/**
 * Thrown by a {@code Mailer}/{@code SmsSender} outbound adapter when a send fails in a way that
 * will never succeed on retry (invalid address, provider hard-rejects the payload, 4xx other than
 * rate-limit). Drives the {@link DeliveryAttempt} transition straight to {@code dead} — no retry
 * loop is entered. Notifications ADD §4.2 / §5.2.
 */
public class PermanentDeliveryException extends RuntimeException {

  public PermanentDeliveryException(String message) {
    super(message);
  }

  public PermanentDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
