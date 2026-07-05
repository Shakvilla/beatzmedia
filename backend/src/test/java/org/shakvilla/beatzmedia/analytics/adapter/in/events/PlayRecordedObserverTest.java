package org.shakvilla.beatzmedia.analytics.adapter.in.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.analytics.domain.PlayFact;
import org.shakvilla.beatzmedia.analytics.fakes.FakeArtistResolver;
import org.shakvilla.beatzmedia.analytics.fakes.InMemoryPlayFactRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.playback.domain.PlayRecorded;

/**
 * Unit tests for {@link PlayRecordedObserver} — proves the artist id is resolved via
 * {@link org.shakvilla.beatzmedia.analytics.application.port.out.ArtistResolver} and an
 * unresolvable track is skipped rather than staged with a fabricated artist id.
 */
@Tag("unit")
class PlayRecordedObserverTest {

  InMemoryPlayFactRepository playFacts;
  FakeArtistResolver artistResolver;
  PlayRecordedObserver observer;

  @BeforeEach
  void setUp() {
    playFacts = new InMemoryPlayFactRepository();
    artistResolver = new FakeArtistResolver();
    observer = new PlayRecordedObserver(playFacts, artistResolver, FakeIds.sequential("fact"));
  }

  @Test
  void onPlayRecorded_resolvableTrack_stagesFactWithResolvedArtist() {
    artistResolver.seed("track-1", "artist-9");
    Instant at = Instant.parse("2026-07-05T10:00:00Z");

    observer.onPlayRecorded(new PlayRecorded("track-1", "acc-1", at, "full", "player"));

    assertEquals(1, playFacts.findUnprocessed().size());
    PlayFact fact = playFacts.findUnprocessed().get(0);
    assertEquals("artist-9", fact.artistId());
    assertEquals("acc-1", fact.accountId());
    assertEquals(at, fact.occurredAt());
  }

  @Test
  void onPlayRecorded_unresolvableTrack_isSkipped_noFactStaged() {
    // No seed for "track-unknown" -> resolver returns empty.
    observer.onPlayRecorded(
        new PlayRecorded("track-unknown", "acc-1", Instant.parse("2026-07-05T10:00:00Z"), "full", "player"));

    assertTrue(playFacts.findUnprocessed().isEmpty(), "unresolvable track must not stage a fact");
  }

  @Test
  void onPlayRecorded_anonymousPlay_stagesFactWithNullAccountId() {
    artistResolver.seed("track-2", "artist-5");
    observer.onPlayRecorded(
        new PlayRecorded("track-2", null, Instant.parse("2026-07-05T10:00:00Z"), "full", "player"));

    PlayFact fact = playFacts.findUnprocessed().get(0);
    assertEquals(null, fact.accountId());
  }
}
