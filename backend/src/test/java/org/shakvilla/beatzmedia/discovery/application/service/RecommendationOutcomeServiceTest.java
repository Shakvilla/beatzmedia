package org.shakvilla.beatzmedia.discovery.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository.Attribution;

@Tag("unit")
class RecommendationOutcomeServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

  @Test
  void attributesReleaseTracksAndPodcastEpisodesButRejectsUnrelatedPlayback() {
    var repository = new FakeRepository();
    var service = new RecommendationOutcomeService(repository);

    repository.attribution = attribution("album", "album-1", "artist", "artist-1");
    service.record(
        "account-1", "recommendation-1", "checkpoint-1", "qualified-play",
        "track", "track-1", "album-1", NOW);
    service.record(
        "account-1", "recommendation-1", "checkpoint-2", "completion",
        "track", "track-2", "another-album", NOW);

    repository.attribution = attribution("podcast", "show-1", "podcast", "show-1");
    service.record(
        "account-1", "recommendation-1", "checkpoint-3", "start",
        "podcast-episode", "episode-1", "show-1", NOW);

    assertEquals(List.of("checkpoint-1", "checkpoint-3"), repository.eventIds);
  }

  private static Attribution attribution(
      String kind, String contentId, String sourceType, String sourceId) {
    return new Attribution(
        "recommendation-1",
        "account-1",
        kind,
        contentId,
        sourceType,
        sourceId,
        "home_for_you",
        "popular",
        "rules-v1",
        1,
        NOW,
        NOW.plusSeconds(3600));
  }

  private static final class FakeRepository implements RecommendationInteractionRepository {
    Attribution attribution;
    final List<String> eventIds = new ArrayList<>();

    @Override
    public void insertAttributions(List<Attribution> values) {}

    @Override
    public Set<String> suppressedKeys(String accountId, List<String> targetKeys) {
      return Set.of();
    }

    @Override
    public Optional<Attribution> attribution(String id, String accountId, Instant now) {
      return Optional.ofNullable(attribution)
          .filter(value -> value.accountId().equals(accountId));
    }

    @Override
    public boolean insertEvent(String eventId, String id, String type, Instant receivedAt) {
      eventIds.add(eventId);
      return true;
    }

    @Override
    public String upsertSuppression(Suppression suppression) {
      return suppression.id();
    }

    @Override
    public boolean deleteSuppression(String suppressionId, String accountId) {
      return false;
    }

    @Override
    public int deleteExpired(Instant now, int limit) {
      return 0;
    }
  }
}
