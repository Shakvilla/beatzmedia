package org.shakvilla.beatzmedia.playback.domain;

import java.time.Instant;
import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Append-only fact recording a single counted play. Never updated or deleted; rolled up by
 * {@code analytics}. Framework-free — no ORM annotations (mapped in the persistence adapter).
 * Playback ADD §3 / §7.
 */
public final class PlayEvent {

  private final String id;
  private final AccountId accountId;
  private final TrackId trackId;
  private final Instant at;
  private final PlaybackMode fullVsPreview;
  private final PlaySource source;

  public PlayEvent(
      String id,
      AccountId accountId,
      TrackId trackId,
      Instant at,
      PlaybackMode fullVsPreview,
      PlaySource source) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("PlayEvent id must not be blank");
    }
    if (trackId == null) {
      throw new IllegalArgumentException("PlayEvent trackId must not be null");
    }
    if (at == null) {
      throw new IllegalArgumentException("PlayEvent at must not be null");
    }
    if (fullVsPreview == null) {
      throw new IllegalArgumentException("PlayEvent fullVsPreview must not be null");
    }
    this.id = id;
    this.accountId = accountId; // nullable — anonymous plays
    this.trackId = trackId;
    this.at = at;
    this.fullVsPreview = fullVsPreview;
    this.source = source != null ? source : PlaySource.player;
  }

  /** Factory for a brand-new counted play (id/at supplied by the platform ports). */
  public static PlayEvent record(
      String id,
      Optional<AccountId> accountId,
      TrackId trackId,
      Instant at,
      PlaybackMode fullVsPreview,
      PlaySource source) {
    return new PlayEvent(id, accountId.orElse(null), trackId, at, fullVsPreview, source);
  }

  public String getId() {
    return id;
  }

  public Optional<AccountId> getAccountId() {
    return Optional.ofNullable(accountId);
  }

  public TrackId getTrackId() {
    return trackId;
  }

  public Instant getAt() {
    return at;
  }

  public PlaybackMode getFullVsPreview() {
    return fullVsPreview;
  }

  public PlaySource getSource() {
    return source;
  }
}
