package org.shakvilla.beatzmedia.notifications.application.port.out;

import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;

/**
 * Output port for the SMS delivery channel (WU-NOT-2, LLFR-NOTIF-02.1). Notifications ADD §4.2.
 *
 * <p>Implementations: dev/test → {@code HttpSmsSender} against the in-repo SMS capture stub
 * (OQ-9, no real provider calls); prod → a real SMS provider client behind the same port
 * (config/secrets human deploy gate).
 */
public interface SmsSender {

  /** Provider selected for a new outbound SMS; persist it before sending so retries stay pinned. */
  default String selectedProvider() {
    return "capture";
  }

  /** Sends through the provider recorded for the message. */
  default void send(SmsMessage message, String provider) {
    send(message);
  }

  /**
   * Sends {@code message}. Throws {@link TransientDeliveryException} for a retryable failure or
   * {@link PermanentDeliveryException} for a non-retryable one. Never throws any other exception
   * type.
   */
  void send(SmsMessage message);
}
