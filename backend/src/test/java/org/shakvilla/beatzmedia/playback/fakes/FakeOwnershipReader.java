package org.shakvilla.beatzmedia.playback.fakes;

import java.util.HashSet;
import java.util.Set;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.out.OwnershipReader;

/** In-memory fake for {@link OwnershipReader} used in unit tests. */
public class FakeOwnershipReader implements OwnershipReader {

  private final Set<String> owned = new HashSet<>();

  public FakeOwnershipReader markOwned(AccountId account, TrackId track) {
    owned.add(key(account, track));
    return this;
  }

  @Override
  public boolean isOwned(AccountId account, TrackId track) {
    return owned.contains(key(account, track));
  }

  private String key(AccountId account, TrackId track) {
    return account.value() + "|" + track.value();
  }
}
