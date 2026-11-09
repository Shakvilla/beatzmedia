package org.shakvilla.beatzmedia.studio.application.port.in;

import java.time.Instant;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** Read-only admin inventory of all Studio podcast episodes, including processing uploads. */
public interface ListAdminPodcastEpisodes {
  Page<AdminEpisodeView> list(PageRequest page);

  record AdminEpisodeView(
      String id, String showId, String showTitle, String title, String artistId,
      String status, int durationSec, Instant createdAt) {}
}
