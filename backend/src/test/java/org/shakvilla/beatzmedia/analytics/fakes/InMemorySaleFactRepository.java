package org.shakvilla.beatzmedia.analytics.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.analytics.application.port.out.SaleFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.SaleFact;

/** In-memory fake for {@link SaleFactRepository}. */
public class InMemorySaleFactRepository implements SaleFactRepository {

  private final List<SaleFact> facts = new ArrayList<>();

  @Override
  public void append(SaleFact fact) {
    facts.add(fact);
  }

  @Override
  public List<SaleFact> findUnprocessed() {
    return facts.stream().filter(f -> !f.processed()).toList();
  }

  @Override
  public void markProcessed(List<String> factIds) {
    for (int i = 0; i < facts.size(); i++) {
      SaleFact f = facts.get(i);
      if (factIds.contains(f.id())) {
        facts.set(i, new SaleFact(f.id(), f.artistId(), f.grossMinor(), f.currency(), f.occurredAt(), true));
      }
    }
  }

  public int allCount() {
    return facts.size();
  }
}
