package org.shakvilla.beatzmedia.discovery.application.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.discovery.application.port.in.GetPersonalizedRecommendations;
import org.shakvilla.beatzmedia.discovery.application.port.out.PersonalizationSignals;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationContent;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationCandidate;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class PersonalizedRecommendationsService implements GetPersonalizedRecommendations {

  private static final Logger LOG = Logger.getLogger(PersonalizedRecommendationsService.class);

  private final PersonalizationSignals signals;
  private final RecommendationContent content;
  private final RecommendationInteractionRepository interactions;
  private final RecommendationAttributionWriter attributionWriter;
  private final Clock clock;

  @Inject
  public PersonalizedRecommendationsService(
      PersonalizationSignals signals,
      RecommendationContent content,
      RecommendationInteractionRepository interactions,
      RecommendationAttributionWriter attributionWriter,
      Clock clock) {
    this.signals = signals;
    this.content = content;
    this.interactions = interactions;
    this.attributionWriter = attributionWriter;
    this.clock = clock;
  }

  @Override
  public RecommendationPage get(String accountId, int limit) {
    int bounded = Math.max(1, Math.min(limit <= 0 ? 12 : limit, 24));
    var snapshot = signals.load(accountId);
    var now = clock.now();
    List<RecommendationCandidate> candidates = content.candidates(accountId, snapshot);
    Set<String> suppressed = interactions.suppressedKeys(accountId, targetKeys(candidates));
    List<RecommendationCandidate> eligible = candidates.stream()
        .filter(candidate -> !suppressed.contains(RecommendationTargets.content(candidate)))
        .filter(candidate -> !suppressed.contains(RecommendationTargets.source(candidate)))
        .toList();
    var ranked = RecommendationRanker.rankDetailed(eligible, snapshot, bounded, now);
    Map<String, String> attributionIds = Map.of();
    try {
      attributionIds = attributionWriter.create(accountId, ranked, now);
    } catch (RuntimeException failure) {
      LOG.warn("Recommendation attribution could not be persisted", failure);
    }
    List<RecommendationItem> items = new ArrayList<>();
    for (int index = 0; index < ranked.size(); index++) {
      var recommendation = ranked.get(index);
      var candidate = recommendation.candidate();
      items.add(new RecommendationItem(
          candidate.kind().wireValue(),
          candidate.contentId(),
          candidate.creatorId(),
          candidate.title(),
          candidate.subtitle(),
          candidate.image(),
          candidate.route(),
          recommendation.reason(),
          attributionIds.get(candidate.key()),
          RecommendationAttributionWriter.RANKING_VERSION,
          index + 1));
    }
    return new RecommendationPage(List.copyOf(items));
  }

  private static List<String> targetKeys(List<RecommendationCandidate> candidates) {
    Set<String> keys = new LinkedHashSet<>();
    for (RecommendationCandidate candidate : candidates) {
      keys.add(RecommendationTargets.content(candidate));
      keys.add(RecommendationTargets.source(candidate));
    }
    return List.copyOf(keys);
  }
}
