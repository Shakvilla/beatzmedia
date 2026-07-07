package org.shakvilla.beatzmedia.analytics.domain;

import java.time.Instant;

/**
 * A single staged settled-tip fact, appended when analytics observes a {@code TipReceived} event
 * (payments). Owned exclusively by analytics. Framework-free. Analytics ADD §3.1 / §4.1.
 */
public record TipFact(
    String id,
    String artistId,
    long creatorShareMinor,
    String currency,
    Instant occurredAt,
    boolean processed) {

  public static TipFact unprocessed(
      String id, String artistId, long creatorShareMinor, String currency, Instant occurredAt) {
    return new TipFact(id, artistId, creatorShareMinor, currency, occurredAt, false);
  }
}
