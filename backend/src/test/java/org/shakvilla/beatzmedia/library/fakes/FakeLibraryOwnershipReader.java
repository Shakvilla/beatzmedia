package org.shakvilla.beatzmedia.library.fakes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.out.LibraryOwnershipReader;

/** In-memory fake for unit tests. */
public class FakeLibraryOwnershipReader implements LibraryOwnershipReader {

  private final Map<String, Set<String>> owned = new HashMap<>();

  public void grant(AccountId account, String trackId) {
    owned.computeIfAbsent(account.value(), k -> new LinkedHashSet<>()).add(trackId);
  }

  @Override
  public List<String> ownedTrackIds(AccountId account) {
    return new ArrayList<>(owned.getOrDefault(account.value(), Set.of()));
  }
}
