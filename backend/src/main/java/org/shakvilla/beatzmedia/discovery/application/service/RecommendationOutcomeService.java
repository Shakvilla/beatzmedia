package org.shakvilla.beatzmedia.discovery.application.service;

import java.time.Instant;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.discovery.application.port.in.RecordRecommendationOutcome;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;

@ApplicationScoped
public class RecommendationOutcomeService implements RecordRecommendationOutcome {

  private static final Set<String> OUTCOMES = Set.of("start", "qualified-play", "completion");

  private final RecommendationInteractionRepository repository;

  @Inject
  public RecommendationOutcomeService(RecommendationInteractionRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void record(
      String accountId,
      String recommendationId,
      String eventId,
      String eventType,
      String contentKind,
      String contentId,
      String parentId,
      Instant occurredAt) {
    if (recommendationId == null || recommendationId.isBlank() || !OUTCOMES.contains(eventType)) {
      return;
    }
    repository.attribution(recommendationId, accountId, occurredAt)
        .filter(item -> matches(item.contentKind(), item.contentId(), contentKind, contentId, parentId))
        .ifPresent(item -> repository.insertEvent(eventId, item.id(), eventType, occurredAt));
  }

  private static boolean matches(
      String recommendedKind,
      String recommendedId,
      String playedKind,
      String playedId,
      String parentId) {
    if ("single".equals(recommendedKind)) {
      return "track".equals(playedKind) && recommendedId.equals(playedId);
    }
    if ("album".equals(recommendedKind) || "ep".equals(recommendedKind)) {
      return "track".equals(playedKind) && recommendedId.equals(parentId);
    }
    return "podcast".equals(recommendedKind)
        && "podcast-episode".equals(playedKind)
        && recommendedId.equals(parentId);
  }
}
