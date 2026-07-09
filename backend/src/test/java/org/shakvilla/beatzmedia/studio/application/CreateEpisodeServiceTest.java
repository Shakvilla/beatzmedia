package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.studio.application.port.in.CreateEpisode.AudioUpload;
import org.shakvilla.beatzmedia.studio.application.port.in.CreateEpisode.CreateEpisodeCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.EpisodeView;
import org.shakvilla.beatzmedia.studio.application.service.CreateEpisodeService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;
import org.shakvilla.beatzmedia.studio.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.studio.domain.InvalidPriceException;
import org.shakvilla.beatzmedia.studio.domain.MediaInvalidException;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ScheduleDateRequiredException;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.domain.ShowNotFoundException;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;
import org.shakvilla.beatzmedia.studio.fakes.FakeUploadOriginalUseCase;
import org.shakvilla.beatzmedia.studio.fakes.RecordingEvent;

/**
 * Unit tests for {@link CreateEpisodeService} — LLFR-STUDIO-02.3. Fakes for {@link
 * org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository}, {@link
 * org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase} and a fixed {@link
 * org.shakvilla.beatzmedia.platform.application.port.out.Clock}.
 */
@Tag("unit")
class CreateEpisodeServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final String NOW = "2026-06-01T00:00:00Z";
  private static final String FUTURE = "2026-07-01T00:00:00Z";
  private static final String PAST = "2026-01-01T00:00:00Z";

  private FakeStudioRepository repo;
  private FakeUploadOriginalUseCase mediaUpload;
  private FakeClock clock;
  private FakeAuditWriter auditWriter;
  private RecordingEvent<EpisodePublished> publishedEvent;
  private CreateEpisodeService service;

  @BeforeEach
  void setUp() {
    repo = new FakeStudioRepository();
    repo.withShow(PodcastShow.create(new ShowId("sh-1"), ARTIST, "Konongo Diaries", "Storytelling",
        java.time.Instant.parse(NOW)));
    mediaUpload = new FakeUploadOriginalUseCase();
    clock = FakeClock.at(NOW);
    auditWriter = new FakeAuditWriter();
    publishedEvent = new RecordingEvent<>();
    service = new CreateEpisodeService(
        repo, mediaUpload, FakeIds.sequential("ep"), clock, auditWriter, publishedEvent);
  }

  private static AudioUpload wavUpload() {
    return new AudioUpload("ep.wav", "audio/wav", 1024, new ByteArrayInputStream(new byte[]{1, 2, 3}), "hash-1");
  }

  private static CreateEpisodeCommand publicCommand() {
    return new CreateEpisodeCommand(
        "sh-1", null, null, "Ep 1", "desc", null, "public", null, false, null, false);
  }

  // ---- Happy path: publish now ----

  @Test
  void create_publicVisibility_publishesNowAndFiresEventExactlyOnce() {
    EpisodeView view = service.create(ARTIST, "idem-1", publicCommand(), wavUpload());

    assertEquals("published", view.status());
    assertEquals("sh-1", view.showId());
    assertEquals("Konongo Diaries", view.showTitle());
    assertEquals(1, mediaUpload.callCount());
    assertEquals(1, publishedEvent.fired().size());
    assertEquals(1, auditWriter.size());
  }

  // ---- Happy path: schedule ----

  @Test
  void create_scheduledVisibilityWithFutureDate_schedulesAndDoesNotFireEvent() {
    CreateEpisodeCommand cmd = new CreateEpisodeCommand(
        "sh-1", null, null, "Ep 2", "desc", null, "scheduled",
        java.time.Instant.parse(FUTURE), true, new BigDecimal("5"), true);

    EpisodeView view = service.create(ARTIST, "idem-2", cmd, wavUpload());

    assertEquals("scheduled", view.status());
    assertTrue(view.premium());
    assertEquals(0, publishedEvent.fired().size());
  }

  // ---- Premium ⇒ price > 0 ----

  @Test
  void create_premiumWithoutPrice_throwsInvalidPrice() {
    CreateEpisodeCommand cmd = new CreateEpisodeCommand(
        "sh-1", null, null, "Ep 3", "desc", null, "public", null, true, null, false);
    assertThrows(InvalidPriceException.class, () -> service.create(ARTIST, "idem-3", cmd, wavUpload()));
    assertEquals(0, mediaUpload.callCount(), "media must not be touched when validation fails first");
  }

  @Test
  void create_premiumWithZeroPrice_throwsInvalidPrice() {
    CreateEpisodeCommand cmd = new CreateEpisodeCommand(
        "sh-1", null, null, "Ep 3", "desc", null, "public", null, true, BigDecimal.ZERO, false);
    assertThrows(InvalidPriceException.class, () -> service.create(ARTIST, "idem-3b", cmd, wavUpload()));
  }

  // ---- Scheduled requires a future date ----

  @Test
  void create_scheduledWithoutDate_throwsScheduleDateRequired() {
    CreateEpisodeCommand cmd = new CreateEpisodeCommand(
        "sh-1", null, null, "Ep 4", "desc", null, "scheduled", null, false, null, false);
    assertThrows(ScheduleDateRequiredException.class, () -> service.create(ARTIST, "idem-4", cmd, wavUpload()));
  }

  @Test
  void create_scheduledWithPastDate_throwsScheduleDateRequired() {
    CreateEpisodeCommand cmd = new CreateEpisodeCommand(
        "sh-1", null, null, "Ep 4", "desc", null, "scheduled", java.time.Instant.parse(PAST), false, null, false);
    assertThrows(ScheduleDateRequiredException.class, () -> service.create(ARTIST, "idem-4b", cmd, wavUpload()));
  }

  // ---- Show resolution ----

  @Test
  void create_unknownShowId_throwsShowNotFound() {
    CreateEpisodeCommand cmd = new CreateEpisodeCommand(
        "no-such-show", null, null, "Ep 5", "desc", null, "public", null, false, null, false);
    assertThrows(ShowNotFoundException.class, () -> service.create(ARTIST, "idem-5", cmd, wavUpload()));
  }

  @Test
  void create_newShow_createsShowAndEpisode() {
    CreateEpisodeCommand cmd = new CreateEpisodeCommand(
        null, "Brand New Show", "Comedy", "Ep 6", "desc", null, "public", null, false, null, false);
    EpisodeView view = service.create(ARTIST, "idem-6", cmd, wavUpload());
    assertEquals("Brand New Show", view.showTitle());
    // setUp() already seeds 'sh-1' — the new-show path must add a second show, not replace it.
    assertEquals(2, repo.findShows(ARTIST).size());
  }

  // ---- Media validation (before touching the media pipeline for deeper checks) ----

  @Test
  void create_unsupportedAudioContentType_throwsMediaInvalid() {
    AudioUpload bad = new AudioUpload("ep.mp3", "audio/mpeg", 1024, new ByteArrayInputStream(new byte[]{1}), "h");
    assertThrows(MediaInvalidException.class, () -> service.create(ARTIST, "idem-7", publicCommand(), bad));
    assertEquals(0, mediaUpload.callCount());
  }

  @Test
  void create_missingAudio_throwsMediaInvalid() {
    assertThrows(MediaInvalidException.class, () -> service.create(ARTIST, "idem-8", publicCommand(), null));
  }

  // ---- Idempotency ----

  @Test
  void create_replayedSameKeyAndBody_returnsSameEpisodeNoSecondUpload() {
    EpisodeView first = service.create(ARTIST, "idem-9", publicCommand(), wavUpload());
    assertEquals(1, mediaUpload.callCount());

    EpisodeView replay = service.create(ARTIST, "idem-9", publicCommand(), wavUpload());

    assertEquals(first.id(), replay.id());
    assertEquals(1, mediaUpload.callCount(), "replay must not call the media pipeline a second time");
    assertEquals(1, publishedEvent.fired().size(), "replay must not re-fire EpisodePublished");
  }

  @Test
  void create_replayedSameKeyDifferentBody_throwsIdempotencyConflict() {
    service.create(ARTIST, "idem-10", publicCommand(), wavUpload());

    CreateEpisodeCommand different = new CreateEpisodeCommand(
        "sh-1", null, null, "A Totally Different Title", "desc", null, "public", null, false, null, false);

    assertThrows(
        IdempotencyConflictException.class,
        () -> service.create(ARTIST, "idem-10", different, wavUpload()));
  }

  @Test
  void create_missingIdempotencyKey_throwsValidation() {
    assertThrows(ValidationException.class, () -> service.create(ARTIST, "", publicCommand(), wavUpload()));
    assertThrows(ValidationException.class, () -> service.create(ARTIST, null, publicCommand(), wavUpload()));
  }

  @Test
  void create_uploadFails_noEpisodeOrShowLeftOrphaned() {
    mediaUpload.rejectNextUpload();
    assertThrows(
        org.shakvilla.beatzmedia.media.domain.UnsupportedFormatException.class,
        () -> service.create(ARTIST, "idem-11", publicCommand(), wavUpload()));
    assertTrue(repo.findEpisodes(ARTIST).isEmpty(), "a failed upload must leave no orphaned episode");
  }
}
