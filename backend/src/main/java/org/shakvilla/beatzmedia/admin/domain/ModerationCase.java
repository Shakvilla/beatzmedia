package org.shakvilla.beatzmedia.admin.domain;

import java.time.Instant;

/**
 * Aggregate root for a moderation-queue report (HLFR-ADMIN-04) or a catalog-item flag
 * (HLFR-ADMIN-03's {@code flag} action creates one of these targeting the release — see admin ADD
 * §13, WU-ADM-3 as-built, for the full reasoning). Pure Java, no framework imports. Admin ADD §3.
 *
 * <p>Fields: {@code id}, {@code targetRef} (opaque id into another module — {@code
 * "release:<releaseId>"} for catalog-sourced flags; other content types are not sourced by any
 * built WU yet), {@code reporter} (opaque actor ref — the flagging admin's account id for
 * catalog-sourced flags; a future end-user reporting flow is out of scope here), {@code reason},
 * {@code severity}, {@code status}, {@code slaDueAt}, {@code escalated}, {@code createdAt}.
 * Mutations enforce: any action on an already-{@code resolved} case is illegal (409); a repeat
 * {@code escalate} on an already-escalated case is illegal (409).
 */
public final class ModerationCase {

  /** SLA window applied to every new case. Mirrors {@code MOD_SLA_HOURS} in admin-data.ts. */
  public static final int DEFAULT_SLA_HOURS = 6;

  private final String id;
  private final String targetRef;
  private final String reporter;
  private final ModReason reason;
  private ModSeverity severity;
  private ModStatus status;
  private final Instant slaDueAt;
  private boolean escalated;
  private final Instant createdAt;

  /** Reconstitutes (or newly creates) a case from/for persistence — no invariant re-derivation. */
  public ModerationCase(
      String id,
      String targetRef,
      String reporter,
      ModReason reason,
      ModSeverity severity,
      ModStatus status,
      Instant slaDueAt,
      boolean escalated,
      Instant createdAt) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("ModerationCase id must not be blank");
    }
    if (targetRef == null || targetRef.isBlank()) {
      throw new IllegalArgumentException("ModerationCase targetRef must not be blank");
    }
    if (reporter == null || reporter.isBlank()) {
      throw new IllegalArgumentException("ModerationCase reporter must not be blank");
    }
    if (reason == null) {
      throw new IllegalArgumentException("ModerationCase reason must not be null");
    }
    if (severity == null) {
      throw new IllegalArgumentException("ModerationCase severity must not be null");
    }
    if (status == null) {
      throw new IllegalArgumentException("ModerationCase status must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("ModerationCase createdAt must not be null");
    }
    this.id = id;
    this.targetRef = targetRef;
    this.reporter = reporter;
    this.reason = reason;
    this.severity = severity;
    this.status = status;
    this.slaDueAt = slaDueAt;
    this.escalated = escalated;
    this.createdAt = createdAt;
  }

  /** Factory for a newly-opened case; {@code slaDueAt} is stamped {@code createdAt + 6h}. */
  public static ModerationCase open(
      String id,
      String targetRef,
      String reporter,
      ModReason reason,
      ModSeverity severity,
      Instant now) {
    return new ModerationCase(
        id, targetRef, reporter, reason, severity, ModStatus.OPEN,
        now.plusSeconds(DEFAULT_SLA_HOURS * 3600L), false, now);
  }

  /** Marks the case as being actively reviewed. Illegal once {@code resolved} (409). */
  public void review() {
    guardNotResolved("review");
    this.status = ModStatus.IN_REVIEW;
  }

  /** Approves the reported content (kept as-is) and resolves the case. Illegal once resolved. */
  public void approve() {
    resolve("approve");
  }

  /** Removes the reported content and resolves the case. Illegal once resolved. */
  public void remove() {
    resolve("remove");
  }

  /** Dismisses the report (no action taken) and resolves the case. Illegal once resolved. */
  public void dismiss() {
    resolve("dismiss");
  }

  /**
   * Flags the case for senior review. Does not change {@code status} (matches the admin console's
   * own behaviour — escalation is an orthogonal flag, not a queue-status transition). Illegal once
   * {@code resolved}, or on a case that is already escalated (409).
   */
  public void escalate() {
    guardNotResolved("escalate");
    if (this.escalated) {
      throw new IllegalModerationTransitionException(id, "escalate");
    }
    this.escalated = true;
  }

  private void resolve(String action) {
    guardNotResolved(action);
    this.status = ModStatus.RESOLVED;
  }

  private void guardNotResolved(String action) {
    if (this.status == ModStatus.RESOLVED) {
      throw new IllegalModerationTransitionException(id, action);
    }
  }

  public String getId() {
    return id;
  }

  public String getTargetRef() {
    return targetRef;
  }

  public String getReporter() {
    return reporter;
  }

  public ModReason getReason() {
    return reason;
  }

  public ModSeverity getSeverity() {
    return severity;
  }

  public ModStatus getStatus() {
    return status;
  }

  public Instant getSlaDueAt() {
    return slaDueAt;
  }

  public boolean isEscalated() {
    return escalated;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
