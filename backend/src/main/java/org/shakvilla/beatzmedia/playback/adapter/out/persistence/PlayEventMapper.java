package org.shakvilla.beatzmedia.playback.adapter.out.persistence;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.domain.PlayEvent;
import org.shakvilla.beatzmedia.playback.domain.PlaySource;
import org.shakvilla.beatzmedia.playback.domain.PlaybackMode;

/**
 * Maps {@link PlayEvent} (domain) to {@link PlayEventEntity} (JPA). No framework annotations on
 * the domain object. Conventions §6. Append-only — no {@code toDomain} needed (write path only).
 */
final class PlayEventMapper {

  private PlayEventMapper() {}

  static PlayEventEntity toEntity(PlayEvent event) {
    PlayEventEntity e = new PlayEventEntity();
    e.id = event.getId();
    e.accountId = event.getAccountId().map(AccountId::value).orElse(null);
    e.trackId = event.getTrackId().value();
    e.at = event.getAt();
    e.fullVsPreview = event.getFullVsPreview() == PlaybackMode.FULL ? "full" : "preview";
    e.source = event.getSource().name();
    return e;
  }

  static PlayEvent toDomain(PlayEventEntity e) {
    return new PlayEvent(
        e.id,
        e.accountId != null ? new AccountId(e.accountId) : null,
        new TrackId(e.trackId),
        e.at,
        "full".equals(e.fullVsPreview) ? PlaybackMode.FULL : PlaybackMode.PREVIEW,
        PlaySource.valueOf(e.source));
  }
}
