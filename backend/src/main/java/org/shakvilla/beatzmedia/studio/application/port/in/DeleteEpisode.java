package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;

/**
 * Input port: {@code DELETE /studio/podcasts/episodes/:id} — LLFR-STUDIO-02.4. A {@code published}
 * episode with any owner cannot be deleted (409 {@code EPISODE_PUBLISHED}, OQ-8). Studio ADD §4.1.
 */
public interface DeleteEpisode {

  void delete(ArtistId artist, EpisodeId id);
}
