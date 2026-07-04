package org.shakvilla.beatzmedia.playback.application.port.out;

import java.time.Instant;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.domain.PlayEvent;

/**
 * Output port for {@code play_event} persistence. Single INSERT per counted play; never updates.
 * Owns the {@code play_event} table exclusively — no other module reads it directly. Playback ADD
 * §5.2/§7.
 */
public interface PlayEventRepository {

  /** Append a new counted play. */
  void insert(PlayEvent event);

  /**
   * Most recent {@code at} for a (account, track) pair within the de-dup lookback, if any. Used by
   * {@code RecordPlay} to decide whether a new call is a duplicate within the anti-inflation window
   * (Playback ADD §9). Anonymous callers are never de-duped server-side (no stable identity).
   */
  java.util.Optional<Instant> lastPlayAt(AccountId account, TrackId track);
}
