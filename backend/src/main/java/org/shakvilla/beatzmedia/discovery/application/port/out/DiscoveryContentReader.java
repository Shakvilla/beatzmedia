package org.shakvilla.beatzmedia.discovery.application.port.out;

public interface DiscoveryContentReader {

  ContentSnapshot release(String releaseId);

  ContentSnapshot episode(String artistId, String episodeId);

  record ContentSnapshot(
      String contentId,
      String creatorId,
      String title,
      String subtitle,
      String image,
      String route) {}
}
