package org.shakvilla.beatzmedia.playback.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.in.RecordPlay;
import org.shakvilla.beatzmedia.playback.application.port.out.CatalogReader;
import org.shakvilla.beatzmedia.playback.application.port.out.PlayEventRepository;
import org.shakvilla.beatzmedia.playback.domain.PlayEvent;
import org.shakvilla.beatzmedia.playback.domain.PlayRecorded;
import org.shakvilla.beatzmedia.playback.domain.PlaySource;
import org.shakvilla.beatzmedia.playback.domain.PlaybackMode;
import org.shakvilla.beatzmedia.playback.domain.TrackOwnership;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for {@link RecordPlay} (LLFR-PLAYBACK-01.2). Appends a {@code play_event},
 * de-duplicated per (account, track) within an anti-inflation window (Playback ADD §9); a
 * suppressed duplicate is a silent no-op (still 204 at the REST boundary). Anonymous plays are
 * never de-duped server-side here (no stable identity) — the gateway/client fingerprint carries
 * that responsibility per the ADD. Always scoped to the JWT-derived caller — never a
 * client-supplied account id (this port only accepts an {@link AccountId} the REST adapter has
 * itself derived from the token).
 */
@ApplicationScoped
public class RecordPlayService implements RecordPlay {

  private final CatalogReader catalogReader;
  private final PlayEventRepository repository;
  private final IdGenerator ids;
  private final Clock clock;
  private final Event<PlayRecorded> playRecordedEvent;
  private final Duration dedupWindow;

  @Inject
  public RecordPlayService(
      CatalogReader catalogReader,
      PlayEventRepository repository,
      IdGenerator ids,
      Clock clock,
      Event<PlayRecorded> playRecordedEvent,
      @ConfigProperty(name = "beatz.playback.play-dedup-window-seconds", defaultValue = "30")
          long dedupWindowSeconds) {
    this.catalogReader = catalogReader;
    this.repository = repository;
    this.ids = ids;
    this.clock = clock;
    this.playRecordedEvent = playRecordedEvent;
    this.dedupWindow = Duration.ofSeconds(dedupWindowSeconds);
  }

  @Override
  @Transactional
  public void recordPlay(TrackId track, Optional<AccountId> caller, PlaySource source) {
    CatalogReader.TrackPlaybackInfo info =
        catalogReader
            .getTrack(track)
            .orElseThrow(() -> new TrackNotFoundException(track.value()));

    Instant now = clock.now();

    if (caller.isPresent() && isDuplicate(caller.get(), track, now)) {
      return; // silent no-op — still 204 at the REST boundary (§9)
    }

    // Plays are only ever counted against the FULL rendition once owned/free; a for-sale track not
    // yet owned records as PREVIEW (mirrors the stream decision without re-querying ownership here
    // — the caller only reaches /play after fetching /stream, which already made that call).
    PlaybackMode fullVsPreview =
        info.ownership() == TrackOwnership.FOR_SALE ? PlaybackMode.PREVIEW : PlaybackMode.FULL;

    PlayEvent event = PlayEvent.record(ids.newId(), caller, track, now, fullVsPreview, source);
    repository.insert(event);

    playRecordedEvent.fire(
        new PlayRecorded(
            track.value(),
            caller.map(AccountId::value).orElse(null),
            now,
            fullVsPreview.name().toLowerCase(),
            (source != null ? source : PlaySource.player).name()));
  }

  private boolean isDuplicate(AccountId account, TrackId track, Instant now) {
    return repository
        .lastPlayAt(account, track)
        .map(last -> last.plus(dedupWindow).isAfter(now))
        .orElse(false);
  }
}
