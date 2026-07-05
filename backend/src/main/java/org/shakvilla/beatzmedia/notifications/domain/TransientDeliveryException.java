package org.shakvilla.beatzmedia.notifications.domain;

/**
 * Thrown by a {@code Mailer}/{@code SmsSender} outbound adapter when a send fails in a way that is
 * likely to succeed on retry (timeout, 5xx, connection reset). Drives the {@link DeliveryAttempt}
 * {@code pending|failed -> failed} transition with backoff, up to {@code maxRetries} (INV-N5).
 * Notifications ADD §4.2 / §5.2.
 */
public class TransientDeliveryException extends RuntimeException {

  public TransientDeliveryException(String message) {
    super(message);
  }

  public TransientDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
