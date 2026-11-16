package org.shakvilla.beatzmedia.studio.application.port.in;

/** Narrow published-episode snapshot used by discovery projections. */
public interface GetEpisodeDiscoveryInfo {

  EpisodeDiscoveryInfo get(String artistId, String episodeId);

  /** Resolve a currently published episode without needing a caller-supplied artist id. */
  EpisodeDiscoveryInfo getPublished(String episodeId);

  record EpisodeDiscoveryInfo(
      String id,
      String showId,
      String title,
      String showTitle,
      String image,
      String publishedAt,
      String originCountry) {
    public EpisodeDiscoveryInfo(
        String id, String showId, String title, String showTitle, String image,
        String publishedAt) {
      this(id, showId, title, showTitle, image, publishedAt, null);
    }
  }
}
