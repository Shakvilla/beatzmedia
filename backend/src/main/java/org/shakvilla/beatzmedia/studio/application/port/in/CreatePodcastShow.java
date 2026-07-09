package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/** Input port: {@code POST /studio/podcasts/shows} — LLFR-STUDIO-02.1. Studio ADD §4.1. */
public interface CreatePodcastShow {

  PodcastShowView create(ArtistId artist, CreatePodcastShowCommand cmd);

  /** {@code CreateShowDto {title,category}} — Studio ADD §5.1. */
  record CreatePodcastShowCommand(String title, String category) {}
}
