package org.shakvilla.beatzmedia.studio.fakes;

import java.util.HashSet;
import java.util.Set;

import org.shakvilla.beatzmedia.studio.application.port.out.OwnershipReader;

/** In-memory fake for {@link OwnershipReader} used in unit tests. */
public class FakeOwnershipReader implements OwnershipReader {

  private final Set<String> owned = new HashSet<>();

  public FakeOwnershipReader withOwner(String episodeId) {
    owned.add(episodeId);
    return this;
  }

  @Override
  public boolean hasAnyOwner(String episodeId) {
    return owned.contains(episodeId);
  }
}
