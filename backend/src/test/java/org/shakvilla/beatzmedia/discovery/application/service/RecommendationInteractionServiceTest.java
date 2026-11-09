package org.shakvilla.beatzmedia.discovery.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.discovery.application.port.in.ManageRecommendationInteractions.EventInput;
import org.shakvilla.beatzmedia.discovery.application.port.in.ManageRecommendationInteractions.FeedbackInput;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository.Attribution;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

@Tag("unit")
class RecommendationInteractionServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

  FakeRepository repository;
  RecommendationInteractionService service;

  @BeforeEach
  void setUp() {
    repository = new FakeRepository();
    repository.attributions.put("recommendation-1", attribution("recommendation-1", "account-1"));
    service = new RecommendationInteractionService(
        repository,
        FakeIds.sequential("suppression"),
        FakeClock.at(NOW.toString()));
  }

  @Test
  void createsIdempotentContentAndSourceSuppressionsFromServerOwnedAttribution() {
    var content = service.feedback(
        "account-1", new FeedbackInput("recommendation-1", "not-interested"));
    var repeated = service.feedback(
        "account-1", new FeedbackInput("recommendation-1", "not-interested"));
    var source = service.feedback(
        "account-1", new FeedbackInput("recommendation-1", "dont-recommend-source"));

    assertEquals(content.suppressionId(), repeated.suppressionId());
    assertEquals(Set.of("content:album:album-1", "artist:artist-1"), repository.byTarget.keySet());
    assertEquals("artist", repository.byTarget.get("artist:artist-1").targetType());
    assertEquals("suppression-3", source.suppressionId());
  }

  @Test
  void eventRetriesDoNotInflateStoredFacts() {
    var event = new EventInput("event-1", "recommendation-1", "impression");

    service.recordEvents("account-1", List.of(event));
    service.recordEvents("account-1", List.of(event));

    assertEquals(Set.of("event-1"), repository.eventIds);
  }

  @Test
  void anotherAccountCannotReadOrMutateTheRecommendation() {
    assertThrows(
        NotFoundException.class,
        () -> service.feedback(
            "account-2", new FeedbackInput("recommendation-1", "not-interested")));
  }

  private static Attribution attribution(String id, String accountId) {
    return new Attribution(
        id,
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
  }

  private static final class FakeRepository implements RecommendationInteractionRepository {
    final Map<String, Attribution> attributions = new LinkedHashMap<>();
    final Map<String, Suppression> byTarget = new LinkedHashMap<>();
    final Set<String> eventIds = new LinkedHashSet<>();

    @Override
    public void insertAttributions(List<Attribution> values) {
      values.forEach(value -> attributions.put(value.id(), value));
    }

    @Override
    public Set<String> suppressedKeys(String accountId, List<String> targetKeys) {
      return Set.copyOf(byTarget.keySet());
    }

    @Override
    public Optional<Attribution> attribution(String id, String accountId, Instant now) {
      return Optional.ofNullable(attributions.get(id))
          .filter(value -> value.accountId().equals(accountId))
          .filter(value -> value.expiresAt().isAfter(now));
    }

    @Override
    public boolean insertEvent(String eventId, String id, String type, Instant receivedAt) {
      return eventIds.add(eventId);
    }

    @Override
    public String upsertSuppression(Suppression suppression) {
      return byTarget.computeIfAbsent(suppression.targetKey(), ignored -> suppression).id();
    }

    @Override
    public boolean deleteSuppression(String suppressionId, String accountId) {
      return byTarget.values().removeIf(
          value -> value.id().equals(suppressionId) && value.accountId().equals(accountId));
    }

    @Override
    public int deleteExpired(Instant now, int limit) {
      return 0;
    }
  }
}
