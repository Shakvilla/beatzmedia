package org.shakvilla.beatzmedia.discovery.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.discovery.application.port.out.PersonalizationSignals;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationContent;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationCandidate;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationKind;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

@Tag("unit")
class PersonalizedRecommendationsServiceTest {

  @Test
  void clampsThePublicLimitToTwentyFour() {
    List<RecommendationCandidate> candidates = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      candidates.add(
          new RecommendationCandidate(
              RecommendationKind.single,
              "track-" + i,
              "artist-" + i,
              "Artist",
              "Track",
              "Artist",
              null,
              "/track/track-" + i,
              List.of(),
              List.of("track-" + i),
              i,
              null));
    }
    PersonalizationSignals signals = accountId -> PersonalizationSignals.Snapshot.empty();
    RecommendationContent content = (accountId, snapshot) -> candidates;
    RecommendationInteractionRepository interactions = new FakeInteractions();
    var service =
        new PersonalizedRecommendationsService(
            signals,
            content,
            interactions,
            new RecommendationAttributionWriter(
                interactions, FakeIds.sequential("recommendation")),
            FakeClock.at("2026-11-04T12:00:00Z"));

    assertEquals(24, service.get("account-1", 500).items().size());
  }

  @Test
  void filtersSourceSuppressionsBeforeRankingAndCreatesAttributionMetadata() {
    var blocked = new RecommendationCandidate(
        RecommendationKind.single,
        "blocked-track",
        "blocked-artist",
        "Blocked",
        "Blocked Track",
        "Blocked",
        null,
        "/track/blocked-track",
        List.of(),
        List.of("blocked-track"),
        1_000,
        null);
    var eligible = new RecommendationCandidate(
        RecommendationKind.single,
        "eligible-track",
        "eligible-artist",
        "Eligible",
        "Eligible Track",
        "Eligible",
        null,
        "/track/eligible-track",
        List.of(),
        List.of("eligible-track"),
        1,
        null);
    var interactions = new FakeInteractions();
    interactions.suppressed = Set.of("artist:blocked-artist");
    var service = new PersonalizedRecommendationsService(
        accountId -> PersonalizationSignals.Snapshot.empty(),
        (accountId, snapshot) -> List.of(blocked, eligible),
        interactions,
        new RecommendationAttributionWriter(
            interactions, FakeIds.sequential("recommendation")),
        FakeClock.at("2026-11-05T12:00:00Z"));

    var result = service.get("account-1", 12).items();

    assertEquals(List.of("eligible-track"), result.stream().map(item -> item.contentId()).toList());
    assertNotNull(result.getFirst().recommendationId());
    assertEquals("rules-v1", result.getFirst().rankingVersion());
    assertEquals(1, result.getFirst().position());
  }

  private static final class FakeInteractions implements RecommendationInteractionRepository {
    Set<String> suppressed = Set.of();

    @Override
    public void insertAttributions(List<Attribution> attributions) {}

    @Override
    public Set<String> suppressedKeys(String accountId, List<String> targetKeys) {
      return suppressed;
    }

    @Override
    public Optional<Attribution> attribution(
        String recommendationId, String accountId, java.time.Instant now) {
      return Optional.empty();
    }

    @Override
    public boolean insertEvent(
        String eventId, String recommendationId, String eventType, java.time.Instant receivedAt) {
      return true;
    }

    @Override
    public String upsertSuppression(Suppression suppression) {
      return suppression.id();
    }

    @Override
    public boolean deleteSuppression(String suppressionId, String accountId) {
      return true;
    }

    @Override
    public int deleteExpired(java.time.Instant now, int limit) {
      return 0;
    }
  }
}
