package org.shakvilla.beatzmedia.analytics.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.analytics.application.port.out.TipFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.TipFact;

/** In-memory fake for {@link TipFactRepository}. */
public class InMemoryTipFactRepository implements TipFactRepository {

  private final List<TipFact> facts = new ArrayList<>();

  @Override
  public void append(TipFact fact) {
    facts.add(fact);
  }

  @Override
  public List<TipFact> findUnprocessed() {
    return facts.stream().filter(f -> !f.processed()).toList();
  }

  @Override
  public void markProcessed(List<String> factIds) {
    for (int i = 0; i < facts.size(); i++) {
      TipFact f = facts.get(i);
      if (factIds.contains(f.id())) {
        facts.set(
            i,
            new TipFact(
                f.id(), f.artistId(), f.creatorShareMinor(), f.currency(), f.occurredAt(), true));
      }
    }
  }

  public int allCount() {
    return facts.size();
  }
}
