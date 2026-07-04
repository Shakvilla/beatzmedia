package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One admin payout run (weekly batch or single send). Aggregate root grouping the {@link PayoutTxn}
 * rows it executed (payments ADD §3). {@code runBy} is the admin actor id (INV-10). Framework-free.
 */
public final class PayoutBatch {

  private final String id;
  private final PayoutBatchKind kind;
  private final String runBy;
  private long totalMinor;
  private int count;
  private final Instant runAt;

  private PayoutBatch(
      String id, PayoutBatchKind kind, String runBy, long totalMinor, int count, Instant runAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.runBy = requireText(runBy, "runBy");
    this.totalMinor = totalMinor;
    this.count = count;
    this.runAt = Objects.requireNonNull(runAt, "runAt");
  }

  /** Start a new (empty) payout run of the given kind by the given admin actor. */
  public static PayoutBatch start(String id, PayoutBatchKind kind, String runBy, Instant runAt) {
    return new PayoutBatch(id, kind, runBy, 0L, 0, runAt);
  }

  /** Reconstitute from persistence. */
  public static PayoutBatch reconstitute(
      String id, PayoutBatchKind kind, String runBy, long totalMinor, int count, Instant runAt) {
    return new PayoutBatch(id, kind, runBy, totalMinor, count, runAt);
  }

  /** Record that one withdrawal of {@code amountMinor} was executed under this batch. */
  public void recordPayment(long amountMinor) {
    if (amountMinor <= 0) {
      throw new IllegalArgumentException("payout amount must be positive minor units");
    }
    this.totalMinor += amountMinor;
    this.count += 1;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  public String getId() {
    return id;
  }

  public PayoutBatchKind getKind() {
    return kind;
  }

  public String getRunBy() {
    return runBy;
  }

  public long getTotalMinor() {
    return totalMinor;
  }

  public int getCount() {
    return count;
  }

  public Instant getRunAt() {
    return runAt;
  }
}
