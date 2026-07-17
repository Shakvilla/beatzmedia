package org.shakvilla.beatzmedia.catalog.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/** In-memory fake for unit tests. Returns configured per-track ownership and price. */
public class FakeOwnershipReader implements OwnershipReader {

  /** Entry holding ownership + optional price. */
  public record Entry(OwnershipStatus status, Long priceMinor) {}

  private final Map<String, Entry> entries = new HashMap<>();
  private int lookups;

  public void set(String trackId, OwnershipStatus status, Long priceMinor) {
    entries.put(trackId, new Entry(status, priceMinor));
  }

  /** Number of ownership/price lookups served, so tests can assert none were wasted. */
  public int lookups() {
    return lookups;
  }

  @Override
  public OwnershipStatus ownership(TrackId trackId, Optional<String> callerId) {
    lookups++;
    Entry e = entries.get(trackId.value());
    return e != null ? e.status() : OwnershipStatus.free;
  }

  @Override
  public Optional<Long> priceMinor(TrackId trackId, Optional<String> callerId) {
    lookups++;
    Entry e = entries.get(trackId.value());
    return (e != null && e.priceMinor() != null) ? Optional.of(e.priceMinor()) : Optional.empty();
  }
}
