package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.studio.application.service.ListAdminPodcastEpisodesService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

class ListAdminPodcastEpisodesServiceTest {
  @Test
  void lists_processing_and_ready_episodes_across_creators() {
    var repository = new FakeStudioRepository();
    var now = Instant.parse("2026-11-09T10:00:00Z");
    var firstArtist = new ArtistId("artist-1");
    var secondArtist = new ArtistId("artist-2");
    repository.withShow(PodcastShow.create(new ShowId("show-1"), firstArtist,
        "Architecture", "Tech", "cover-1", null, now));
    repository.withShow(PodcastShow.create(new ShowId("show-2"), secondArtist,
        "Culture", "Culture", "cover-2", null, now));

    Episode processing = episode("episode-1", "show-1", firstArtist, now);
    processing.publishNow(now);
    repository.withEpisode(processing);
    Episode ready = episode("episode-2", "show-2", secondArtist, now.plusSeconds(1));
    ready.publishNow(now.plusSeconds(1));
    ready.markMediaReady(180);
    repository.withEpisode(ready);

    var result = new ListAdminPodcastEpisodesService(repository).list(new PageRequest(1, 10));

    assertEquals(2, result.total());
    assertEquals("episode-2", result.items().get(0).id());
    assertEquals("published", result.items().get(0).status());
    assertEquals("Culture", result.items().get(0).showTitle());
    assertEquals("processing", result.items().get(1).status());
    assertEquals("Architecture", result.items().get(1).showTitle());
  }

  private static Episode episode(String id, String showId, ArtistId artist, Instant createdAt) {
    return Episode.createDraft(new EpisodeId(id), new ShowId(showId), artist,
        "Episode " + id, null, "asset-" + id, null, 0, false, 0, Currency.GHS, false,
        createdAt, "key-" + id, "hash-" + id);
  }
}
