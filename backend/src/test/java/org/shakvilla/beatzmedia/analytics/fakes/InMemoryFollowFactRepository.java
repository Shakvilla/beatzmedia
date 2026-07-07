package org.shakvilla.beatzmedia.analytics.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.analytics.application.port.out.FollowFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.FollowFact;

/** In-memory fake for {@link FollowFactRepository}. */
public class InMemoryFollowFactRepository implements FollowFactRepository {

  private final List<FollowFact> facts = new ArrayList<>();

  @Override
  public void append(FollowFact fact) {
    facts.add(fact);
  }

  @Override
  public List<FollowFact> findUnprocessed() {
    return facts.stream().filter(f -> !f.processed()).toList();
  }

  @Override
  public void markProcessed(List<String> factIds) {
    for (int i = 0; i < facts.size(); i++) {
      FollowFact f = facts.get(i);
      if (factIds.contains(f.id())) {
        facts.set(i, new FollowFact(f.id(), f.artistId(), f.occurredAt(), true));
      }
    }
  }

  public int allCount() {
    return facts.size();
  }
}
