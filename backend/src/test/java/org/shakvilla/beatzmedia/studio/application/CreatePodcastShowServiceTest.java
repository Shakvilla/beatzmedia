package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.studio.application.port.in.CreatePodcastShow.CreatePodcastShowCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.PodcastShowView;
import org.shakvilla.beatzmedia.studio.application.service.CreatePodcastShowService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

/** Unit tests for {@link CreatePodcastShowService} — LLFR-STUDIO-02.1. */
@Tag("unit")
class CreatePodcastShowServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");

  private CreatePodcastShowService service;
  private FakeStudioRepository repo;

  @Test
  void create_validInput_returnsView() {
    setUp();
    PodcastShowView view = service.create(ARTIST, new CreatePodcastShowCommand("My Show", "Comedy"));
    assertEquals("My Show", view.title());
    assertEquals("Comedy", view.category());
    assertEquals(1, repo.findShows(ARTIST).size());
  }

  @Test
  void create_blankTitle_throwsValidation() {
    setUp();
    assertThrows(
        ValidationException.class,
        () -> service.create(ARTIST, new CreatePodcastShowCommand("", "Comedy")));
  }

  @Test
  void create_blankCategory_throwsValidation() {
    setUp();
    assertThrows(
        ValidationException.class,
        () -> service.create(ARTIST, new CreatePodcastShowCommand("My Show", "")));
  }

  private void setUp() {
    repo = new FakeStudioRepository();
    service = new CreatePodcastShowService(repo, FakeIds.sequential("sh"), FakeClock.fixed());
  }
}
