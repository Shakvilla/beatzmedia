package org.shakvilla.beatzmedia.discovery.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository.Attribution;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

@ApplicationScoped
public class RecommendationAttributionWriter {

  static final String RANKING_VERSION = "rules-v1";
  static final String SURFACE = "home_for_you";
  private static final Duration RETENTION = Duration.ofDays(90);

  private final RecommendationInteractionRepository repository;
  private final IdGenerator ids;

  @Inject
  public RecommendationAttributionWriter(
      RecommendationInteractionRepository repository, IdGenerator ids) {
    this.repository = repository;
    this.ids = ids;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public Map<String, String> create(
      String accountId,
      List<RecommendationRanker.RankedRecommendation> ranked,
      Instant now) {
    List<Attribution> attributions = new ArrayList<>();
    Map<String, String> idsByCandidate = new LinkedHashMap<>();
    for (int index = 0; index < ranked.size(); index++) {
      var recommendation = ranked.get(index);
      var candidate = recommendation.candidate();
      String id = ids.newId();
      attributions.add(new Attribution(
          id,
          accountId,
          candidate.kind().wireValue(),
          candidate.contentId(),
          RecommendationTargets.sourceType(candidate),
          candidate.creatorId(),
          SURFACE,
          recommendation.reasonCode(),
          RANKING_VERSION,
          index + 1,
          now,
          now.plus(RETENTION)));
      idsByCandidate.put(candidate.key(), id);
    }
    repository.insertAttributions(attributions);
    return Map.copyOf(idsByCandidate);
  }
}
