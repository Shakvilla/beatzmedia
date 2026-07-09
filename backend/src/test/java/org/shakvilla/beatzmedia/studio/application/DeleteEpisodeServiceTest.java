package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.studio.application.service.DeleteEpisodeService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeHasOwnersException;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeNotFoundException;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

/** Unit tests for {@link DeleteEpisodeService} — LLFR-STUDIO-02.4 / OQ-8 delete guard. */
@Tag("unit")
class DeleteEpisodeServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final ArtistId OTHER_ARTIST = new ArtistId("artist-2");
  private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

  private FakeStudioRepository repo;
  private FakeOwnershipReader ownershipReader;
  private DeleteEpisodeService service;

  @BeforeEach
  void setUp() {
    repo = new FakeStudioRepository();
    ownershipReader = new FakeOwnershipReader();
    service = new DeleteEpisodeService(
        repo, ownershipReader, FakeIds.sequential("aud"), FakeClock.at(NOW), new FakeAuditWriter());
  }

  private Episode episodeWithStatus(String id, boolean publish) {
    Episode e = Episode.createDraft(
        new EpisodeId(id), new ShowId("sh-1"), ARTIST, "Title", "desc", "audio-key", null, 120,
        false, 0L, Currency.GHS, false, NOW, null, null);
    if (publish) {
      e.publishNow(NOW);
    }
    repo.withEpisode(e);
    return e;
  }

  @Test
  void delete_draftEpisode_deletesFreely() {
    episodeWithStatus("ep-1", false);
    service.delete(ARTIST, new EpisodeId("ep-1"));
    assertTrue(repo.findEpisode(ARTIST, new EpisodeId("ep-1")).isEmpty());
  }

  @Test
  void delete_publishedEpisodeWithNoOwner_deletesFreely() {
    episodeWithStatus("ep-1", true);
    service.delete(ARTIST, new EpisodeId("ep-1"));
    assertTrue(repo.findEpisode(ARTIST, new EpisodeId("ep-1")).isEmpty());
  }

  @Test
  void delete_publishedEpisodeWithOwner_throws409EpisodePublished_OQ8() {
    episodeWithStatus("ep-1", true);
    ownershipReader.withOwner("ep-1");
    assertThrows(EpisodeHasOwnersException.class, () -> service.delete(ARTIST, new EpisodeId("ep-1")));
    assertTrue(repo.findEpisode(ARTIST, new EpisodeId("ep-1")).isPresent(), "episode must not be deleted");
  }

  @Test
  void delete_nonexistentEpisode_throwsEpisodeNotFound() {
    assertThrows(EpisodeNotFoundException.class, () -> service.delete(ARTIST, new EpisodeId("no-such-ep")));
  }

  @Test
  void delete_someoneElsesEpisode_throwsEpisodeNotFound() {
    episodeWithStatus("ep-1", false);
    assertThrows(EpisodeNotFoundException.class, () -> service.delete(OTHER_ARTIST, new EpisodeId("ep-1")));
  }
}
