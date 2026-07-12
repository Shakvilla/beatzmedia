package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code compliance_request} table. Domain types carry no ORM annotations. Admin
 * ADD §5.2 / §7 / V965 migration (WU-ADM-8).
 */
@Entity
@Table(name = "compliance_request")
public class ComplianceRequestEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  /** Values: DSAR-export | DSAR-delete | Takedown | Tax */
  @Column(name = "type", nullable = false)
  public String type;

  @Column(name = "subject_ref", nullable = false)
  public String subjectRef;

  @Column(name = "detail")
  public String detail;

  @Column(name = "due_at")
  public Instant dueAt;

  /** Values: new | in_progress | completed | overdue */
  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
