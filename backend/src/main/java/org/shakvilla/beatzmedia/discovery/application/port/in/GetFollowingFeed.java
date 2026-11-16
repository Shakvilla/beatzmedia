package org.shakvilla.beatzmedia.discovery.application.port.in;

import java.time.Instant;
import java.util.List;

public interface GetFollowingFeed {

  FollowingPage get(String accountId, String cursor, int limit);

  record FollowingItem(
      String kind,
      String contentId,
      String creatorId,
      String title,
      String subtitle,
      String image,
      String route,
      Instant publishedAt) {}

  record FollowingPage(List<FollowingItem> items, String nextCursor) {}
}
