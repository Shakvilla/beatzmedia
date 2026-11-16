package org.shakvilla.beatzmedia.discovery.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.discovery.application.port.out.DiscoveryContentReader;
import org.shakvilla.beatzmedia.discovery.application.port.out.FollowingFeedRepository;
import org.shakvilla.beatzmedia.discovery.application.port.out.FollowingRelationships;
import org.shakvilla.beatzmedia.discovery.domain.CreatorKind;
import org.shakvilla.beatzmedia.discovery.domain.FeedKind;
import org.shakvilla.beatzmedia.discovery.domain.FollowingFeedItem;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

@Tag("unit")
class FollowingFeedServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

  @Test
  void combinesFollowedArtistsAndShowsWithStableCursorPagination() {
    var relationships = new FakeRelationships();
    var repository = new FakeRepository();
    repository.rows.add(item(FeedKind.release, CreatorKind.artist, "release-1", "artist-1", NOW));
    repository.rows.add(
        item(FeedKind.podcast_episode, CreatorKind.show, "episode-1", "show-1", NOW.minusSeconds(1)));
    repository.rows.add(
        item(FeedKind.release, CreatorKind.artist, "release-2", "artist-1", NOW.minusSeconds(2)));
    var service = new FollowingFeedService(relationships, repository);

    var first = service.get("account-1", null, 2);

    assertEquals(2, first.items().size());
    assertEquals(List.of("artist-1"), repository.artistIds);
    assertEquals(List.of("show-1"), repository.showIds);
    assertNotNull(first.nextCursor());

    service.get("account-1", first.nextCursor(), 2);
    assertEquals(NOW.minusSeconds(1), repository.beforePublishedAt);
    assertEquals(FeedKind.podcast_episode, repository.afterKind);
    assertEquals("episode-1", repository.afterContentId);
  }

  @Test
  void publicationProjectionStoresIdempotentGlobalSnapshotsWithoutAnAccountId() {
    var repository = new FakeRepository();
    DiscoveryContentReader content =
        new DiscoveryContentReader() {
          @Override
          public ContentSnapshot release(String releaseId) {
            return new ContentSnapshot(
                releaseId, "artist-1", "New Album", "Ama", "/cover", "/album/" + releaseId);
          }

          @Override
          public ContentSnapshot episode(String artistId, String episodeId) {
            return new ContentSnapshot(
                episodeId, "show-1", "New Episode", "The Show", "/ep", "/podcast/show-1");
          }
        };
    var service = new FollowingFeedProjectionService(content, repository, FakeClock.at(NOW));

    service.release("release-1", NOW.minusSeconds(10));
    service.release("release-1", NOW.minusSeconds(10));
    service.episode("artist-1", "episode-1", NOW);

    assertEquals(2, repository.saved.size());
    assertEquals(CreatorKind.artist, repository.saved.get(0).creatorKind());
    assertEquals(CreatorKind.show, repository.saved.get(1).creatorKind());
  }

  private static FollowingFeedItem item(
      FeedKind kind, CreatorKind creatorKind, String id, String creatorId, Instant publishedAt) {
    return new FollowingFeedItem(
        kind,
        id,
        creatorKind,
        creatorId,
        "Title",
        "Subtitle",
        "/image",
        "/route",
        publishedAt,
        NOW);
  }

  private static final class FakeRelationships implements FollowingRelationships {
    @Override
    public List<String> followedArtistIds(String accountId) {
      return List.of("artist-1");
    }

    @Override
    public List<String> followedShowIds(String accountId) {
      return List.of("show-1");
    }
  }

  private static final class FakeRepository implements FollowingFeedRepository {
    final List<FollowingFeedItem> rows = new ArrayList<>();
    final List<FollowingFeedItem> saved = new ArrayList<>();
    List<String> artistIds;
    List<String> showIds;
    Instant beforePublishedAt;
    FeedKind afterKind;
    String afterContentId;

    @Override
    public void upsert(FollowingFeedItem item) {
      saved.removeIf(existing -> existing.key().equals(item.key()));
      saved.add(item);
    }

    @Override
    public List<FollowingFeedItem> find(
        List<String> artistIds,
        List<String> showIds,
        Instant beforePublishedAt,
        FeedKind afterKind,
        String afterContentId,
        int limit) {
      this.artistIds = artistIds;
      this.showIds = showIds;
      this.beforePublishedAt = beforePublishedAt;
      this.afterKind = afterKind;
      this.afterContentId = afterContentId;
      return rows.stream().limit(limit).toList();
    }
  }
}
