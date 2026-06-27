package org.shakvilla.beatzmedia.audit.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;

/**
 * In-memory fake for {@link AuditWriter}. Records all appended entries so unit tests can assert on
 * them. Testing-strategy §2.
 */
public class FakeAuditWriter implements AuditWriter {

  private final List<AuditEntry> entries = new ArrayList<>();

  @Override
  public void append(AuditEntry entry) {
    entries.add(entry);
  }

  /** Returns all appended entries (for test assertions). */
  public List<AuditEntry> all() {
    return List.copyOf(entries);
  }

  /** Returns the number of entries appended since last reset. */
  public int size() {
    return entries.size();
  }

  /** Clears the recorded entries. */
  public void reset() {
    entries.clear();
  }
}
