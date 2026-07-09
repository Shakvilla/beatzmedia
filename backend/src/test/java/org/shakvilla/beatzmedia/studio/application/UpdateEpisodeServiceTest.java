package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.studio.application.port.in.EpisodeView;
import org.shakvilla.beatzmedia.studio.application.port.in.UpdateEpisode.UpdateEpisodeCommand;
import org.shakvilla.beatzmedia.studio.application.service.UpdateEpisodeService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeNotFoundException;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;
import org.shakvilla.beatzmedia.studio.domain.IllegalEpisodeTransitionException;
import org.shakvilla.beatzmedia.studio.domain.InvalidPriceException;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ScheduleDateRequiredException;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;
import org.shakvilla.beatzmedia.studio.fakes.RecordingEvent;

/** Unit tests for {@link UpdateEpisodeService} — LLFR-STUDIO-02.4. */
@Tag("unit")
class UpdateEpisodeServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final ArtistId OTHER_ARTIST = new ArtistId("artist-2");
  private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant FUTURE = Instant.parse("2026-07-01T00:00:00Z");

  private FakeStudioRepository repo;
  private FakeClock clock;
  private FakeAuditWriter auditWriter;
  private RecordingEvent<EpisodePublished> publishedEvent;
  private UpdateEpisodeService service;

  @BeforeEach
  void setUp() {
    repo = new FakeStudioRepository();
    repo.withShow(PodcastShow.create(new ShowId("sh-1"), ARTIST, "Show", "Comedy", NOW));
    clock = FakeClock.at(NOW);
    auditWriter = new FakeAuditWriter();
    publishedEvent = new RecordingEvent<>();
    service = new UpdateEpisodeService(repo, FakeIds.sequential("aud"), clock, auditWriter, publishedEvent);
  }

  private Episode draftEpisode(String id) {
    Episode e = Episode.createDraft(
        new EpisodeId(id), new ShowId("sh-1"), ARTIST, "Title", "desc", "audio-key", null, 120,
        false, 0L, Currency.GHS, false, NOW, null, null);
    repo.withEpisode(e);
    return e;
  }

  @Test
  void update_titleAndDescription_appliesPatch() {
    draftEpisode("ep-1");
    EpisodeView view = service.update(
        ARTIST, new EpisodeId("ep-1"),
        new UpdateEpisodeCommand("New Title", "New desc", null, null, null, null, null));
    assertEquals("New Title", view.title());
  }

  @Test
  void update_premiumWithoutPrice_throwsInvalidPrice() {
    draftEpisode("ep-1");
    assertThrows(
        InvalidPriceException.class,
        () -> service.update(
            ARTIST, new EpisodeId("ep-1"),
            new UpdateEpisodeCommand(null, null, true, null, null, null, null)));
  }

  @Test
  void update_premiumWithPrice_succeeds() {
    draftEpisode("ep-1");
    EpisodeView view = service.update(
        ARTIST, new EpisodeId("ep-1"),
        new UpdateEpisodeCommand(null, null, true, new BigDecimal("5"), null, null, null));
    assertTrue(view.premium());
    assertEquals(new BigDecimal("5.00"), view.price());
  }

  @Test
  void update_visibilityPublicFromDraft_publishesAndFiresEventOnce() {
    draftEpisode("ep-1");
    EpisodeView view = service.update(
        ARTIST, new EpisodeId("ep-1"),
        new UpdateEpisodeCommand(null, null, null, null, "public", null, null));
    assertEquals("published", view.status());
    assertEquals(1, publishedEvent.fired().size());
  }

  @Test
  void update_visibilityScheduledFromDraft_schedulesNoEvent() {
    draftEpisode("ep-1");
    EpisodeView view = service.update(
        ARTIST, new EpisodeId("ep-1"),
        new UpdateEpisodeCommand(null, null, null, null, "scheduled", FUTURE, null));
    assertEquals("scheduled", view.status());
    assertEquals(0, publishedEvent.fired().size());
  }

  @Test
  void update_visibilityScheduledWithoutDate_throwsScheduleDateRequired() {
    draftEpisode("ep-1");
    assertThrows(
        ScheduleDateRequiredException.class,
        () -> service.update(
            ARTIST, new EpisodeId("ep-1"),
            new UpdateEpisodeCommand(null, null, null, null, "scheduled", null, null)));
  }

  @Test
  void update_visibilityPublicOnScheduledEpisode_throwsIllegalTransition_INV7() {
    Episode e = draftEpisode("ep-1");
    e.scheduleAt(FUTURE);
    assertThrows(
        IllegalEpisodeTransitionException.class,
        () -> service.update(
            ARTIST, new EpisodeId("ep-1"),
            new UpdateEpisodeCommand(null, null, null, null, "public", null, null)));
  }

  @Test
  void update_visibilityDraftOnScheduledEpisode_unschedules() {
    Episode e = draftEpisode("ep-1");
    e.scheduleAt(FUTURE);
    EpisodeView view = service.update(
        ARTIST, new EpisodeId("ep-1"),
        new UpdateEpisodeCommand(null, null, null, null, "draft", null, null));
    assertEquals("draft", view.status());
  }

  @Test
  void update_reschedule_bareDateOnScheduledEpisode_updatesScheduledAt() {
    Episode e = draftEpisode("ep-1");
    e.scheduleAt(FUTURE);
    Instant later = FUTURE.plusSeconds(3600);
    EpisodeView view = service.update(
        ARTIST, new EpisodeId("ep-1"), new UpdateEpisodeCommand(null, null, null, null, null, later, null));
    assertEquals("scheduled", view.status());
  }

  @Test
  void update_nonexistentEpisode_throwsEpisodeNotFound() {
    assertThrows(
        EpisodeNotFoundException.class,
        () -> service.update(
            ARTIST, new EpisodeId("no-such-ep"),
            new UpdateEpisodeCommand("x", null, null, null, null, null, null)));
  }

  @Test
  void update_someoneElsesEpisode_throwsEpisodeNotFound_notLeaked() {
    draftEpisode("ep-1");
    // findEpisode is artist-scoped: a foreign episode resolves as if it doesn't exist (404, not 403).
    assertThrows(
        EpisodeNotFoundException.class,
        () -> service.update(
            OTHER_ARTIST, new EpisodeId("ep-1"),
            new UpdateEpisodeCommand("x", null, null, null, null, null, null)));
  }
}
