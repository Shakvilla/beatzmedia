package org.shakvilla.beatzmedia.discovery.domain;

import java.time.Instant;

public record FollowingFeedItem(
    FeedKind kind,
    String contentId,
    CreatorKind creatorKind,
    String creatorId,
    String title,
    String subtitle,
    String image,
    String route,
    Instant publishedAt,
    Instant projectedAt) {

  public FollowingFeedItem {
    if (contentId == null || contentId.isBlank()) throw new IllegalArgumentException("contentId");
    if (creatorId == null || creatorId.isBlank()) throw new IllegalArgumentException("creatorId");
    if (title == null || title.isBlank()) throw new IllegalArgumentException("title");
    if (subtitle == null || subtitle.isBlank()) throw new IllegalArgumentException("subtitle");
    if (route == null || route.isBlank()) throw new IllegalArgumentException("route");
  }

  public String key() {
    return kind.name() + ":" + contentId;
  }
}
