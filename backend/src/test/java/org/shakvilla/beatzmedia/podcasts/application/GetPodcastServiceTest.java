package org.shakvilla.beatzmedia.podcasts.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;
import org.shakvilla.beatzmedia.podcasts.application.service.GetPodcastService;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastNotFoundException;
import org.shakvilla.beatzmedia.podcasts.fakes.FakePodcastRepository;

/** Unit tests for {@link GetPodcastService} — show detail (LLFR-PODCAST-01.2). */
@Tag("unit")
class GetPodcastServiceTest {

  private static final Instant CREATED = Instant.parse("2026-06-01T00:00:00Z");

  FakePodcastRepository repository;
  GetPodcastService service;

  @BeforeEach
  void setUp() {
    repository =
        new FakePodcastRepository()
            .withShow(
                new Podcast(
                    new PodcastId("show-1"), "Show One", "Pub", "creator-1", "img.png",
                    PodcastCategory.CULTURE, "desc", 10, 90, null, true, CREATED));
    service = new GetPodcastService(repository);
  }

  @Test
  void knownShow_returnsView() {
    PodcastView view = service.get(new PodcastId("show-1"));
    assertEquals("show-1", view.id());
    assertEquals("Show One", view.title());
    assertEquals("Culture", view.category());
  }

  @Test
  void unknownShow_throwsPodcastNotFound() {
    assertThrows(
        PodcastNotFoundException.class, () -> service.get(new PodcastId("does-not-exist")));
  }
}
