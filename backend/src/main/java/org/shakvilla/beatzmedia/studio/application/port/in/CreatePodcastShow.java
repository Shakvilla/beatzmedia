package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/** Input port: {@code POST /studio/podcasts/shows} — LLFR-STUDIO-02.1. Studio ADD §4.1. */
public interface CreatePodcastShow {

  PodcastShowView create(ArtistId artist, CreatePodcastShowCommand cmd);

  /** {@code CreateShowDto {title,category}} — Studio ADD §5.1. */
  /**
   * {@code image} is the show's cover art. Optional at creation — a show is drafted before it is
   * ready to publish — but required before any of its episodes can reach fans, because the
   * fan-facing {@code podcast.image} column is NOT NULL (V974).
   */
  record CreatePodcastShowCommand(
      String title, String category, String image, String description) {}
}
