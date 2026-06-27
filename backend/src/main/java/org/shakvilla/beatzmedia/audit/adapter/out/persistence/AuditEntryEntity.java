package org.shakvilla.beatzmedia.audit.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code audit_entry} table. Append-only. Domain types carry no ORM
 * annotations; this adapter class is the only place Hibernate annotations appear. Audit ADD §5.2 /
 * migration V941.
 */
@Entity
@Table(name = "audit_entry")
public class AuditEntryEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "actor_id", nullable = false)
  public String actorId;

  @Column(name = "action", nullable = false)
  public String action;

  @Column(name = "target_type", nullable = false)
  public String targetType;

  @Column(name = "target_id", nullable = false)
  public String targetId;

  @Column(name = "type", nullable = false)
  public String type;

  @Column(name = "reason")
  public String reason;

  @Column(name = "occurred_at", nullable = false)
  public Instant occurredAt;
}
