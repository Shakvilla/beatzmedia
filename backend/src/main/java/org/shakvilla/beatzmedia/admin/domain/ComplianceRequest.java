package org.shakvilla.beatzmedia.admin.domain;

import java.time.Instant;

/**
 * Aggregate root for a compliance request (HLFR-ADMIN-09) — a DSAR export/delete, a takedown, or a
 * tax obligation, tied to the Ghana Data Protection Act. Pure Java, no framework imports. Admin ADD
 * §3.
 *
 * <p>Fields: {@code id}, {@code type}, {@code subjectRef} (opaque ref to the data subject/target),
 * {@code detail} (nullable), {@code dueAt} (nullable), {@code status}, {@code createdAt}.
 *
 * <p>Mutations: {@code start} moves {@code new|overdue → in_progress} (409 from in_progress/
 * completed); {@code complete} moves any non-completed state {@code → completed} (409 if already
 * completed). {@code export} (DSAR data export) and {@code notice} (DMCA notice) are side actions
 * that do not change the status — they are handled at the service layer (audited).
 */
public final class ComplianceRequest {

  private final String id;
  private final ComplianceType type;
  private final String subjectRef;
  private final String detail;
  private final Instant dueAt;
  private ComplianceStatus status;
  private final Instant createdAt;

  /** Reconstitutes (or newly creates) a request from/for persistence — no invariant re-derivation. */
  public ComplianceRequest(
      String id,
      ComplianceType type,
      String subjectRef,
      String detail,
      Instant dueAt,
      ComplianceStatus status,
      Instant createdAt) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("ComplianceRequest id must not be blank");
    }
    if (type == null) {
      throw new IllegalArgumentException("ComplianceRequest type must not be null");
    }
    if (subjectRef == null || subjectRef.isBlank()) {
      throw new IllegalArgumentException("ComplianceRequest subjectRef must not be blank");
    }
    if (status == null) {
      throw new IllegalArgumentException("ComplianceRequest status must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("ComplianceRequest createdAt must not be null");
    }
    this.id = id;
    this.type = type;
    this.subjectRef = subjectRef;
    this.detail = detail;
    this.dueAt = dueAt;
    this.status = status;
    this.createdAt = createdAt;
  }

  /**
   * Starts work on the request ({@code new|overdue → in_progress}).
   *
   * @throws IllegalComplianceTransitionException if already {@code in_progress} or {@code completed}
   *     (409)
   */
  public void start() {
    if (status != ComplianceStatus.NEW && status != ComplianceStatus.OVERDUE) {
      throw new IllegalComplianceTransitionException(id, "start");
    }
    this.status = ComplianceStatus.IN_PROGRESS;
  }

  /**
   * Completes the request ({@code → completed}).
   *
   * @throws IllegalComplianceTransitionException if already {@code completed} (409)
   */
  public void complete() {
    if (status == ComplianceStatus.COMPLETED) {
      throw new IllegalComplianceTransitionException(id, "complete");
    }
    this.status = ComplianceStatus.COMPLETED;
  }

  public String getId() {
    return id;
  }

  public ComplianceType getType() {
    return type;
  }

  public String getSubjectRef() {
    return subjectRef;
  }

  public String getDetail() {
    return detail;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public ComplianceStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
