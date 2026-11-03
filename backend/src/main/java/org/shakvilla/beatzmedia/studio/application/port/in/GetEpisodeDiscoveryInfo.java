package org.shakvilla.beatzmedia.studio.application.port.in;

/** Narrow published-episode snapshot used by discovery projections. */
public interface GetEpisodeDiscoveryInfo {

  EpisodeDiscoveryInfo get(String artistId, String episodeId);

  record EpisodeDiscoveryInfo(
      String id,
      String showId,
      String title,
      String showTitle,
      String image,
      String publishedAt) {}
}
