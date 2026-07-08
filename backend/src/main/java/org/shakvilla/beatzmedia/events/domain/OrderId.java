package org.shakvilla.beatzmedia.events.domain;

/**
 * Opaque reference to a {@code commerce} order (the {@code ticket.order_id} column). Deliberately
 * NOT the commerce module's own {@code OrderId} type — this module never FK-joins into commerce's
 * tables (Events ADD §5.2, schema comment "commerce order ref (no cross-module FK)"); the value is
 * treated as a bare string here.
 */
public record OrderId(String value) {

  public OrderId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("OrderId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
