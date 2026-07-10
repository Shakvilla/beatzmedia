package org.shakvilla.beatzmedia.audit.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditFilter;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * In-memory fake for {@link AuditReader}. Supports seeding entries and applies basic filter logic
 * (type, actor, q) so unit tests can assert on the read endpoint. Testing-strategy §2.
 */
public class FakeAuditReader implements AuditReader {

  private final List<AuditEntry> entries = new ArrayList<>();

  /** Seeds entries into the fake store. */
  public void seed(AuditEntry... toAdd) {
    for (AuditEntry e : toAdd) {
      entries.add(e);
    }
  }

  /** Returns all seeded entries (for test assertions). */
  public List<AuditEntry> all() {
    return List.copyOf(entries);
  }

  /** Clears all seeded entries. */
  public void reset() {
    entries.clear();
  }

  @Override
  public Page<AuditEntry> query(AuditFilter filter, PageRequest page) {
    Stream<AuditEntry> stream = entries.stream();

    if (filter.type() != null) {
      stream = stream.filter(e -> e.getType() == filter.type());
    }
    if (filter.actor() != null && !filter.actor().isBlank()) {
      String lower = filter.actor().toLowerCase();
      stream = stream.filter(e ->
          (e.getActor() != null && e.getActor().toLowerCase().contains(lower))
              || (e.getActorName() != null && e.getActorName().toLowerCase().contains(lower)));
    }
    if (filter.q() != null && !filter.q().isBlank()) {
      String lower = filter.q().toLowerCase();
      stream = stream.filter(e ->
          (e.getAction() != null && e.getAction().toLowerCase().contains(lower))
              || (e.getTargetType() != null && e.getTargetType().toLowerCase().contains(lower))
              || (e.getTargetId() != null && e.getTargetId().toLowerCase().contains(lower)));
    }
    if (filter.targetId() != null && !filter.targetId().isBlank()) {
      stream = stream.filter(e -> filter.targetId().equals(e.getTargetId()));
    }

    // Sort newest first
    List<AuditEntry> filtered = stream
        .sorted(Comparator.comparing(AuditEntry::getOccurredAt).reversed())
        .toList();

    long total = filtered.size();
    int from = page.offset();
    int to = Math.min(from + page.size(), (int) total);
    List<AuditEntry> pageItems = from >= total
        ? List.of()
        : filtered.subList(from, to);

    return Page.of(pageItems, page.page(), page.size(), total);
  }
}
