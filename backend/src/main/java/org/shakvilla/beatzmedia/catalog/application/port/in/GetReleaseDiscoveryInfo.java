package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/** Narrow published-release snapshot used by discovery projections. */
public interface GetReleaseDiscoveryInfo {

  ReleaseDiscoveryInfo get(ReleaseId releaseId);

  record ReleaseDiscoveryInfo(
      String id,
      String artistId,
      String title,
      String artistName,
      String coverImage,
      String releaseType,
      String publishedAt,
      String originCountry) {
    public ReleaseDiscoveryInfo(
        String id, String artistId, String title, String artistName, String coverImage,
        String releaseType, String publishedAt) {
      this(id, artistId, title, artistName, coverImage, releaseType, publishedAt, null);
    }
  }
}
