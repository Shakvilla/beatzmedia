package org.shakvilla.beatzmedia.discovery.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.shakvilla.beatzmedia.discovery.domain.CreatorKind;
import org.shakvilla.beatzmedia.discovery.domain.FeedKind;

@Entity
@Table(name = "following_feed_item")
public class FollowingFeedItemEntity {
  @Id String id;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false)
  FeedKind kind;

  @Column(name = "content_id", nullable = false)
  String contentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "creator_kind", nullable = false)
  CreatorKind creatorKind;

  @Column(name = "creator_id", nullable = false)
  String creatorId;

  @Column(nullable = false)
  String title;

  @Column(nullable = false)
  String subtitle;

  String image;

  @Column(nullable = false)
  String route;

  @Column(name = "published_at", nullable = false)
  Instant publishedAt;

  @Column(name = "projected_at", nullable = false)
  Instant projectedAt;
}
