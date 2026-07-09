package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.studio.application.port.in.EpisodeView;
import org.shakvilla.beatzmedia.studio.application.service.ListStudioEpisodesService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

/** Unit tests for {@link ListStudioEpisodesService} — LLFR-STUDIO-02.2. */
@Tag("unit")
class ListStudioEpisodesServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

  private FakeStudioRepository repo;
  private ListStudioEpisodesService service;

  @BeforeEach
  void setUp() {
    repo = new FakeStudioRepository();
    service = new ListStudioEpisodesService(repo);
  }

  @Test
  void list_decoratesEpisodesWithShowTitle() {
    repo.withShow(PodcastShow.create(new ShowId("sh-1"), ARTIST, "Konongo Diaries", "Storytelling", NOW));
    Episode e1 = Episode.createDraft(
        new EpisodeId("ep-1"), new ShowId("sh-1"), ARTIST, "Ep 1", "desc", "audio-key", null, 120,
        false, 0L, Currency.GHS, false, NOW, null, null);
    e1.publishNow(NOW);
    repo.withEpisode(e1);

    List<EpisodeView> views = service.list(ARTIST);

    assertEquals(1, views.size());
    assertEquals("Konongo Diaries", views.get(0).showTitle());
    assertEquals("published", views.get(0).status());
  }

  @Test
  void list_noEpisodes_returnsEmpty() {
    assertEquals(0, service.list(ARTIST).size());
  }
}
