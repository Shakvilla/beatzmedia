package org.shakvilla.beatzmedia.analytics.domain;

import java.time.LocalDate;

/**
 * A single time bucket a rollup row is keyed on: a bucket start date at a given {@link Grain}.
 * Analytics ADD §3.1.
 *
 * <p>Bucket start convention: {@code DAILY} → the day itself; {@code WEEKLY} → the Monday of the
 * ISO week; {@code MONTHLY} → the first day of the month. Normalisation happens in
 * {@link #startOf(LocalDate, Grain)} so the same instant always resolves to the same bucket
 * regardless of which day within it is used to compute the key (required for the upsert-by-key
 * idempotency invariant, ADD §4.1).
 */
public record RollupBucket(LocalDate bucket, Grain grain) {

  public RollupBucket {
    if (bucket == null) {
      throw new IllegalArgumentException("bucket must not be null");
    }
    if (grain == null) {
      throw new IllegalArgumentException("grain must not be null");
    }
  }

  /** Normalise an arbitrary date onto its bucket start for the given grain. */
  public static LocalDate startOf(LocalDate date, Grain grain) {
    return switch (grain) {
      case DAILY -> date;
      case WEEKLY -> date.minusDays(date.getDayOfWeek().getValue() - 1L); // Monday
      case MONTHLY -> date.withDayOfMonth(1);
    };
  }

  /** Build the normalised bucket containing {@code date} at {@code grain}. */
  public static RollupBucket of(LocalDate date, Grain grain) {
    return new RollupBucket(startOf(date, grain), grain);
  }
}
