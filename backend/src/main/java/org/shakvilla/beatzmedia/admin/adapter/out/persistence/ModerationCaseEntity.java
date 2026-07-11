package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code moderation_case} table. Domain types carry no ORM annotations. Admin
 * ADD §5.2 / §7 / V963 migration.
 */
@Entity
@Table(name = "moderation_case")
public class ModerationCaseEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "target_ref", nullable = false)
  public String targetRef;

  @Column(name = "reporter", nullable = false)
  public String reporter;

  /** Values: Copyright | Hate speech | Sexual content | Spam | Impersonation */
  @Column(name = "reason", nullable = false)
  public String reason;

  /** Values: high | med | low */
  @Column(name = "severity", nullable = false)
  public String severity;

  /** Values: open | in_review | resolved */
  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "sla_due_at")
  public Instant slaDueAt;

  @Column(name = "is_escalated", nullable = false)
  public boolean escalated;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
