package org.shakvilla.beatzmedia.events.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code ticket} table. Domain types carry no ORM annotations. {@code order_id}
 * is a bare TEXT reference to a {@code commerce} order — no cross-module FK (Events ADD §7 /
 * migration V954).
 */
@Entity
@Table(name = "ticket")
public class TicketEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "event_id", nullable = false)
  public String eventId;

  @Column(name = "tier_id", nullable = false)
  public String tierId;

  @Column(name = "order_id", nullable = false)
  public String orderId;

  @Column(name = "holder_account_id", nullable = false)
  public String holderAccountId;

  @Column(name = "holder_name", nullable = false)
  public String holderName;

  @Column(name = "qr_ref", nullable = false, unique = true)
  public String qrRef;

  @Column(name = "issued_at", nullable = false)
  public Instant issuedAt;
}
