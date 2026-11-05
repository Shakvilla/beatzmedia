package org.shakvilla.beatzmedia.discovery.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.discovery.application.port.in.GetPersonalizedRecommendations;
import org.shakvilla.beatzmedia.discovery.application.port.out.PersonalizationSignals;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationContent;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class PersonalizedRecommendationsService implements GetPersonalizedRecommendations {

  private final PersonalizationSignals signals;
  private final RecommendationContent content;
  private final Clock clock;

  @Inject
  public PersonalizedRecommendationsService(
      PersonalizationSignals signals, RecommendationContent content, Clock clock) {
    this.signals = signals;
    this.content = content;
    this.clock = clock;
  }

  @Override
  public RecommendationPage get(String accountId, int limit) {
    int bounded = Math.max(1, Math.min(limit <= 0 ? 12 : limit, 24));
    var snapshot = signals.load(accountId);
    var ranked =
        RecommendationRanker.rank(
            content.candidates(accountId, snapshot), snapshot, bounded, clock.now());
    return new RecommendationPage(ranked);
  }
}
