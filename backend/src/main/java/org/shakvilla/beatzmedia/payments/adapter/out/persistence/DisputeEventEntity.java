package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code dispute_event} table (V705, WU-PAY-5). Payments ADD §5.2. */
@Entity
@Table(name = "dispute_event")
public class DisputeEventEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "dispute_id", nullable = false)
  public String disputeId;

  @Column(name = "text", nullable = false)
  public String text;

  @Column(name = "actor")
  public String actor;

  @Column(name = "at", nullable = false)
  public Instant at;
}
