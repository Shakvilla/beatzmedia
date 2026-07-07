package org.shakvilla.beatzmedia.analytics.domain;

import java.time.Instant;

/**
 * A single staged new-follower fact, appended when analytics observes a {@code Followed} event
 * (library) with {@code kind=artist}. Owned exclusively by analytics. Analytics ADD §3.1 / §4.1.
 */
public record FollowFact(String id, String artistId, Instant occurredAt, boolean processed) {

  public static FollowFact unprocessed(String id, String artistId, Instant occurredAt) {
    return new FollowFact(id, artistId, occurredAt, false);
  }
}
