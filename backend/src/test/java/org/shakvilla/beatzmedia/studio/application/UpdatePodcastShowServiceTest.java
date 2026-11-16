package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.studio.application.port.in.UpdatePodcastShow.UpdateShowCommand;
import org.shakvilla.beatzmedia.studio.application.service.UpdatePodcastShowService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

@Tag("unit")
class UpdatePodcastShowServiceTest {
  @Test
  void editsOwnedShowAndRefreshesPublishedEpisodes() {
    ArtistId owner = new ArtistId("artist-1");
    ShowId id = new ShowId("show-1");
    Instant now = Instant.parse("2026-11-09T00:00:00Z");
    FakeStudioRepository repository = new FakeStudioRepository().withShow(
        PodcastShow.create(id, owner, "Old", "Tech", "/cover", "Old description", now));
    Episode published = Episode.createDraft(new EpisodeId("ep-1"), id, owner, "Episode", "Desc",
        "audio", null, 120, false, 0L, Currency.GHS, false, now, null, null);
    published.publishNow(now);
    repository.withEpisode(published);
    List<String> projected = new ArrayList<>();
    var audit = new ArrayList<org.shakvilla.beatzmedia.audit.domain.AuditEntry>();
    var service = new UpdatePodcastShowService(repository,
        (artist, episode) -> projected.add(episode.id().value()), audit::add,
        FakeIds.sequential("audit"), FakeClock.at(now));

    var view = service.update(owner, id,
        new UpdateShowCommand("New", "Culture", "New description", " gh "));
    assertEquals("New", view.title());
    assertEquals("GH", view.originCountry());
    assertEquals("New description", repository.findShow(owner, id).orElseThrow().description());
    assertEquals(List.of("ep-1"), projected);
    assertEquals(1, audit.size());
    assertThrows(NotFoundException.class, () -> service.update(new ArtistId("other"), id,
        new UpdateShowCommand("Bad", "Tech", "", "")));
  }
}
