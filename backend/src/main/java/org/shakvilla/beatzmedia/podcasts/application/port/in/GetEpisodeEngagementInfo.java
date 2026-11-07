package org.shakvilla.beatzmedia.podcasts.application.port.in;

import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;

/** Narrow public read port used by the engagement module; exposes no podcast persistence type. */
public interface GetEpisodeEngagementInfo {

  EpisodeEngagementInfo get(EpisodeId episodeId);

  record EpisodeEngagementInfo(
      String id,
      String showId,
      String title,
      String showTitle,
      String image,
      int durationSeconds) {}
}
