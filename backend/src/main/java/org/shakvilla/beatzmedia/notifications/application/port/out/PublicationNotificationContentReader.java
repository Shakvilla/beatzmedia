package org.shakvilla.beatzmedia.notifications.application.port.out;

/** Narrow publication snapshots used to create durable fanout jobs. */
public interface PublicationNotificationContentReader {

  ContentSnapshot release(String releaseId);

  ContentSnapshot episode(String artistId, String episodeId);

  record ContentSnapshot(
      String contentId,
      String creatorId,
      String creatorName,
      String title,
      String route) {}
}
