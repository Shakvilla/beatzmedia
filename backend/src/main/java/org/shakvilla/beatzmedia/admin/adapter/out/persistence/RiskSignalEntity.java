package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code risk_signal} table. Domain types carry no ORM annotations. Admin ADD
 * §5.2 / §7 / V964 migration (WU-ADM-6).
 */
@Entity
@Table(name = "risk_signal")
public class RiskSignalEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "subject_ref", nullable = false)
  public String subjectRef;

  @Column(name = "type", nullable = false)
  public String type;

  @Column(name = "detail")
  public String detail;

  /** Values: high | med | low */
  @Column(name = "level", nullable = false)
  public String level;

  /** Values: open | cleared | banned */
  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "detected_at", nullable = false)
  public Instant detectedAt;
}
