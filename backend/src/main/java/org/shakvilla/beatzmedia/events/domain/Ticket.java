package org.shakvilla.beatzmedia.events.domain;

import java.time.Instant;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * A minted ticket — one per seat sold. Minted ONLY as a result of a settled commerce ticket line
 * (carries {@code orderId}); never on cart add or checkout-initiate (INV-EVT-3). Framework-free.
 * Events ADD §3.
 */
public final class Ticket {

  private final TicketId id;
  private final EventId eventId;
  private final TicketTierId tierId;
  private final OrderId orderId;
  private final AccountId holderAccountId;
  private final String holderName;
  private final String qrRef;
  private final Instant issuedAt;

  public Ticket(
      TicketId id,
      EventId eventId,
      TicketTierId tierId,
      OrderId orderId,
      AccountId holderAccountId,
      String holderName,
      String qrRef,
      Instant issuedAt) {
    if (holderName == null || holderName.isBlank()) {
      throw new IllegalArgumentException("holderName must not be blank");
    }
    if (qrRef == null || qrRef.isBlank()) {
      throw new IllegalArgumentException("qrRef must not be blank");
    }
    this.id = id;
    this.eventId = eventId;
    this.tierId = tierId;
    this.orderId = orderId;
    this.holderAccountId = holderAccountId;
    this.holderName = holderName;
    this.qrRef = qrRef;
    this.issuedAt = issuedAt;
  }

  public TicketId id() {
    return id;
  }

  public EventId eventId() {
    return eventId;
  }

  public TicketTierId tierId() {
    return tierId;
  }

  public OrderId orderId() {
    return orderId;
  }

  public AccountId holderAccountId() {
    return holderAccountId;
  }

  public String holderName() {
    return holderName;
  }

  public String qrRef() {
    return qrRef;
  }

  public Instant issuedAt() {
    return issuedAt;
  }
}
