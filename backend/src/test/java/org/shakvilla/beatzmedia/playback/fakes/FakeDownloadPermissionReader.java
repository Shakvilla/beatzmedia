package org.shakvilla.beatzmedia.playback.fakes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.out.DownloadPermissionReader;

/**
 * In-memory fake for {@link DownloadPermissionReader}. Stands in for the caller's own ownership
 * GRANT, which is the only authority on whether a download is permitted. Records every query so a
 * test can assert the service actually consulted the grant (and in the right guard order) rather
 * than answering from somewhere else.
 */
public class FakeDownloadPermissionReader implements DownloadPermissionReader {

  private final Set<String> permitted = new HashSet<>();
  private final List<String> queries = new ArrayList<>();

  /** Seed "this account holds a grant for this track AND that grant permits downloading". */
  public FakeDownloadPermissionReader permit(AccountId account, TrackId track) {
    permitted.add(key(account, track));
    return this;
  }

  @Override
  public boolean mayDownload(AccountId account, TrackId track) {
    queries.add(key(account, track));
    return permitted.contains(key(account, track));
  }

  /** Every (account, track) pair this reader was asked about, in call order. */
  public List<String> queries() {
    return queries;
  }

  public String key(AccountId account, TrackId track) {
    return account.value() + "|" + track.value();
  }
}
