package org.shakvilla.beatzmedia.payments.domain;

/**
 * The settlement outcome a provider event (webhook or poll) reports for a {@link PaymentIntent}.
 * Pure Java, no framework imports.
 *
 * <ul>
 *   <li>{@code SETTLED} — the rail confirmed the charge; drives {@code pending → settled} and a
 *       {@link PaymentSettled} event (INV-1 grant trigger downstream).
 *   <li>{@code FAILED} — the rail rejected the charge; drives {@code pending → failed} and a
 *       {@link PaymentFailed} event.
 *   <li>{@code PENDING} — the rail is still processing; the intent stays {@code pending} (used by the
 *       timeout poll to decide whether to keep waiting or time out).
 * </ul>
 */
public enum PaymentEventType {
  SETTLED,
  FAILED,
  PENDING
}
