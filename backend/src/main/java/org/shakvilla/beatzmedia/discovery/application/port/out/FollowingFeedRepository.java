package org.shakvilla.beatzmedia.discovery.application.port.out;

import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.discovery.domain.FeedKind;
import org.shakvilla.beatzmedia.discovery.domain.FollowingFeedItem;

public interface FollowingFeedRepository {

  void upsert(FollowingFeedItem item);

  List<FollowingFeedItem> find(
      List<String> artistIds,
      List<String> showIds,
      Instant beforePublishedAt,
      FeedKind afterKind,
      String afterContentId,
      int limit);
}
