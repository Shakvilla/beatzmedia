package org.shakvilla.beatzmedia.playback.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.service.RecordPlayService;
import org.shakvilla.beatzmedia.playback.domain.PlayRecorded;
import org.shakvilla.beatzmedia.playback.domain.PlaySource;
import org.shakvilla.beatzmedia.playback.domain.TrackOwnership;
import org.shakvilla.beatzmedia.playback.fakes.FakeCatalogReader;
import org.shakvilla.beatzmedia.playback.fakes.FakePlayEventRepository;
import org.shakvilla.beatzmedia.playback.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link RecordPlayService} (LLFR-PLAYBACK-01.2): appends a play_event, de-dupes
 * repeated calls for the same (account, track) within the anti-inflation window (silent no-op),
 * scopes strictly to the caller passed in (never a client-supplied id), and emits
 * {@link PlayRecorded} only on a counted play. Playback ADD §9 / §11.
 */
@Tag("unit")
class RecordPlayServiceTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");
  private static final AccountId OTHER_ACCOUNT = new AccountId("acct-2");
  private static final TrackId TRACK = new TrackId("track-1");

  FakeCatalogReader catalog;
  FakePlayEventRepository repository;
  FakeIds ids;
  FakeClock clock;
  RecordingEvent<PlayRecorded> events;
  RecordPlayService service;

  @BeforeEach
  void setUp() {
    catalog = new FakeCatalogReader().seed(TRACK.value(), TrackOwnership.FREE);
    repository = new FakePlayEventRepository();
    ids = FakeIds.sequential("play");
    clock = FakeClock.fixed();
    events = new RecordingEvent<>();
    service = new RecordPlayService(catalog, repository, ids, clock, events, 30L);
  }

  @Test
  void recordPlay_countedPlay_insertsEvent_andFiresPlayRecorded() {
    service.recordPlay(TRACK, Optional.of(ACCOUNT), PlaySource.player);

    assertEquals(1, repository.size());
    assertEquals(1, events.count());
    PlayRecorded fired = events.fired().get(0);
    assertEquals(TRACK.value(), fired.trackId());
    assertEquals(ACCOUNT.value(), fired.accountId());
    assertEquals("full", fired.fullVsPreview());
    assertEquals("player", fired.source());
  }

  @Test
  void recordPlay_repeatedCallWithinWindow_isSilentNoOp_doesNotDoubleCount() {
    service.recordPlay(TRACK, Optional.of(ACCOUNT), PlaySource.player);
    clock.advanceSeconds(5); // still within the 30s de-dup window
    service.recordPlay(TRACK, Optional.of(ACCOUNT), PlaySource.player);

    assertEquals(1, repository.size(), "second call within the window must not double-count");
    assertEquals(1, events.count(), "PlayRecorded must only fire once per counted play");
  }

  @Test
  void recordPlay_repeatedCallAfterWindow_countsAgain() {
    service.recordPlay(TRACK, Optional.of(ACCOUNT), PlaySource.player);
    clock.advanceSeconds(31); // past the 30s de-dup window
    service.recordPlay(TRACK, Optional.of(ACCOUNT), PlaySource.player);

    assertEquals(2, repository.size(), "a call after the window elapses must count again");
    assertEquals(2, events.count());
  }

  @Test
  void recordPlay_differentAccounts_bothCount_noCrossAccountSuppression() {
    service.recordPlay(TRACK, Optional.of(ACCOUNT), PlaySource.player);
    service.recordPlay(TRACK, Optional.of(OTHER_ACCOUNT), PlaySource.player);

    assertEquals(2, repository.size(), "distinct accounts must never suppress each other's plays");
  }

  @Test
  void recordPlay_anonymousCaller_alwaysCounts_noAccountScopedDedup() {
    service.recordPlay(TRACK, Optional.empty(), PlaySource.player);
    service.recordPlay(TRACK, Optional.empty(), PlaySource.player);

    assertEquals(2, repository.size());
    assertTrue(repository.events().get(0).getAccountId().isEmpty());
  }

  @Test
  void recordPlay_unknownTrack_throwsTrackNotFound() {
    assertThrows(
        TrackNotFoundException.class,
        () ->
            service.recordPlay(
                new TrackId("does-not-exist"), Optional.of(ACCOUNT), PlaySource.player));
  }

  @Test
  void recordPlay_defaultsSourceToPlayer_whenNull() {
    service.recordPlay(TRACK, Optional.of(ACCOUNT), null);

    assertEquals("player", events.fired().get(0).source());
  }

  @Test
  void recordPlay_forSaleTrack_recordsPreviewMode() {
    catalog.seed("track-for-sale", TrackOwnership.FOR_SALE);
    service.recordPlay(new TrackId("track-for-sale"), Optional.of(ACCOUNT), PlaySource.preview);

    assertEquals("preview", events.fired().get(0).fullVsPreview());
  }
}
