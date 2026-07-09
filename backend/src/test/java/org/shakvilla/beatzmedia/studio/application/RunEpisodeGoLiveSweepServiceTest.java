package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.studio.application.service.RunEpisodeGoLiveSweepService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;
import org.shakvilla.beatzmedia.studio.fakes.RecordingEvent;

/**
 * Unit tests for {@link RunEpisodeGoLiveSweepService} — INV-7 scheduled go-live. Mirrors {@code
 * catalog.RunGoLiveSweepServiceTest}.
 */
@Tag("unit")
class RunEpisodeGoLiveSweepServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final Instant CREATED = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

  private FakeStudioRepository repo;
  private RecordingEvent<EpisodePublished> publishedEvent;
  private RunEpisodeGoLiveSweepService service;

  @BeforeEach
  void setUp() {
    repo = new FakeStudioRepository();
    publishedEvent = new RecordingEvent<>();
    service = new RunEpisodeGoLiveSweepService(repo, FakeClock.at(NOW), publishedEvent);
  }

  private Episode scheduledEpisode(String id, Instant scheduledAt) {
    Episode e = Episode.createDraft(
        new EpisodeId(id), new ShowId("sh-1"), ARTIST, "Title", "desc", "audio-key", null, 120,
        false, 0L, Currency.GHS, false, CREATED, null, null);
    e.scheduleAt(scheduledAt);
    repo.withEpisode(e);
    return e;
  }

  @Test
  void run_dueScheduledEpisode_transitionsToPublishedAndFiresEventOnce() {
    scheduledEpisode("ep-1", NOW.minusSeconds(60));

    int count = service.run();

    assertEquals(1, count);
    assertEquals(EpisodeStatus.published, repo.findEpisode(ARTIST, new EpisodeId("ep-1")).orElseThrow().status());
    assertEquals(1, publishedEvent.fired().size());
  }

  @Test
  void run_notYetDueEpisode_isSkipped() {
    scheduledEpisode("ep-1", NOW.plusSeconds(3600));

    int count = service.run();

    assertEquals(0, count);
    assertEquals(EpisodeStatus.scheduled, repo.findEpisode(ARTIST, new EpisodeId("ep-1")).orElseThrow().status());
    assertEquals(0, publishedEvent.fired().size());
  }

  @Test
  void run_reRunningSweep_isExactlyOnce_noOpOnSecondRun() {
    scheduledEpisode("ep-1", NOW.minusSeconds(60));

    service.run();
    int secondRunCount = service.run();

    assertEquals(0, secondRunCount, "already-published episode is no longer due; re-run is a no-op");
    assertEquals(1, publishedEvent.fired().size(), "EpisodePublished fired exactly once across both runs");
  }

  @Test
  void run_draftEpisode_isNeverPicked() {
    Episode draft = Episode.createDraft(
        new EpisodeId("ep-draft"), new ShowId("sh-1"), ARTIST, "Title", "desc", "audio-key", null, 120,
        false, 0L, Currency.GHS, false, CREATED, null, null);
    repo.withEpisode(draft);

    int count = service.run();

    assertEquals(0, count);
  }
}
