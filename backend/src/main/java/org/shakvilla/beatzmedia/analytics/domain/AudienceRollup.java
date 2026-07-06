package org.shakvilla.beatzmedia.analytics.domain;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * A single {@code (artist, bucket, grain)} engagement aggregate row: plays + follower/listener
 * counters. Analytics ADD §3.1 / §7.
 */
public record AudienceRollup(
    ArtistId artistId,
    RollupBucket bucket,
    long plays,
    int followersGained,
    int uniqueListeners,
    int completionPct) {

  public AudienceRollup {
    if (artistId == null) {
      throw new IllegalArgumentException("artistId must not be null");
    }
    if (bucket == null) {
      throw new IllegalArgumentException("bucket must not be null");
    }
    if (completionPct < 0 || completionPct > 100) {
      throw new IllegalArgumentException("completionPct must be 0..100");
    }
  }

  /** An empty rollup row for a bucket with no engagement facts yet. */
  public static AudienceRollup zero(ArtistId artistId, RollupBucket bucket) {
    return new AudienceRollup(artistId, bucket, 0L, 0, 0, 0);
  }

  /** Fold one more counted play into this rollup (unique-listener/completion tracked separately). */
  public AudienceRollup plusPlay() {
    return new AudienceRollup(artistId, bucket, plays + 1, followersGained, uniqueListeners, completionPct);
  }

  /** Fold one more new follower into this rollup. */
  public AudienceRollup plusFollower() {
    return new AudienceRollup(artistId, bucket, plays, followersGained + 1, uniqueListeners, completionPct);
  }

  /** Return a copy with the unique-listener count set (computed by the job from distinct accounts). */
  public AudienceRollup withUniqueListeners(int count) {
    return new AudienceRollup(artistId, bucket, plays, followersGained, count, completionPct);
  }
}
