package org.shakvilla.beatzmedia.discovery.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.discovery.application.port.in.GetFollowingFeed;
import org.shakvilla.beatzmedia.discovery.application.port.out.FollowingFeedRepository;
import org.shakvilla.beatzmedia.discovery.application.port.out.FollowingRelationships;
import org.shakvilla.beatzmedia.discovery.domain.FollowingFeedItem;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class FollowingFeedService implements GetFollowingFeed {

  private final FollowingRelationships relationships;
  private final FollowingFeedRepository feed;

  @Inject
  public FollowingFeedService(FollowingRelationships relationships, FollowingFeedRepository feed) {
    this.relationships = relationships;
    this.feed = feed;
  }

  @Override
  public FollowingPage get(String accountId, String cursor, int limit) {
    int bounded = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
    var decoded = FollowingCursor.decode(cursor);
    List<FollowingFeedItem> rows =
        feed.find(
            relationships.followedArtistIds(accountId),
            relationships.followedShowIds(accountId),
            decoded.publishedAt(),
            decoded.kind(),
            decoded.contentId(),
            bounded + 1);
    boolean hasNext = rows.size() > bounded;
    List<FollowingFeedItem> page = hasNext ? List.copyOf(rows.subList(0, bounded)) : List.copyOf(rows);
    String nextCursor = hasNext ? FollowingCursor.encode(page.getLast()) : null;
    return new FollowingPage(page.stream().map(FollowingFeedService::view).toList(), nextCursor);
  }

  private static FollowingItem view(FollowingFeedItem item) {
    return new FollowingItem(
        item.kind().wireValue(),
        item.contentId(),
        item.creatorId(),
        item.title(),
        item.subtitle(),
        item.image(),
        item.route(),
        item.publishedAt());
  }
}
