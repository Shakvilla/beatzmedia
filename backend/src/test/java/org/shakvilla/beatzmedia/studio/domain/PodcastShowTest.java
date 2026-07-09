package org.shakvilla.beatzmedia.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PodcastShow}. LLFR-STUDIO-02.1. */
@Tag("unit")
class PodcastShowTest {

  private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

  @Test
  void create_validInput_succeeds() {
    PodcastShow show =
        PodcastShow.create(new ShowId("sh-1"), new ArtistId("artist-1"), "Konongo Diaries", "Storytelling", NOW);
    assertEquals("Konongo Diaries", show.title());
    assertEquals("Storytelling", show.category());
    assertEquals(NOW, show.createdAt());
  }

  @Test
  void create_blankTitle_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PodcastShow.create(new ShowId("sh-1"), new ArtistId("artist-1"), "  ", "Storytelling", NOW));
  }

  @Test
  void create_blankCategory_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PodcastShow.create(new ShowId("sh-1"), new ArtistId("artist-1"), "Title", " ", NOW));
  }
}
