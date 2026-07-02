package org.shakvilla.beatzmedia.payments.application.port.out;

import org.shakvilla.beatzmedia.payments.domain.PaymentEvent;

/**
 * Output port for the {@code payment_event} idempotency backstop (payments ADD §4.2, WU-PAY-2). Owns
 * only the payments module's {@code payment_event} table; no cross-module joins. Transaction boundary
 * = the calling application service ({@code @Transactional}).
 */
public interface PaymentEventRepository {

  /**
   * Record a freshly-received provider event. Returns {@code true} if this is the first time the
   * event's {@code providerEventId} has been seen (row inserted), or {@code false} if it was already
   * recorded (a duplicate/replayed webhook). The {@code provider_event_id} UNIQUE constraint is the
   * durable backstop against races; a concurrent duplicate surfaces as {@code false}, never a raw
   * constraint-violation 500. This makes {@code HandleProviderWebhook} apply a settlement at most
   * once (LLFR-PAYMENTS-01.2 AC).
   */
  boolean recordEvent(PaymentEvent event);
}
