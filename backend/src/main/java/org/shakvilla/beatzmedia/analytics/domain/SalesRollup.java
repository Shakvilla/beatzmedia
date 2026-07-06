package org.shakvilla.beatzmedia.analytics.domain;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * A single {@code (artist, bucket, grain)} sales/tips aggregate row. Settled money only, minor
 * units (INV-11). Analytics ADD §3.1 / §7.
 *
 * <p><strong>royaltyMinor is always 0.</strong> OQ-4 resolved to pure buy-to-own (no royalty
 * accrual model) — the column is kept because the ADD schema defines it, but analytics never
 * computes or accrues a non-zero value into it (see {@link #zero(ArtistId, RollupBucket)} and
 * every upsert path in the rollup service).
 */
public record SalesRollup(
    ArtistId artistId,
    RollupBucket bucket,
    long salesMinor,
    long tipsMinor,
    long royaltyMinor,
    int units) {

  public SalesRollup {
    if (artistId == null) {
      throw new IllegalArgumentException("artistId must not be null");
    }
    if (bucket == null) {
      throw new IllegalArgumentException("bucket must not be null");
    }
    if (royaltyMinor != 0) {
      // Defence-in-depth: royalty is not modelled (pure buy-to-own, OQ-4). Never a non-zero value.
      throw new IllegalArgumentException("royaltyMinor must always be 0 (OQ-4: no royalty model)");
    }
  }

  /** An empty rollup row for a bucket with no settled facts yet. */
  public static SalesRollup zero(ArtistId artistId, RollupBucket bucket) {
    return new SalesRollup(artistId, bucket, 0L, 0L, 0L, 0);
  }

  /** Fold one more settled sale (gross minor units) into this rollup, returning a new instance. */
  public SalesRollup plusSale(long grossMinor) {
    return new SalesRollup(artistId, bucket, salesMinor + grossMinor, tipsMinor, 0L, units + 1);
  }

  /** Fold one more settled tip (creator-share minor units) into this rollup. */
  public SalesRollup plusTip(long creatorShareMinor) {
    return new SalesRollup(artistId, bucket, salesMinor, tipsMinor + creatorShareMinor, 0L, units);
  }
}
