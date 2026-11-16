package org.shakvilla.beatzmedia.discovery.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.discovery.application.port.in.ManageRecommendationInteractions;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository.Attribution;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository.Suppression;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

@ApplicationScoped
public class RecommendationInteractionService implements ManageRecommendationInteractions {

  private static final Set<String> EVENT_TYPES = Set.of("impression", "open");
  private static final Set<String> FEEDBACK_ACTIONS =
      Set.of("not-interested", "dont-recommend-source");
  private static final int MAX_EVENT_BATCH = 50;

  private final RecommendationInteractionRepository repository;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public RecommendationInteractionService(
      RecommendationInteractionRepository repository, IdGenerator ids, Clock clock) {
    this.repository = repository;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void recordEvents(String accountId, List<EventInput> events) {
    if (events == null || events.isEmpty() || events.size() > MAX_EVENT_BATCH) {
      throw new ValidationException("events must contain 1..50 items", "events");
    }
    Instant now = clock.now();
    for (EventInput event : events) {
      requireText(event == null ? null : event.eventId(), "eventId");
      requireText(event.recommendationId(), "recommendationId");
      if (!EVENT_TYPES.contains(event.eventType())) {
        throw new ValidationException("eventType must be impression or open", "eventType");
      }
      Attribution attribution = repository
          .attribution(event.recommendationId(), accountId, now)
          .orElseThrow(() -> new NotFoundException("Recommendation not found"));
      repository.insertEvent(event.eventId(), attribution.id(), event.eventType(), now);
    }
  }

  @Override
  @Transactional
  public FeedbackResult feedback(String accountId, FeedbackInput input) {
    if (input == null) throw new ValidationException("feedback body is required");
    requireText(input.recommendationId(), "recommendationId");
    if (!FEEDBACK_ACTIONS.contains(input.action())) {
      throw new ValidationException(
          "action must be not-interested or dont-recommend-source", "action");
    }
    Instant now = clock.now();
    Attribution attribution = repository
        .attribution(input.recommendationId(), accountId, now)
        .orElseThrow(() -> new NotFoundException("Recommendation not found"));
    Suppression suppression = suppression(accountId, attribution, input.action(), now);
    return new FeedbackResult(repository.upsertSuppression(suppression));
  }

  @Override
  @Transactional
  public void undo(String accountId, String suppressionId) {
    requireText(suppressionId, "suppressionId");
    if (!repository.deleteSuppression(suppressionId, accountId)) {
      throw new NotFoundException("Recommendation suppression not found");
    }
  }

  private Suppression suppression(
      String accountId, Attribution attribution, String action, Instant now) {
    if ("not-interested".equals(action)) {
      return new Suppression(
          ids.newId(),
          accountId,
          RecommendationTargets.content(attribution.contentKind(), attribution.contentId()),
          "content",
          attribution.contentKind(),
          attribution.contentId(),
          now);
    }
    return new Suppression(
        ids.newId(),
        accountId,
        RecommendationTargets.source(attribution.sourceType(), attribution.sourceId()),
        attribution.sourceType(),
        attribution.sourceType(),
        attribution.sourceId(),
        now);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field + " is required", field);
    }
  }
}
