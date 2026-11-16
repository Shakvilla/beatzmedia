package org.shakvilla.beatzmedia.discovery.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RecommendationInteractionRepository {

  void insertAttributions(List<Attribution> attributions);

  Set<String> suppressedKeys(String accountId, List<String> targetKeys);

  Optional<Attribution> attribution(String recommendationId, String accountId, Instant now);

  boolean insertEvent(String eventId, String recommendationId, String eventType, Instant receivedAt);

  String upsertSuppression(Suppression suppression);

  boolean deleteSuppression(String suppressionId, String accountId);

  int deleteExpired(Instant now, int limit);

  record Attribution(
      String id,
      String accountId,
      String contentKind,
      String contentId,
      String sourceType,
      String sourceId,
      String surface,
      String reasonCode,
      String rankingVersion,
      int position,
      Instant createdAt,
      Instant expiresAt) {}

  record Suppression(
      String id,
      String accountId,
      String targetKey,
      String targetType,
      String targetKind,
      String targetId,
      Instant createdAt) {}
}
