package org.shakvilla.beatzmedia.studio.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/** Input port: {@code GET /studio/podcasts/episodes} — LLFR-STUDIO-02.2. Studio ADD §4.1. */
public interface ListStudioEpisodes {

  List<EpisodeView> list(ArtistId artist);
}
