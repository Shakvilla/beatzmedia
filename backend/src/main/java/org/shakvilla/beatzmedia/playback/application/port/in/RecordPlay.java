package org.shakvilla.beatzmedia.playback.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.domain.PlaySource;

/**
 * Input port: append a {@code play_event} for plays count / royalties, de-duplicated per
 * (account, track) within an anti-inflation window (Playback ADD §9); a suppressed duplicate is a
 * silent no-op. Emits {@code PlayRecorded} (AFTER_SUCCESS) on a counted play.
 * LLFR-PLAYBACK-01.2. Playback ADD §4.1.
 *
 * <p>Trigger: {@code POST /v1/tracks/:id/play}. Auth: optional — always scoped to the JWT
 * subject when present; never accepts a client-supplied account id. Unknown track →
 * {@link org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException} (404 TRACK_NOT_FOUND).
 */
public interface RecordPlay {

  void recordPlay(TrackId track, Optional<AccountId> caller, PlaySource source);
}
