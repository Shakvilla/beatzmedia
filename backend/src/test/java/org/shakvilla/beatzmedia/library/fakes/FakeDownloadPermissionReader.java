package org.shakvilla.beatzmedia.library.fakes;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.out.DownloadPermissionReader;

/**
 * In-memory fake for unit tests. Counts invocations so tests can assert the collection service
 * calls this exactly once per load (batched), never once per owned track (N+1).
 */
public class FakeDownloadPermissionReader implements DownloadPermissionReader {

  private final Set<String> downloadable = new HashSet<>();
  private final AtomicInteger calls = new AtomicInteger();

  public void permit(String trackId) {
    downloadable.add(trackId);
  }

  public int callCount() {
    return calls.get();
  }

  @Override
  public Set<String> downloadableTrackIds(AccountId account, Collection<String> trackIds) {
    calls.incrementAndGet();
    Set<String> candidates = new HashSet<>(trackIds);
    Set<String> result = new HashSet<>(downloadable);
    result.retainAll(candidates);
    return result;
  }
}
