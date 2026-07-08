package org.shakvilla.beatzmedia.events.domain;

/**
 * A caller-supplied idempotency key for the internal {@code IssueTicket} port (Events ADD §4.1).
 * Deliberately local to this module (mirrors {@code payments.domain.IdempotencyKey} rather than
 * importing it) so {@code events} does not take a compile-time dependency on {@code payments} for
 * an internal, non-REST value type. The actual replay guard is
 * {@code EventRepository#ticketExistsForOrderTier(OrderId, TicketTierId)}; this key is carried for
 * caller-side traceability/logging.
 */
public record IdempotencyKey(String value) {

  public IdempotencyKey {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("IdempotencyKey value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
