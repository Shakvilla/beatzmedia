package org.shakvilla.beatzmedia.discovery.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.discovery.domain.RecommendationCandidate;

public interface RecommendationContent {

  List<RecommendationCandidate> candidates(
      String accountId, PersonalizationSignals.Snapshot signals);
}
