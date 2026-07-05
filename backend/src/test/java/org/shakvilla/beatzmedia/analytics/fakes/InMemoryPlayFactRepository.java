package org.shakvilla.beatzmedia.analytics.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.analytics.application.port.out.PlayFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.PlayFact;

/** In-memory fake for {@link PlayFactRepository}. */
public class InMemoryPlayFactRepository implements PlayFactRepository {

  private final List<PlayFact> facts = new ArrayList<>();

  @Override
  public void append(PlayFact fact) {
    facts.add(fact);
  }

  @Override
  public List<PlayFact> findUnprocessed() {
    return facts.stream().filter(f -> !f.processed()).toList();
  }

  @Override
  public void markProcessed(List<String> factIds) {
    for (int i = 0; i < facts.size(); i++) {
      PlayFact f = facts.get(i);
      if (factIds.contains(f.id())) {
        facts.set(i, new PlayFact(f.id(), f.artistId(), f.accountId(), f.occurredAt(), true));
      }
    }
  }

  public int allCount() {
    return facts.size();
  }
}
