package org.shakvilla.beatzmedia.events.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code ticket_tier} table. Domain types carry no ORM annotations. {@code sold}
 * is DB CHECK-backstopped ({@code sold >= 0 AND sold <= capacity}, INV-EVT-1). Events ADD §7 /
 * migration V953.
 */
@Entity
@Table(name = "ticket_tier")
public class TicketTierEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "event_id", nullable = false)
  public String eventId;

  @Column(name = "name", nullable = false)
  public String name;

  /** Minor units (pesewas); currency is always GHS for v1 (no currency column, Events ADD §7). */
  @Column(name = "price_minor", nullable = false)
  public long priceMinor;

  @Column(name = "capacity", nullable = false)
  public int capacity;

  @Column(name = "sold", nullable = false)
  public int sold;

  /** JSON array of perk strings, e.g. {@code ["Fast-track entry"]}. */
  @Column(name = "perks", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String perksJson;
}
