package org.shakvilla.beatzmedia.payments.fakes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.shakvilla.beatzmedia.payments.application.port.out.DiscrepancyRepository;
import org.shakvilla.beatzmedia.payments.domain.ReconciliationDiscrepancy;

/**
 * In-memory fake for {@link DiscrepancyRepository}. Mirrors the {@code (intent_id, kind, as_of_day)}
 * UNIQUE backstop: the first record of a natural key returns {@code true}, repeats return
 * {@code false} — so re-running the daily reconciliation is idempotent in unit tests.
 */
public class FakeDiscrepancyRepository implements DiscrepancyRepository {

  private final Set<String> keys = new HashSet<>();
  private final List<ReconciliationDiscrepancy> recorded = new ArrayList<>();

  @Override
  public boolean record(ReconciliationDiscrepancy d) {
    String key = d.getIntentId() + "|" + d.getKind().name() + "|" + d.getAsOfDay();
    if (!keys.add(key)) {
      return false;
    }
    recorded.add(d);
    return true;
  }

  /** All discrepancies newly recorded (deduped), for assertions. */
  public List<ReconciliationDiscrepancy> recorded() {
    return List.copyOf(recorded);
  }

  public int count() {
    return recorded.size();
  }
}
