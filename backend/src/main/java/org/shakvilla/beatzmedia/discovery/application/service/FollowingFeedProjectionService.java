package org.shakvilla.beatzmedia.discovery.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.discovery.application.port.out.DiscoveryContentReader;
import org.shakvilla.beatzmedia.discovery.application.port.out.FollowingFeedRepository;
import org.shakvilla.beatzmedia.discovery.domain.CreatorKind;
import org.shakvilla.beatzmedia.discovery.domain.FeedKind;
import org.shakvilla.beatzmedia.discovery.domain.FollowingFeedItem;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

@ApplicationScoped
public class FollowingFeedProjectionService {

  private final DiscoveryContentReader content;
  private final FollowingFeedRepository feed;
  private final Clock clock;

  @Inject
  public FollowingFeedProjectionService(
      DiscoveryContentReader content, FollowingFeedRepository feed, Clock clock) {
    this.content = content;
    this.feed = feed;
    this.clock = clock;
  }

  public void release(String releaseId, Instant publishedAt) {
    var snapshot = content.release(releaseId);
    feed.upsert(
        new FollowingFeedItem(
            FeedKind.release,
            snapshot.contentId(),
            CreatorKind.artist,
            snapshot.creatorId(),
            snapshot.title(),
            snapshot.subtitle(),
            snapshot.image(),
            snapshot.route(),
            publishedAt,
            clock.now()));
  }

  public void episode(String artistId, String episodeId, Instant publishedAt) {
    var snapshot = content.episode(artistId, episodeId);
    feed.upsert(
        new FollowingFeedItem(
            FeedKind.podcast_episode,
            snapshot.contentId(),
            CreatorKind.show,
            snapshot.creatorId(),
            snapshot.title(),
            snapshot.subtitle(),
            snapshot.image(),
            snapshot.route(),
            publishedAt,
            clock.now()));
  }
}
