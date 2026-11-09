package org.shakvilla.beatzmedia.discovery.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository.Attribution;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository.Suppression;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("integration")
class RecommendationInteractionRepositoryIT {

  private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

  @Inject RecommendationInteractionRepository repository;

  @Test
  @TestTransaction
  void persistsAccountBoundAttributionIdempotentEventsSuppressionsAndCleanup() {
    String suffix = Long.toString(System.nanoTime());
    String accountId = "recommendation-account-" + suffix;
    String recommendationId = "recommendation-" + suffix;
    String suppressionId = "suppression-" + suffix;
    String eventId = "event-" + suffix;
    var attribution = new Attribution(
        recommendationId,
        accountId,
        "album",
        "album-1",
        "artist",
        "artist-1",
        "home_for_you",
        "popular",
        "rules-v1",
        1,
        NOW,
        NOW.plusSeconds(3600));

    repository.insertAttributions(List.of(attribution));
    assertTrue(repository.attribution(recommendationId, accountId, NOW).isPresent());
    assertTrue(repository.attribution(recommendationId, "another-account", NOW).isEmpty());
    assertTrue(repository.insertEvent(eventId, recommendationId, "impression", NOW));
    assertFalse(repository.insertEvent(eventId, recommendationId, "impression", NOW));

    assertEquals(suppressionId, repository.upsertSuppression(new Suppression(
        suppressionId,
        accountId,
        "artist:artist-1",
        "artist",
        "artist",
        "artist-1",
        NOW)));
    assertEquals(
        Set.of("artist:artist-1"),
        repository.suppressedKeys(
            accountId, List.of("artist:artist-1", "artist:artist-2")));
    assertTrue(repository.deleteSuppression(suppressionId, accountId));

    assertEquals(1, repository.deleteExpired(NOW.plusSeconds(7200), 10));
    assertTrue(repository.attribution(
        recommendationId, accountId, NOW.plusSeconds(7200)).isEmpty());
  }
}
