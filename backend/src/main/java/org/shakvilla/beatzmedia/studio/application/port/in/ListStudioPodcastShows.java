package org.shakvilla.beatzmedia.studio.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/** Input port: {@code GET /studio/podcasts/shows} — LLFR-STUDIO-02.1. Studio ADD §4.1. */
public interface ListStudioPodcastShows {

  List<PodcastShowView> list(ArtistId artist);
}
