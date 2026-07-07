package org.shakvilla.beatzmedia.analytics.domain;

import java.time.Instant;

/**
 * A single staged counted-play fact, appended when analytics observes a {@code PlayRecorded} event
 * (playback). {@code artistId} is resolved at observation time via catalog's {@code GetTrack} INPUT
 * port (in-process, no cross-module table read — mirrors {@code playback}'s own
 * {@code CatalogReaderAdapter}). {@code accountId} is nullable (anonymous plays are still counted
 * towards plays, but never towards unique listeners). Analytics ADD §3.1 / §4.1.
 */
public record PlayFact(
    String id, String artistId, String accountId, Instant occurredAt, boolean processed) {

  public static PlayFact unprocessed(String id, String artistId, String accountId, Instant occurredAt) {
    return new PlayFact(id, artistId, accountId, occurredAt, false);
  }
}
