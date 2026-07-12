package org.shakvilla.beatzmedia.admin.domain;

import java.time.Instant;

/**
 * Aggregate root for a trust &amp; safety risk signal (HLFR-ADMIN-07). Pure Java, no framework
 * imports. Admin ADD §3.
 *
 * <p>Fields: {@code id}, {@code subjectRef} (opaque ref to the flagged subject — for the {@code ban}
 * action this is treated as the target account ref; a non-account subject simply 404s at the
 * identity ban boundary), {@code type} (free-text signal type, e.g. {@code "Payment fraud"}),
 * {@code detail} (nullable), {@code level}, {@code status}, {@code detectedAt}.
 *
 * <p>Mutations enforce that any action ({@code review}/{@code clear}/{@code ban}) targets an {@code
 * open} signal — otherwise {@link IllegalRiskTransitionException} (409). {@code review} is an
 * audited acknowledgment that leaves the status {@code open} (the frontend {@code RiskStatus} enum
 * has no reviewed state); {@code clear} → {@code cleared}; {@code ban} → {@code banned}.
 */
public final class RiskSignal {

  private final String id;
  private final String subjectRef;
  private final String type;
  private final String detail;
  private final RiskLevel level;
  private RiskStatus status;
  private final Instant detectedAt;

  /** Reconstitutes (or newly creates) a signal from/for persistence — no invariant re-derivation. */
  public RiskSignal(
      String id,
      String subjectRef,
      String type,
      String detail,
      RiskLevel level,
      RiskStatus status,
      Instant detectedAt) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("RiskSignal id must not be blank");
    }
    if (subjectRef == null || subjectRef.isBlank()) {
      throw new IllegalArgumentException("RiskSignal subjectRef must not be blank");
    }
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("RiskSignal type must not be blank");
    }
    if (level == null) {
      throw new IllegalArgumentException("RiskSignal level must not be null");
    }
    if (status == null) {
      throw new IllegalArgumentException("RiskSignal status must not be null");
    }
    if (detectedAt == null) {
      throw new IllegalArgumentException("RiskSignal detectedAt must not be null");
    }
    this.id = id;
    this.subjectRef = subjectRef;
    this.type = type;
    this.detail = detail;
    this.level = level;
    this.status = status;
    this.detectedAt = detectedAt;
  }

  /**
   * Marks the signal as reviewed — an audited acknowledgment that leaves the status {@code open}
   * (there is no distinct reviewed state). Only an {@code open} signal may be reviewed.
   *
   * @throws IllegalRiskTransitionException if the signal is not {@code open} (409)
   */
  public void review() {
    requireOpen("review");
    // No status change: 'reviewed' is not a distinct RiskStatus. The admin service audits the act.
  }

  /**
   * Clears the signal ({@code open → cleared}).
   *
   * @throws IllegalRiskTransitionException if the signal is not {@code open} (409)
   */
  public void clear() {
    requireOpen("clear");
    this.status = RiskStatus.CLEARED;
  }

  /**
   * Bans the signal's subject ({@code open → banned}); the caller also bans the subject account via
   * the identity port and appends the audit entry (INV-10).
   *
   * @throws IllegalRiskTransitionException if the signal is not {@code open} (409)
   */
  public void ban() {
    requireOpen("ban");
    this.status = RiskStatus.BANNED;
  }

  private void requireOpen(String action) {
    if (this.status != RiskStatus.OPEN) {
      throw new IllegalRiskTransitionException(id, action);
    }
  }

  public String getId() {
    return id;
  }

  public String getSubjectRef() {
    return subjectRef;
  }

  public String getType() {
    return type;
  }

  public String getDetail() {
    return detail;
  }

  public RiskLevel getLevel() {
    return level;
  }

  public RiskStatus getStatus() {
    return status;
  }

  public Instant getDetectedAt() {
    return detectedAt;
  }
}
